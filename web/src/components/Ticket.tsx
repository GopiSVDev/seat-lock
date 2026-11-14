import type { Booking, BookingStatus } from '../api/types';
import { certificationName, formatDay, formatName, formatRupees, formatTime, languageName, plural } from '../lib/format';
import { Poster } from './Poster';

export const STATUS: Record<BookingStatus, { label: string; tone: 'green' | 'red' | 'amber' | 'grey' }> = {
  CONFIRMED: { label: 'Confirmed', tone: 'green' },
  PENDING_PAYMENT: { label: 'Payment pending', tone: 'amber' },
  EXPIRED: { label: 'Expired', tone: 'grey' },
  FAILED: { label: 'Refunded', tone: 'red' },
  CANCELLED: { label: 'Cancelled', tone: 'red' },
};

const STATUS_TEXT: Record<BookingStatus, string> = {
  CONFIRMED: 'Show this ticket at the cinema entrance.',
  PENDING_PAYMENT: 'We’re waiting for the payment to come through.',
  EXPIRED: 'Payment wasn’t completed in time, so the seats were released. You haven’t been charged.',
  FAILED: 'The seats were taken before your payment could be confirmed. The full amount is being refunded.',
  CANCELLED: 'This booking was cancelled. The full amount is being refunded to your original payment method.',
};

export function statusText(status: BookingStatus): string {
  return STATUS_TEXT[status];
}

export const bookingRef = (id: number) => `SL${id.toString().padStart(7, '0')}`;

/** The e-ticket: film, show, seats, booking reference and amount. */
export function Ticket({ booking }: { booking: Booking }) {
  const { show } = booking;
  const status = STATUS[booking.status];
  return (
    <article className="ticket" data-status={booking.status} aria-label={`Ticket for ${show.movie.title}, booking ${bookingRef(booking.id)}`}>
      <div className="ticket-top">
        <Poster id={show.movie.id} title={show.movie.title} posterUrl={show.movie.posterUrl} size="thumb" eager />
        <div className="ticket-film">
          <h2>{show.movie.title}</h2>
          <p>
            {certificationName(show.movie.certification)} · {languageName(show.language)} · {formatName(show.format)}
          </p>
        </div>
        <span className="pill" data-tone={status.tone}>
          {status.label}
        </span>
      </div>
      <dl className="ticket-facts">
        <div className="ticket-wide">
          <dt>Cinema</dt>
          <dd>{show.theatre.name}</dd>
        </div>
        <div>
          <dt>Screen</dt>
          <dd>{show.screenName}</dd>
        </div>
        <div>
          <dt>Date</dt>
          <dd>{formatDay(show.startTime)}</dd>
        </div>
        <div>
          <dt>Time</dt>
          <dd className="num">{formatTime(show.startTime)}</dd>
        </div>
        <div>
          <dt>{plural(booking.seats.length, 'Seat')}</dt>
          <dd className="ticket-seats">{booking.seats.map((s) => s.label).join(', ')}</dd>
        </div>
      </dl>
      <div className="ticket-tear" aria-hidden="true" />
      <div className="ticket-bottom">
        <div>
          <span>Booking ID</span>
          <strong className="num">{bookingRef(booking.id)}</strong>
        </div>
        <div>
          <span>{booking.status === 'CONFIRMED' ? 'Amount paid' : 'Amount'}</span>
          <strong className="num">{formatRupees(booking.totalAmount)}</strong>
        </div>
      </div>
      <p className="visually-hidden">{STATUS_TEXT[booking.status]}</p>
    </article>
  );
}
