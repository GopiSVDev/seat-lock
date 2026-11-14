import { INDIA_TIME_ZONE } from './format';

/** Today's date in India as 2030-01-01 (the API's date parameter). */
export function indiaToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: INDIA_TIME_ZONE }).format(now);
}

/** The India date a moment falls on, as 2030-01-01. */
export function indiaDate(instant: string | Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: INDIA_TIME_ZONE }).format(new Date(instant));
}

/** A date + time typed by an admin, read as India time (IST has no daylight saving), to a UTC instant. */
export function indiaLocalToInstant(date: string, time: string): string {
  return new Date(`${date}T${time}:00+05:30`).toISOString();
}

/** Whole seconds from now until the instant (negative once passed). */
export function secondsUntil(instant: string | Date, now: number = Date.now()): number {
  return (new Date(instant).getTime() - now) / 1000;
}
