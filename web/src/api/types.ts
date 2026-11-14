import type { components } from './schema';

type S = components['schemas'];

// springdoc marks every response field optional; the API always sends them, some as null.
// Strict makes them required and lets the listed ones be null.
type Strict<T, Nullable extends keyof T = never> = {
  [K in keyof T]-?: K extends Nullable ? Exclude<T[K], undefined> | null : Exclude<T[K], undefined>;
};

export type Language = S['ShowResponse']['language'] & string;
export type ShowFormat = S['ShowResponse']['format'] & string;
export type SeatType = S['Row']['seatType'];
export type Genre = S['CreateMovieRequest']['genres'][number];
export type Certification = S['CreateMovieRequest']['certification'];
export type BookingStatus = NonNullable<S['BookingResponse']['status']>;

export type City = Strict<S['CityResponse'], 'popularRank' | 'matchedAlias'>;
export type CityInfo = Strict<S['CityInfo']>;
export type Theatre = Omit<Strict<S['TheatreResponse']>, 'city'> & { city: CityInfo };
export type MovieCard = Strict<S['MovieCardResponse'], 'posterUrl' | 'languages' | 'formats'>;
export type Movie = Strict<S['MovieResponse'], 'posterUrl' | 'description'>;
export type ShowDetails = Strict<S['ShowResponse']> & { prices: Partial<Record<SeatType, number>> };
export type Showtime = Strict<S['Showtime']>;
export type TheatreShows = Omit<Strict<S['TheatreShows']>, 'shows'> & { shows: Showtime[] };
export type ShowtimesResponse = Omit<Strict<S['ShowtimesResponse']>, 'theatres'> & { theatres: TheatreShows[] };
export type MovieShows = Omit<Strict<S['MovieShows'], 'posterUrl'>, 'shows'> & { shows: Showtime[] };
export type TheatreShowtimes = Omit<Strict<S['TheatreShowtimesResponse']>, 'theatre' | 'movies'> & {
  theatre: Theatre;
  movies: MovieShows[];
};

export type HeldSeat = Strict<S['Seat']>;
export type Hold = Omit<Strict<S['HoldResponse']>, 'seats'> & { seats: HeldSeat[] };
export type Checkout = Strict<S['Checkout']>;
export type BookingShow = Omit<Strict<S['Show']>, 'movie' | 'theatre'> & {
  movie: Strict<S['Movie'], 'posterUrl'>;
  theatre: Strict<S['Theatre']>;
};
export type Booking = Omit<
  Strict<S['BookingResponse'], 'cancelledAt' | 'paymentExpiresAt' | 'checkout'>,
  'show' | 'seats' | 'checkout'
> & { show: BookingShow; seats: HeldSeat[]; checkout: Checkout | null };

export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

// The seat map endpoints return pre-serialised JSON, so the spec only says "bytes": typed from docs/api.md.
export type LayoutSeat = { id: number; number: number };
export type LayoutRow = { label: string; seatType: SeatType; price: number; seats: LayoutSeat[] };
export type Layout = { showId: number; totalSeats: number; rows: LayoutRow[] };
export type Availability = { showId: number; availableSeats: number; unavailable: number[] };

// Screen rows share the name "Row" with the create request in the spec, so the response row is typed here.
export type ScreenSeat = { id: number; number: number; seatType: SeatType };
export type Screen = { id: number; theatreId: number; name: string; totalSeats: number; rows: { label: string; seats: ScreenSeat[] }[] };

export type CreateMovie = S['CreateMovieRequest'];
export type CreateTheatre = S['CreateTheatreRequest'];
export type CreateScreen = S['CreateScreenRequest'];
export type CreateShow = Omit<S['CreateShowRequest'], 'prices'> & { prices: Partial<Record<SeatType, number>> };
export type CreateCity = S['CreateCityRequest'];
export type PaymentConfirmation = S['PaymentConfirmationRequest'];

export const SEAT_TYPES: SeatType[] = ['RECLINER', 'PREMIUM', 'REGULAR'];
export const LANGUAGES: Language[] = [
  'ENGLISH', 'HINDI', 'TAMIL', 'TELUGU', 'MALAYALAM', 'KANNADA', 'BENGALI', 'MARATHI', 'GUJARATI',
  'PUNJABI', 'ODIA', 'ASSAMESE', 'BHOJPURI', 'URDU', 'JAPANESE', 'KOREAN', 'FRENCH', 'SPANISH',
];
export const FORMATS: ShowFormat[] = ['TWO_D', 'THREE_D', 'IMAX_2D', 'IMAX_3D', 'FOUR_DX'];
export const GENRES: Genre[] = [
  'ACTION', 'ADVENTURE', 'ANIMATION', 'BIOGRAPHY', 'COMEDY', 'CRIME', 'DOCUMENTARY', 'DRAMA', 'FAMILY',
  'FANTASY', 'HISTORICAL', 'HORROR', 'MUSICAL', 'MYSTERY', 'ROMANCE', 'SCI_FI', 'SPORTS', 'THRILLER', 'WAR',
];
export const CERTIFICATIONS: Certification[] = ['U', 'UA', 'A'];
