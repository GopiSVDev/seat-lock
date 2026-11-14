import { request } from './client';
import type {
  Availability, Booking, City, CreateCity, CreateMovie, CreateScreen, CreateShow, CreateTheatre, Genre, Hold,
  Language, Layout, Movie, MovieCard, Page, PaymentConfirmation, Screen, SeatType, ShowDetails, ShowFormat,
  ShowtimesResponse, Theatre, TheatreShowtimes,
} from './types';

// Catalogue (public reads)
export const searchCities = (params: { query?: string; popular?: boolean; withTheatres?: boolean }, signal?: AbortSignal) =>
  request<City[]>('/cities', { query: params, signal });
export const getCity = (id: number) => request<City>(`/cities/${id}`);

export type MovieFilters = { cityId?: number; language?: Language[]; format?: ShowFormat[]; genre?: Genre[]; page?: number; size?: number };
export const listMovies = (filters: MovieFilters) => request<Page<MovieCard>>('/movies', { query: filters });
export const getMovie = (id: number) => request<Movie>(`/movies/${id}`);

export const listTheatres = (params: { cityId?: number; query?: string; page?: number; size?: number }) =>
  request<Page<Theatre>>('/theatres', { query: params });
export const getTheatre = (id: number) => request<Theatre>(`/theatres/${id}`);

type ShowFilters = { date?: string; language?: Language[]; format?: ShowFormat[] };
export const movieShowtimes = (params: { movieId: number; cityId: number } & ShowFilters) =>
  request<ShowtimesResponse>('/shows', { query: params });
export const theatreShowtimes = (theatreId: number, params: ShowFilters) =>
  request<TheatreShowtimes>(`/theatres/${theatreId}/shows`, { query: params });

export const getShow = (id: number) => request<ShowDetails>(`/shows/${id}`);
export const getScreen = (id: number) => request<Screen>(`/screens/${id}`);
export const getLayout = (showId: number) => request<Layout>(`/shows/${showId}/layout`);
export const getAvailability = (showId: number) => request<Availability>(`/shows/${showId}/availability`);

// Holds
export const holdSeats = (showId: number, showSeatIds: number[]) =>
  request<Hold>('/holds', { method: 'POST', body: { showId, showSeatIds }, auth: true });
export const holdBestAvailable = (showId: number, quantity: number, seatType: SeatType) =>
  request<Hold>('/holds/best-available', { method: 'POST', body: { showId, quantity, seatType }, auth: true });
export const getHold = (id: string) => request<Hold>(`/holds/${id}`, { auth: true });
export const releaseHold = (id: string, keepalive = false) =>
  request<void>(`/holds/${id}`, { method: 'DELETE', auth: true, keepalive });

// Bookings
export const checkout = (holdId: string) => request<Booking>('/bookings', { method: 'POST', body: { holdId }, auth: true });
export const confirmPayment = (bookingId: number, body: PaymentConfirmation) =>
  request<Booking>(`/bookings/${bookingId}/payment`, { method: 'POST', body, auth: true });
export const getBooking = (id: number) => request<Booking>(`/bookings/${id}`, { auth: true });
export const myBookings = (period: 'UPCOMING' | 'PAST', page: number) =>
  request<Page<Booking>>('/bookings', { query: { period, page, size: 10 }, auth: true });
export const cancelBooking = (id: number) => request<Booking>(`/bookings/${id}/cancel`, { method: 'POST', auth: true });

// Admin
export const createCity = (body: CreateCity) => request<City>('/cities', { method: 'POST', body, auth: true });
export const addCityAlias = (cityId: number, alias: string) =>
  request<{ id: number; cityId: number; alias: string }>(`/cities/${cityId}/aliases`, { method: 'POST', body: { alias }, auth: true });
export const createMovie = (body: CreateMovie) => request<Movie>('/movies', { method: 'POST', body, auth: true });
export const createTheatre = (body: CreateTheatre) => request<Theatre>('/theatres', { method: 'POST', body, auth: true });
export const createScreen = (theatreId: number, body: CreateScreen) =>
  request<Screen>(`/theatres/${theatreId}/screens`, { method: 'POST', body, auth: true });
export const createShow = (body: CreateShow) => request<ShowDetails>('/shows', { method: 'POST', body, auth: true });
