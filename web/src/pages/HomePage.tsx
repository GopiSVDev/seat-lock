import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Navigate, useSearchParams } from 'react-router';
import { listMovies } from '../api/endpoints';
import type { Genre, Language, ShowFormat } from '../api/types';
import { FilterMenu } from '../components/FilterMenu';
import { FilmIcon } from '../components/Icons';
import { MovieCard, MovieCardSkeleton } from '../components/MovieCard';
import { Pager } from '../components/Pager';
import { EmptyState, ErrorState } from '../components/States';
import { useCity } from '../hooks/useCity';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { formatName, genreName, languageName, plural } from '../lib/format';
import '../styles/browse.css';

const union = <T,>(...lists: (T[] | null | undefined)[]) => [...new Set(lists.flatMap((l) => l ?? []))];

export function HomePage() {
  const [city] = useCity();
  const [params, setParams] = useSearchParams();
  useDocumentTitle(city ? `Movies in ${city.name}` : null);

  const language = params.getAll('language') as Language[];
  const format = params.getAll('format') as ShowFormat[];
  const genre = params.getAll('genre') as Genre[];
  const page = Number(params.get('page') ?? 0);

  const movies = useQuery({
    queryKey: ['movies', city?.id, language, format, genre, page],
    queryFn: () => listMovies({ cityId: city!.id, language, format, genre, page, size: 20 }),
    enabled: !!city,
    placeholderData: keepPreviousData,
  });
  // filter options come from the unfiltered list, so picking one doesn't hide the others
  const all = useQuery({
    queryKey: ['movies', city?.id, 'options'],
    queryFn: () => listMovies({ cityId: city!.id, size: 50 }),
    enabled: !!city,
    staleTime: 60_000,
  });

  if (!city) return <Navigate to="/city" replace />;

  const update = (key: string, values: string[]) => {
    const next = new URLSearchParams(params);
    next.delete(key);
    values.forEach((v) => next.append(key, v));
    next.delete('page');
    setParams(next, { replace: true });
  };
  const options = all.data?.items ?? [];
  const items = movies.data?.items ?? [];
  const filtered = language.length + format.length + genre.length > 0;

  return (
    <div className="container page">
      <div className="home-head">
        <h1>Movies in {city.name}</h1>
        {movies.data && <span className="muted">{plural(movies.data.totalItems, 'movie')}</span>}
      </div>

      <div className="toolbar" aria-label="Filters" role="group">
        <FilterMenu label="Language" options={union(language, ...options.map((m) => m.languages))} selected={language} onChange={(v) => update('language', v)} name={languageName} />
        <FilterMenu label="Format" options={union(format, ...options.map((m) => m.formats))} selected={format} onChange={(v) => update('format', v)} name={formatName} />
        <FilterMenu label="Genre" options={union(genre, ...options.map((m) => m.genres))} selected={genre} onChange={(v) => update('genre', v)} name={genreName} />
        {filtered && (
          <button type="button" className="link" onClick={() => setParams({}, { replace: true })}>
            Clear all
          </button>
        )}
      </div>

      {movies.isPending ? (
        <div className="movie-grid" aria-busy="true">
          {Array.from({ length: 10 }, (_, i) => (
            <MovieCardSkeleton key={i} />
          ))}
        </div>
      ) : movies.isError ? (
        <ErrorState error={movies.error} onRetry={() => movies.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState title={filtered ? 'No movies match these filters' : `No shows in ${city.name} right now`} icon={<FilmIcon />}>
          {filtered ? (
            <button type="button" className="btn btn-outline btn-sm" onClick={() => setParams({})}>
              Clear filters
            </button>
          ) : (
            <p>Try another city from the top of the page.</p>
          )}
        </EmptyState>
      ) : (
        <>
          <div className="movie-grid">
            {items.map((movie, i) => (
              <MovieCard key={movie.id} movie={movie} eager={i < 5} />
            ))}
          </div>
          <Pager
            page={page}
            totalPages={movies.data.totalPages}
            onPage={(p) => {
              const next = new URLSearchParams(params);
              next.set('page', String(p));
              setParams(next);
              window.scrollTo({ top: 0 });
            }}
          />
        </>
      )}
    </div>
  );
}
