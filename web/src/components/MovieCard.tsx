import { useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { getMovie } from '../api/endpoints';
import type { MovieCard as MovieCardData } from '../api/types';
import { certificationName, languageName } from '../lib/format';
import { Poster } from './Poster';

export function MovieCard({ movie, eager = false }: { movie: MovieCardData; eager?: boolean }) {
  const queryClient = useQueryClient();
  const prefetch = () => queryClient.prefetchQuery({ queryKey: ['movie', movie.id], queryFn: () => getMovie(movie.id) });
  const languages = movie.languages ?? [];
  const imax = movie.formats?.some((f) => f.startsWith('IMAX'));
  return (
    <Link to={`/movies/${movie.id}`} className="movie-card" onPointerEnter={prefetch} onFocus={prefetch}>
      <div className="movie-card-art">
        <Poster id={movie.id} title={movie.title} posterUrl={movie.posterUrl} eager={eager} />
        {imax && <span className="movie-card-badge">IMAX</span>}
      </div>
      <h3 className="movie-card-title">{movie.title}</h3>
      <p className="movie-card-meta">
        {certificationName(movie.certification)}
        {languages.length > 0 && <> · {languages.map(languageName).join(', ')}</>}
      </p>
    </Link>
  );
}

export function MovieCardSkeleton() {
  return (
    <div className="movie-card" aria-hidden="true">
      <div className="skeleton" style={{ aspectRatio: '2 / 3', borderRadius: 'var(--radius)' }} />
      <div className="skeleton" style={{ height: 16, width: '80%', marginTop: 12 }} />
      <div className="skeleton" style={{ height: 12, width: '55%', marginTop: 8 }} />
    </div>
  );
}
