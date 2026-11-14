// Money arrives in paise, times in UTC; everything shown is rupees and India time.
export const INDIA_TIME_ZONE = 'Asia/Kolkata';

const rupees = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

export function formatRupees(paise: number): string {
  return rupees.format(paise / 100);
}

const timeFormat = new Intl.DateTimeFormat('en-IN', {
  timeZone: INDIA_TIME_ZONE,
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
});
const dayFormat = new Intl.DateTimeFormat('en-IN', {
  timeZone: INDIA_TIME_ZONE,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});
const longDayFormat = new Intl.DateTimeFormat('en-IN', {
  timeZone: INDIA_TIME_ZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

/** 7:30 pm */
export function formatTime(instant: string | Date): string {
  return timeFormat.format(new Date(instant)).replace(/\s?([ap])\.?m\.?/i, ' $1m').toLowerCase();
}

/** Thu, 1 Jan */
export function formatDay(instant: string | Date): string {
  return dayFormat.format(new Date(instant));
}

/** Thursday, 1 January 2030 */
export function formatLongDay(instant: string | Date): string {
  return longDayFormat.format(new Date(instant));
}

/** 24 Oct 2026, from an API date (2026-10-24) */
export function formatDate(isoDate: string): string {
  return new Intl.DateTimeFormat('en-IN', { timeZone: INDIA_TIME_ZONE, day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(`${isoDate}T12:00:00+05:30`));
}

/** A calendar date string from the API (2030-01-01), shown as a date strip tile. */
export function dateParts(isoDate: string): { weekday: string; day: string; month: string } {
  const noonInIndia = new Date(`${isoDate}T12:00:00+05:30`);
  const parts = new Intl.DateTimeFormat('en-IN', {
    timeZone: INDIA_TIME_ZONE,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).formatToParts(noonInIndia);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return { weekday: get('weekday'), day: get('day'), month: get('month') };
}

/** mm:ss for countdowns; never negative */
export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.ceil(totalSeconds));
  const minutes = Math.floor(s / 60);
  const seconds = s % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

const LANGUAGE_NAMES: Record<string, string> = {
  ENGLISH: 'English', HINDI: 'Hindi', TAMIL: 'Tamil', TELUGU: 'Telugu', MALAYALAM: 'Malayalam',
  KANNADA: 'Kannada', BENGALI: 'Bengali', MARATHI: 'Marathi', GUJARATI: 'Gujarati', PUNJABI: 'Punjabi',
  ODIA: 'Odia', ASSAMESE: 'Assamese', BHOJPURI: 'Bhojpuri', URDU: 'Urdu', JAPANESE: 'Japanese',
  KOREAN: 'Korean', FRENCH: 'French', SPANISH: 'Spanish',
};
const FORMAT_NAMES: Record<string, string> = {
  TWO_D: '2D', THREE_D: '3D', IMAX_2D: 'IMAX 2D', IMAX_3D: 'IMAX 3D', FOUR_DX: '4DX',
};
const GENRE_NAMES: Record<string, string> = { SCI_FI: 'Sci-fi' };
const CERTIFICATION_NAMES: Record<string, string> = { U: 'U', UA: 'U/A', A: 'A' };
const SEAT_TYPE_NAMES: Record<string, string> = { REGULAR: 'Regular', PREMIUM: 'Premium', RECLINER: 'Recliner' };

const titleCase = (value: string) => value.charAt(0) + value.slice(1).toLowerCase();

export const languageName = (v: string) => LANGUAGE_NAMES[v] ?? titleCase(v);
export const formatName = (v: string) => FORMAT_NAMES[v] ?? v;
export const genreName = (v: string) => GENRE_NAMES[v] ?? titleCase(v);
export const certificationName = (v: string) => CERTIFICATION_NAMES[v] ?? v;
export const seatTypeName = (v: string) => SEAT_TYPE_NAMES[v] ?? titleCase(v);

export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h ? `${h}h ${m.toString().padStart(2, '0')}m` : `${m}m`;
}

export function plural(count: number, one: string, many = `${one}s`): string {
  return `${count} ${count === 1 ? one : many}`;
}
