import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError, errorMessage } from '../api/client';
import { checkout, confirmPayment, getBooking, releaseHold } from '../api/endpoints';
import type { Booking } from '../api/types';
import { RequireLogin } from '../auth/guards';
import { useSession } from '../auth/session';
import { Countdown } from '../components/Countdown';
import { BackIcon, ClockIcon } from '../components/Icons';
import { Poster } from '../components/Poster';
import { EmptyState, Notice, Skeleton, type Tone } from '../components/States';
import { useRetryAfter } from '../hooks/useCountdown';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { certificationName, formatDay, formatName, formatRupees, formatTime, languageName, plural } from '../lib/format';
import { openRazorpay, widgetTimeout, type RazorpaySuccess } from '../lib/razorpay';
import { recallCheckout, rememberCheckout } from '../lib/seats';
import '../styles/checkout.css';

type Message = { tone: Tone; title: string; text?: string };

export function CheckoutPage() {
  return (
    <RequireLogin>
      <Checkout />
    </RequireLogin>
  );
}

function Checkout() {
  const holdId = useParams().holdId!;
  const memory = recallCheckout(holdId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const session = useSession();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [gone, setGone] = useState(false);
  const [expired, setExpired] = useState(false);
  const [widgetOpen, setWidgetOpen] = useState(false);
  const [message, setMessage] = useState<Message | null>(null);
  const [receipt, setReceipt] = useState<RazorpaySuccess | null>(null);
  const [wait, startWait] = useRetryAfter();
  // once money may have moved, leaving the page must not give the seats back
  const keep = useRef(false);
  useDocumentTitle('Review your booking');

  const toTicket = useCallback(
    (b: Booking) => {
      keep.current = true;
      queryClient.setQueryData(['booking', b.id], b);
      queryClient.invalidateQueries({ queryKey: ['my-bookings'] });
      navigate(`/bookings/${b.id}`, { replace: true, state: { fresh: true } });
    },
    [navigate, queryClient],
  );

  // Leaving checkout without paying (back button, another link) releases the seats at once
  useEffect(
    () => () => {
      if (!keep.current) releaseHold(holdId, true).catch(() => undefined);
    },
    [holdId],
  );

  const start = useMutation({
    mutationFn: () => checkout(holdId),
    onSuccess: (b) => {
      if (b.status !== 'PENDING_PAYMENT') return toTicket(b);
      rememberCheckout(holdId, b.show.id, b.id);
      setBooking(b);
      setMessage(null);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.status === 404) {
        // the seats were released or paid for already
        if (memory?.bookingId) {
          try {
            const b = await getBooking(memory.bookingId);
            if (b.status !== 'PENDING_PAYMENT') return toTicket(b);
          } catch {
            // fall through
          }
        }
        keep.current = true;
        return setGone(true);
      }
      if (error instanceof ApiError && error.status === 503) startWait(error.retryAfter ?? 5);
      setMessage({
        tone: 'error',
        title: error instanceof ApiError && error.status >= 500 ? 'Payments are unavailable right now' : 'Couldn’t start the payment',
        text: `${errorMessage(error)}${error instanceof ApiError && error.status >= 500 ? ' Your seats are still reserved; please try again.' : ''}`,
      });
    },
  });

  const confirm = useMutation({
    mutationFn: (result: RazorpaySuccess) =>
      confirmPayment(booking!.id, {
        razorpayOrderId: result.razorpay_order_id,
        razorpayPaymentId: result.razorpay_payment_id,
        razorpaySignature: result.razorpay_signature,
      }),
    onSuccess: toTicket,
    onError: (error) => {
      if (error instanceof ApiError && error.status === 503) startWait(error.retryAfter ?? 5);
      setMessage({
        tone: 'warn',
        title: 'Payment received, confirmation pending',
        text: `${errorMessage(error)} Your seats are safe. Retry below, or check My bookings in a minute.`,
      });
    },
  });

  const startedOnce = useRef(false);
  useEffect(() => {
    if (startedOnce.current) return;
    startedOnce.current = true;
    start.mutate();
  }, [start]);

  const pay = async () => {
    if (!booking?.checkout || !booking.paymentExpiresAt) return;
    const timeout = widgetTimeout(booking.paymentExpiresAt);
    if (timeout < 30) {
      setMessage({ tone: 'error', title: 'Not enough time left to pay', text: 'Please go back and select your seats again.' });
      return;
    }
    setMessage(null);
    setWidgetOpen(true);
    try {
      await openRazorpay({
        key: booking.checkout.keyId,
        order_id: booking.checkout.razorpayOrderId,
        amount: booking.checkout.amount,
        currency: booking.checkout.currency,
        name: 'SeatLock',
        description: `${booking.show.movie.title} · ${booking.seats.map((s) => s.label).join(', ')}`,
        timeout,
        prefill: { name: session.name ?? undefined },
        handler: (result) => {
          keep.current = true;
          setWidgetOpen(false);
          setReceipt(result);
          confirm.mutate(result);
        },
        onFailed: (failure) =>
          setMessage({ tone: 'error', title: 'Payment failed', text: `${failure.error.description ?? 'Your bank declined the payment.'} You haven’t been charged. Try again or use another method.` }),
        modal: {
          confirm_close: true,
          ondismiss: () => {
            setWidgetOpen(false);
            if (!keep.current) setMessage({ tone: 'info', title: 'Payment not completed', text: `Your seats are reserved until ${formatTime(booking.paymentExpiresAt!)}.` });
          },
        },
      });
    } catch (error) {
      setWidgetOpen(false);
      setMessage({ tone: 'error', title: 'Couldn’t open the payment window', text: error instanceof Error ? error.message : 'Please try again.' });
    }
  };

  const showId = booking?.show.id ?? memory?.showId;

  if (gone) {
    return (
      <EmptyState title="Your seats were released" icon={<ClockIcon size={22} />}>
        <p>The time to complete this booking ran out. Please select your seats again.</p>
        <Link className="btn" to={showId ? `/shows/${showId}` : '/'}>
          {showId ? 'Select seats' : 'Browse movies'}
        </Link>
      </EmptyState>
    );
  }

  if (expired) {
    return (
      <EmptyState title="Time’s up" icon={<ClockIcon size={22} />}>
        <p>Payment wasn’t completed in 10 minutes, so the seats were released. You haven’t been charged.</p>
        <Link className="btn" to={showId ? `/shows/${showId}` : '/'}>
          Select seats again
        </Link>
      </EmptyState>
    );
  }

  if (!booking) {
    if (start.isPending || !message) {
      return (
        <div className="container page checkout" aria-busy="true">
          <Skeleton height={28} width={240} />
          <div className="checkout-grid" style={{ marginTop: 24 }}>
            <Skeleton height={320} style={{ borderRadius: 'var(--radius-lg)' }} />
            <Skeleton height={200} style={{ borderRadius: 'var(--radius-lg)' }} />
          </div>
        </div>
      );
    }
    return (
      <div className="container page checkout" style={{ maxWidth: 640 }}>
        <Notice tone={message.tone} title={message.title}>
          {message.text}
        </Notice>
        <div className="checkout-actions">
          <button type="button" className="btn" disabled={wait > 0} onClick={() => start.mutate()}>
            {wait > 0 ? `Try again in ${wait}s` : 'Try again'}
          </button>
          {showId && (
            <Link className="btn btn-outline" to={`/shows/${showId}`}>
              Change seats
            </Link>
          )}
        </div>
      </div>
    );
  }

  const { show } = booking;
  const groups = groupByPrice(booking.seats);
  const confirming = confirm.isPending || (!!receipt && !confirm.isError);

  return (
    <div className="container page checkout">
      <Link to={`/shows/${show.id}`} className="back">
        <BackIcon size={16} /> Change seats
      </Link>
      <div className="checkout-head">
        <h1 className="page-title">Review your booking</h1>
        {booking.paymentExpiresAt && <Countdown endsAt={booking.paymentExpiresAt} onEnd={() => !keep.current && setExpired(true)} />}
      </div>

      <div className="checkout-grid">
        <section className="card summary" aria-labelledby="summary-title">
          <h2 id="summary-title" className="visually-hidden">
            Booking summary
          </h2>
          <div className="summary-film">
            <Poster id={show.movie.id} title={show.movie.title} posterUrl={show.movie.posterUrl} size="thumb" />
            <div>
              <p className="summary-title">{show.movie.title}</p>
              <p className="faint">
                {certificationName(show.movie.certification)} · {languageName(show.language)} · {formatName(show.format)}
              </p>
            </div>
          </div>
          <dl className="summary-facts">
            <div>
              <dt>Date</dt>
              <dd>{formatDay(show.startTime)}</dd>
            </div>
            <div>
              <dt>Time</dt>
              <dd className="num">{formatTime(show.startTime)}</dd>
            </div>
            <div>
              <dt>Cinema</dt>
              <dd>
                {show.theatre.name}, {show.screenName}
              </dd>
            </div>
            <div>
              <dt>Seats</dt>
              <dd>{booking.seats.map((s) => s.label).join(', ')}</dd>
            </div>
          </dl>
          <table className="summary-lines">
            <caption className="visually-hidden">Price breakdown</caption>
            <tbody>
              {groups.map(([price, count]) => (
                <tr key={price}>
                  <th scope="row">
                    {plural(count, 'ticket')} × {formatRupees(price)}
                  </th>
                  <td className="num">{formatRupees(price * count)}</td>
                </tr>
              ))}
              <tr>
                <th scope="row">Convenience fee</th>
                <td className="num">{formatRupees(0)}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">Total</th>
                <td className="num">{formatRupees(booking.totalAmount)}</td>
              </tr>
            </tfoot>
          </table>
        </section>

        <section className="card pay" aria-labelledby="pay-title">
          <h2 id="pay-title" className="pay-label">
            Amount payable
          </h2>
          <p className="pay-amount num">{formatRupees(booking.totalAmount)}</p>
          {message && (
            <div aria-live="polite">
              <Notice tone={message.tone} title={message.title}>
                {message.text}
              </Notice>
            </div>
          )}
          {receipt && confirm.isError ? (
            <button type="button" className="btn btn-block" disabled={wait > 0 || confirm.isPending} onClick={() => confirm.mutate(receipt)}>
              {wait > 0 ? `Retry in ${wait}s` : 'Retry confirmation'}
            </button>
          ) : (
            <button type="button" className="btn btn-block" onClick={pay} disabled={widgetOpen || confirming}>
              {confirming ? 'Confirming payment…' : widgetOpen ? 'Complete payment in the window' : `Pay ${formatRupees(booking.totalAmount)}`}
            </button>
          )}
          <p className="pay-note">Secured by Razorpay. Test mode: no real money is charged.</p>
        </section>
      </div>
    </div>
  );
}

function groupByPrice(seats: Booking['seats']): [number, number][] {
  const counts = new Map<number, number>();
  seats.forEach((s) => counts.set(s.price, (counts.get(s.price) ?? 0) + 1));
  return [...counts.entries()].sort((a, b) => b[0] - a[0]);
}
