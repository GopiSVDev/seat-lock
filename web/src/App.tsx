import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router';
import { Layout } from './components/Layout';
import { CityPage } from './pages/CityPage';
import { HomePage } from './pages/HomePage';
import { MoviePage } from './pages/MoviePage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ShowPage } from './pages/ShowPage';
import { TheatrePage } from './pages/TheatrePage';
import { TheatresPage } from './pages/TheatresPage';
import { Loading } from './components/States';

// Browsing is in the main bundle; paying, bookings and admin load when first opened
const CheckoutPage = lazy(() => import('./pages/CheckoutPage').then((m) => ({ default: m.CheckoutPage })));
const BookingPage = lazy(() => import('./pages/BookingPage').then((m) => ({ default: m.BookingPage })));
const MyBookingsPage = lazy(() => import('./pages/MyBookingsPage').then((m) => ({ default: m.MyBookingsPage })));
const Admin = lazy(() => import('./pages/admin/routes').then((m) => ({ default: m.AdminRoutes })));

export function App() {
  return (
    <Suspense fallback={<div style={{ minHeight: '60vh' }} />}>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="city" element={<CityPage />} />
          <Route path="movies/:id" element={<MoviePage />} />
          <Route path="theatres" element={<TheatresPage />} />
          <Route path="theatres/:id" element={<TheatrePage />} />
          <Route path="shows/:id" element={<ShowPage />} />
          <Route path="checkout/:holdId" element={<CheckoutPage />} />
          <Route path="bookings" element={<MyBookingsPage />} />
          <Route path="bookings/:id" element={<BookingPage />} />
          <Route path="auth/callback" element={<Loading text="Signing you in" />} />
          <Route path="admin/*" element={<Admin />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
