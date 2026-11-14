import { readJson, session, writeJson } from './storage';

export const MAX_SEATS = 10;
export const PAYMENT_SECONDS = 10 * 60;

type Rows = { seats: { id: number }[] }[];

/**
 * The seats a tap on `seatId` selects when `count` are wanted: the tapped seat plus free neighbours
 * in its row, to the right first. Fewer than `count` when the gap between blocked seats is smaller.
 */
export function blockFrom(rows: Rows, blocked: (id: number) => boolean, seatId: number, count: number): number[] {
  for (const row of rows) {
    const index = row.seats.findIndex((s) => s.id === seatId);
    if (index === -1) continue;
    let left = index;
    let right = index;
    while (left > 0 && !blocked(row.seats[left - 1].id)) left--;
    while (right < row.seats.length - 1 && !blocked(row.seats[right + 1].id)) right++;
    const start = right - left + 1 <= count ? left : Math.max(left, Math.min(index, right - count + 1));
    const end = Math.min(right, start + count - 1);
    return row.seats.slice(start, end + 1).map((s) => s.id);
  }
  return [seatId];
}

/** What a tap does: unselects a selected seat, tops up a partial selection, or starts a new block. */
export function selectSeats(rows: Rows, unavailable: ReadonlySet<number>, picked: number[], seatId: number, quantity: number): number[] {
  if (picked.includes(seatId)) return picked.filter((id) => id !== seatId);
  if (unavailable.has(seatId)) return picked;
  if (picked.length >= quantity) return blockFrom(rows, (id) => unavailable.has(id), seatId, quantity);
  return [...picked, ...blockFrom(rows, (id) => unavailable.has(id) || picked.includes(id), seatId, quantity - picked.length)];
}

type CheckoutMemory = { showId: number; bookingId?: number };
const checkoutKey = (holdId: string) => `seatlock.checkout.${holdId}`;

/** What a checkout page needs after a refresh: which show the seats are for, and its booking once made. */
export function rememberCheckout(holdId: string, showId: number, bookingId?: number): void {
  const previous = readJson<CheckoutMemory>(session, checkoutKey(holdId));
  writeJson(session, checkoutKey(holdId), { showId, bookingId: bookingId ?? previous?.bookingId });
}

export function recallCheckout(holdId: string): CheckoutMemory | null {
  return readJson<CheckoutMemory>(session, checkoutKey(holdId));
}
