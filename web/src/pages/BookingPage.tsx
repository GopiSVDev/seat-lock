import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { ApiError, errorMessage } from '../api/client';
import { cancelBooking, getBooking } from '../api/endpoints';
import { RequireLogin } from '../auth/guards';
import { Dialog } from '../components/Dialog';
import { BackIcon, CheckIcon } from '../components/Icons';
import { ErrorState, Notice, Skeleton } from '../components/States';
import { Ticket, statusText } from '../components/Ticket';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { formatDay, formatRupees, formatTime } from '../lib/format';
import '../styles/bookings.css';

export function BookingPage() {
  return (
    <RequireLogin>
      <BookingView />
    </RequireLogin>
  );
}

function BookingView() {
  const id = Number(useParams().id);
  const fresh = (useLocation().state as { fresh?: boolean } | null)?.fresh ?? false;
  const queryClient = useQueryClient();
  const [asking, setAsking] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const booking = useQuery({
    queryKey: ['booking', id],
    queryFn: () => getBooking(id),
    // Razorpay's webhook can confirm an unpaid booking at any moment
    refetchInterval: (q) => (q.state.data?.status === 'PENDING_PAYMENT' ? 3000 : false),
  });
  useDocumentTitle(booking.data ? `Booking: ${booking.data.show.movie.title}` : 'Booking');

  const cancel = useMutation({
    mutationFn: () => cancelBooking(id),
    onSuccess: (b) => {
      queryClient.setQueryData(['booking', id], b);
      queryClient.invalidateQueries({ queryKey: ['my-bookings'] });
      setAsking(false);
    },
    onError: (error) => {
      setAsking(false);
      setCancelError(error instanceof ApiError && error.status === 409 ? `${error.detail}.` : errorMessage(error));
    },
  });

  if (booking.isPending) {
    return (
      <div className="container page booking" aria-busy="true">
        <Skeleton height={380} style={{ borderRadius: 'var(--radius-lg)' }} />
      </div>
    );
  }
  if (booking.isError) return <ErrorState error={booking.error} onRetry={() => booking.refetch()} title="Booking not found" />;
  const b = booking.data;
  const upcoming = Date.parse(b.show.startTime) > Date.now();
  const justBooked = fresh && b.status === 'CONFIRMED';

  return (
    <div className="container page booking">
      <Link to="/bookings" className="back">
        <BackIcon size={16} /> My bookings
      </Link>

      {justBooked ? (
        <div className="booked" role="status">
          <span className="booked-icon">
            <CheckIcon size={22} />
          </span>
          <div>
            <h1>Booking confirmed</h1>
            <p className="muted">Your tickets are below. Show them at the cinema entrance.</p>
          </div>
        </div>
      ) : (
        <h1 className="booking-title">{b.status === 'CONFIRMED' ? 'Your tickets' : 'Booking details'}</h1>
      )}

      <Ticket booking={b} />

      <div className="booking-after">
        {b.status !== 'CONFIRMED' && <Notice tone={b.status === 'PENDING_PAYMENT' ? 'info' : 'warn'}>{statusText(b.status)}</Notice>}
        {b.status === 'CANCELLED' && b.cancelledAt && (
          <p className="faint">
            Cancelled on {formatDay(b.cancelledAt)} at {formatTime(b.cancelledAt)}. Refunds usually take 5 to 7 working days.
          </p>
        )}
        {cancelError && (
          <Notice tone="error" title="Couldn’t cancel this booking">
            {cancelError}
          </Notice>
        )}
        {b.status === 'CONFIRMED' && upcoming && (
          <button
            type="button"
            className="btn btn-outline"
            onClick={() => {
              setCancelError(null);
              setAsking(true);
            }}
          >
            Cancel booking
          </button>
        )}
      </div>

      {asking && (
        <Dialog
          title="Cancel this booking?"
          onClose={() => setAsking(false)}
          actions={
            <>
              <button type="button" className="btn btn-outline" onClick={() => setAsking(false)}>
                Keep booking
              </button>
              <button type="button" className="btn btn-danger" onClick={() => cancel.mutate()} disabled={cancel.isPending}>
                {cancel.isPending ? 'Cancelling…' : 'Cancel booking'}
              </button>
            </>
          }
        >
          <p>
            Seats {b.seats.map((s) => s.label).join(', ')} will be released. A full refund of {formatRupees(b.totalAmount)} goes back to your original payment method, usually within 5 to
            7 working days.
          </p>
        </Dialog>
      )}
    </div>
  );
}
