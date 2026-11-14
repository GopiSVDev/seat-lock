import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { myBookings } from '../api/endpoints';
import type { Booking } from '../api/types';
import { RequireLogin } from '../auth/guards';
import { ChevronRight, TicketIcon } from '../components/Icons';
import { Pager } from '../components/Pager';
import { Poster } from '../components/Poster';
import { EmptyState, ErrorState, Skeleton } from '../components/States';
import { STATUS } from '../components/Ticket';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { formatDay, formatRupees, formatTime } from '../lib/format';
import '../styles/bookings.css';

export function MyBookingsPage() {
  return (
    <RequireLogin>
      <MyBookings />
    </RequireLogin>
  );
}

function MyBookings() {
  useDocumentTitle('My bookings');
  const [params, setParams] = useSearchParams();
  const period = params.get('period') === 'PAST' ? 'PAST' : 'UPCOMING';
  const page = Number(params.get('page') ?? 0);
  const list = useQuery({ queryKey: ['my-bookings', period, page], queryFn: () => myBookings(period, page), placeholderData: keepPreviousData });

  return (
    <div className="container page bookings">
      <h1 className="page-title" style={{ marginBottom: 16 }}>
        My bookings
      </h1>
      <div className="tabs" role="tablist" aria-label="Bookings">
        {(['UPCOMING', 'PAST'] as const).map((p) => (
          <button key={p} type="button" role="tab" id={`tab-${p}`} aria-selected={period === p} aria-controls="booking-list" onClick={() => setParams(p === 'PAST' ? { period: p } : {})}>
            {p === 'UPCOMING' ? 'Upcoming' : 'Past'}
          </button>
        ))}
      </div>
      <div id="booking-list" role="tabpanel" aria-labelledby={`tab-${period}`}>
        {list.isPending ? (
          <div style={{ display: 'grid', gap: 10 }}>
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} height={110} style={{ borderRadius: 'var(--radius-lg)' }} />
            ))}
          </div>
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => list.refetch()} />
        ) : list.data.items.length === 0 ? (
          <EmptyState title={period === 'UPCOMING' ? 'No upcoming bookings' : 'No past bookings'} icon={<TicketIcon />}>
            {period === 'UPCOMING' && (
              <Link className="btn" to="/">
                Browse movies
              </Link>
            )}
          </EmptyState>
        ) : (
          <>
            <ul className="booking-list">
              {list.data.items.map((b) => (
                <li key={b.id}>
                  <BookingRow booking={b} />
                </li>
              ))}
            </ul>
            <Pager page={page} totalPages={list.data.totalPages} onPage={(p) => setParams({ ...(period === 'PAST' ? { period } : {}), page: String(p) })} />
          </>
        )}
      </div>
    </div>
  );
}

function BookingRow({ booking: b }: { booking: Booking }) {
  const status = STATUS[b.status];
  return (
    <Link to={`/bookings/${b.id}`} className="booking-row">
      <Poster id={b.show.movie.id} title={b.show.movie.title} posterUrl={b.show.movie.posterUrl} size="thumb" />
      <span className="booking-row-body">
        <strong>{b.show.movie.title}</strong>
        <span className="num">
          {formatDay(b.show.startTime)} · {formatTime(b.show.startTime)}
        </span>
        <span>
          {b.show.theatre.name} · {b.seats.map((s) => s.label).join(', ')}
        </span>
      </span>
      <span className="booking-row-side">
        <span className="pill" data-tone={status.tone}>
          {status.label}
        </span>
        <strong className="num">{formatRupees(b.totalAmount)}</strong>
      </span>
      <ChevronRight />
    </Link>
  );
}
