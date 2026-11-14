import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError, errorMessage } from '../api/client';
import { getAvailability, getLayout, getScreen, getShow, getTheatre, holdBestAvailable, holdSeats, theatreShowtimes } from '../api/endpoints';
import type { Hold, Layout, SeatType } from '../api/types';
import { useSession } from '../auth/session';
import { BackIcon } from '../components/Icons';
import { SeatMap } from '../components/SeatMap';
import { ErrorState, Notice, Skeleton, type Tone } from '../components/States';
import { useRetryAfter } from '../hooks/useCountdown';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { formatDay, formatName, formatRupees, formatTime, languageName, plural, seatTypeName } from '../lib/format';
import { MAX_SEATS, rememberCheckout, selectSeats } from '../lib/seats';
import { readJson, session, writeJson } from '../lib/storage';
import { indiaDate } from '../lib/time';
import '../styles/show.css';

type Message = { tone: Tone; title: string; text?: string };
type Saved = { picked: number[]; quantity: number; resume: boolean };

const savedKey = (showId: number) => `seatlock.pick.${showId}`;
const loadCheckout = () => import('./CheckoutPage');

export function ShowPage() {
  const showId = Number(useParams().id);
  const auth = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const show = useQuery({ queryKey: ['show', showId], queryFn: () => getShow(showId) });
  const layout = useQuery({ queryKey: ['layout', showId], queryFn: () => getLayout(showId), staleTime: Infinity });
  const availability = useQuery({
    queryKey: ['availability', showId],
    queryFn: () => getAvailability(showId),
    // the server caches it for a second; polling keeps the map honest while people pick
    refetchInterval: 4000,
  });
  const screen = useQuery({ queryKey: ['screen', show.data?.screenId], queryFn: () => getScreen(show.data!.screenId), enabled: !!show.data, staleTime: Infinity });
  const theatreId = screen.data?.theatreId;
  const theatre = useQuery({ queryKey: ['theatre', theatreId], queryFn: () => getTheatre(theatreId!), enabled: !!theatreId, staleTime: Infinity });
  const day = show.data ? indiaDate(show.data.startTime) : undefined;
  const sameDay = useQuery({
    queryKey: ['theatre-shows', theatreId, day],
    queryFn: () => theatreShowtimes(theatreId!, { date: day }),
    enabled: !!theatreId && !!day,
  });

  const saved = useRef(readJson<Saved>(session, savedKey(showId)));
  const [quantity, setQuantity] = useState(saved.current?.quantity ?? 2);
  const [picked, setPicked] = useState<number[]>(saved.current?.picked ?? []);
  const [lost, setLost] = useState<number[]>([]);
  const [message, setMessage] = useState<Message | null>(null);
  const [wait, startWait] = useRetryAfter();

  useDocumentTitle(show.data ? `${show.data.movieTitle}: select seats` : null);

  const unavailable = useMemo(() => new Set(availability.data?.unavailable ?? []), [availability.data]);
  const pickedSet = useMemo(() => new Set(picked), [picked]);
  const lostSet = useMemo(() => new Set(lost), [lost]);
  const prices = useMemo(() => seatPrices(layout.data), [layout.data]);
  const total = picked.reduce((sum, id) => sum + (prices.get(id) ?? 0), 0);

  // a seat someone else books while it is selected here drops out of the selection
  useEffect(() => {
    setPicked((current) => {
      const free = current.filter((id) => !unavailable.has(id));
      return free.length === current.length ? current : free;
    });
  }, [unavailable]);

  useEffect(() => {
    if (picked.length > 0) void loadCheckout();
  }, [picked.length]);

  const toCheckout = (hold: Hold) => {
    writeJson(session, savedKey(showId), null);
    rememberCheckout(hold.id, showId);
    navigate(`/checkout/${hold.id}`);
  };

  const onError = async (error: unknown, attempted: number[], best?: SeatType) => {
    if (!(error instanceof ApiError)) return setMessage({ tone: 'error', title: errorMessage(error) });
    if (error.status === 429) {
      startWait(error.retryAfter ?? 10);
      return setMessage({ tone: 'warn', title: 'Too many attempts', text: `Please wait ${error.retryAfter ?? 10} seconds and try again. Your selection is kept.` });
    }
    if (error.status === 409) {
      const fresh = await queryClient.fetchQuery({ queryKey: ['availability', showId], queryFn: () => getAvailability(showId), staleTime: 0 });
      if (best) {
        return setMessage({ tone: 'error', title: `Not enough ${seatTypeName(best)} seats together`, text: `There's no block of ${quantity} left in this section. Try fewer seats or another section.` });
      }
      const gone = attempted.filter((id) => fresh.unavailable.includes(id));
      setLost(gone);
      setPicked((current) => current.filter((id) => !gone.includes(id)));
      const labels = gone.map((id) => labelOf(layout.data, id)).filter(Boolean);
      return setMessage({
        tone: 'error',
        title: labels.length ? `${labels.join(', ')} ${labels.length === 1 ? 'was' : 'were'} just booked by someone else` : 'Some of these seats are no longer available',
        text: 'Please choose other seats.',
      });
    }
    setMessage({ tone: 'error', title: error.detail });
  };

  const reserve = useMutation({
    mutationFn: (ids: number[]) => holdSeats(showId, ids),
    onSuccess: toCheckout,
    onError: (error, ids) => onError(error, ids),
  });
  const reserveBest = useMutation({
    mutationFn: (type: SeatType) => holdBestAvailable(showId, quantity, type),
    onSuccess: toCheckout,
    onError: (error, type) => onError(error, [], type),
  });
  const busy = reserve.isPending || reserveBest.isPending;
  const { mutate: reserveSeats } = reserve;

  const proceed = (ids = picked) => {
    setMessage(null);
    setLost([]);
    if (auth.loggedIn) return reserveSeats(ids);
    writeJson(session, savedKey(showId), { picked: ids, quantity, resume: true } satisfies Saved);
    auth.login();
  };

  // back from the login page with seats selected: carry on where the visitor left off
  useEffect(() => {
    const s = saved.current;
    if (!auth.loggedIn || !s?.resume || !layout.data) return;
    saved.current = null;
    writeJson(session, savedKey(showId), null);
    if (s.picked.length > 0) reserveSeats(s.picked);
  }, [auth.loggedIn, layout.data, showId, reserveSeats]);

  if (show.isPending || layout.isPending) return <ShowSkeleton />;
  if (show.isError) return <ErrorState error={show.error} onRetry={() => show.refetch()} title="This show isn't available" />;
  if (layout.isError) return <ErrorState error={layout.error} onRetry={() => layout.refetch()} />;

  const s = show.data;
  const rows = layout.data.rows;
  const started = new Date(s.startTime).getTime() <= Date.now();
  const otherTimes = sameDay.data?.movies.find((m) => m.id === s.movieId)?.shows ?? [];
  const short = quantity - picked.length;

  const changeQuantity = (q: number) => {
    setQuantity(q);
    setMessage(null);
    setPicked((current) => (current.length > q ? [] : current));
  };

  return (
    <div className="show-page">
      <header className="show-head">
        <div className="container">
          <Link to={`/movies/${s.movieId}`} className="back">
            <BackIcon size={16} /> Back
          </Link>
          <h1>{s.movieTitle}</h1>
          <p className="show-sub">
            {theatre.data ? <Link to={`/theatres/${theatre.data.id}`}>{theatre.data.name}</Link> : <span className="skeleton" style={{ display: 'inline-block', width: 120, height: 14 }} />}
            <span>{s.screenName}</span>
          </p>
          <p className="show-sub">
            <span className="num">
              {formatDay(s.startTime)}, {formatTime(s.startTime)}
            </span>
            <span>
              {languageName(s.language)} · {formatName(s.format)}
            </span>
          </p>
          {otherTimes.length > 1 && (
            <nav className="show-times" aria-label="Other showtimes on this day">
              {otherTimes.map((t) => (
                <Link key={t.id} to={`/shows/${t.id}`} className="show-time" aria-current={t.id === showId ? 'page' : undefined} replace>
                  <strong className="num">{formatTime(t.startTime)}</strong>
                  <span>{formatName(t.format)}</span>
                </Link>
              ))}
            </nav>
          )}
        </div>
      </header>

      <div className="container show-body">
        {started && (
          <Notice tone="warn" title="This show has started">
            Booking is closed for this show.
          </Notice>
        )}

        <div className="quantity">
          <span className="quantity-label" id="quantity-label">
            How many seats?
          </span>
          <div className="quantity-options" role="radiogroup" aria-labelledby="quantity-label">
            {Array.from({ length: MAX_SEATS }, (_, i) => i + 1).map((q) => (
              <button key={q} type="button" role="radio" aria-checked={q === quantity} className="quantity-option num" onClick={() => changeQuantity(q)}>
                {q}
              </button>
            ))}
          </div>
        </div>

        <div className="map-card">
          <SeatMap
            rows={rows}
            unavailable={unavailable}
            picked={pickedSet}
            lost={lostSet}
            onSeat={
              started
                ? undefined
                : (id) => {
                    setMessage(null);
                    setLost([]);
                    setPicked((p) => selectSeats(rows, unavailable, p, id, quantity));
                  }
            }
            onBest={started ? undefined : (type) => (auth.loggedIn ? reserveBest.mutate(type) : auth.login())}
            bestBusy={busy || wait > 0}
            status={
              availability.data && (
                <span className="faint num">{availability.isError ? 'Live updates paused' : `${availability.data.availableSeats} of ${layout.data.totalSeats} available`}</span>
              )
            }
          />
        </div>
      </div>

      {(picked.length > 0 || message) && (
        <div className="checkout-bar">
          <div className="container">
            {message && (
              <div aria-live="polite" className="checkout-bar-message">
                <Notice tone={message.tone} title={message.title}>
                  {message.text}
                </Notice>
              </div>
            )}
            {picked.length > 0 && (
              <div className="checkout-bar-row">
                <div className="checkout-bar-summary">
                  <strong className="num">{formatRupees(total)}</strong>
                  <span>
                    {plural(picked.length, 'ticket')} · {picked.map((id) => labelOf(layout.data, id)).join(', ')}
                    {short > 0 && <span className="faint"> · select {short} more</span>}
                  </span>
                </div>
                <button type="button" className="btn" disabled={busy || wait > 0 || started} onClick={() => proceed()}>
                  {wait > 0 ? `Try again in ${wait}s` : busy ? 'Please wait…' : 'Continue'}
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ShowSkeleton() {
  return (
    <div aria-busy="true">
      <div className="show-head">
        <div className="container" style={{ display: 'grid', gap: 10 }}>
          <Skeleton height={14} width={80} />
          <Skeleton height={26} width="45%" />
          <Skeleton height={14} width="60%" />
        </div>
      </div>
      <div className="container show-body">
        <Skeleton height={420} style={{ borderRadius: 'var(--radius-lg)' }} />
      </div>
    </div>
  );
}

function seatPrices(layout: Layout | undefined): Map<number, number> {
  const map = new Map<number, number>();
  layout?.rows.forEach((row) => row.seats.forEach((seat) => map.set(seat.id, row.price)));
  return map;
}

function labelOf(layout: Layout | undefined, id: number): string {
  for (const row of layout?.rows ?? []) {
    const seat = row.seats.find((x) => x.id === id);
    if (seat) return `${row.label}${seat.number}`;
  }
  return '';
}
