import { Navigate, Route, Routes } from 'react-router';
import { AdminCities } from './AdminCities';
import { AdminMovies } from './AdminMovies';
import { AdminScreens } from './AdminScreens';
import { AdminShows } from './AdminShows';
import { AdminTheatres } from './AdminTheatres';
import { AdminLayout } from './shared';

export function AdminRoutes() {
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<Navigate to="movies" replace />} />
        <Route path="movies" element={<AdminMovies />} />
        <Route path="theatres" element={<AdminTheatres />} />
        <Route path="screens" element={<AdminScreens />} />
        <Route path="shows" element={<AdminShows />} />
        <Route path="cities" element={<AdminCities />} />
      </Route>
    </Routes>
  );
}
