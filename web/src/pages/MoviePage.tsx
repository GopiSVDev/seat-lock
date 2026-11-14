import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import { getMovie, movieShowtimes } from '../api/endpoints';
import type { Language, ShowFormat } from '../api/types';
import { DateStrip } from '../components/DateStrip';
import { FilterMenu } from '../components/FilterMenu';
import { Poster } from '../components/Poster';
import { Showtimes } from '../components/Showtimes';
import { EmptyState, ErrorState, Skeleton } from '../components/States';
import { useCity } from '../hooks/useCity';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { certificationName, formatDate, formatDuration, formatLongDay, formatName, genreName, languageName } from '../lib/format';
import '../styles/browse.css';

export function MoviePage() {
  const movieId = Number(useParams().id);
  const [city] = useCity();
  const [params, setParams] = useSearchParams();
  const date = params.get('date') ?? undefined;
  const language = params.getAll('language') as Language[];
  const format = params.getAll('format') as ShowFormat[];

  const movie = useQuery({ queryKey: ['movie', movieId], queryFn: () => getMovie(movieId) });
  const shows = useQuery({
    queryKey: ['showtimes', movieId, city?.id, date, language, format],
    queryFn: () => movieShowtimes({ movieId, cityId: city!.id, date, language, format }),
    enabled: !!city,
    placeholderData: keepPreviousData,
  });
  // what this film plays in, across all days, for the filter menus
  const everything = useQuery({
    queryKey: ['showtimes', movieId, city?.id, date],
    queryFn: () => movieShowtimes({ movieId, cityId: city!.id, date }),
    enabled: !!city,
  });
  useDocumentTitle(movie.data?.title);

  if (movie.isPending) return <MovieSkeleton />;
  if (movie.isError) return <ErrorState error={movie.error} onRetry={() => movie.refetch()} title="Couldn't load this movie" />;
  const m = movie.data;

  const set = (mutate: (p: URLSearchParams) => void) => {
    const next = new URLSearchParams(params);
    mutate(next);
    setParams(next, { replace: true });
  };
  const playing = everything.data?.theatres.flatMap((t) => t.shows) ?? [];
  const languages = [...new Set([...language, ...playing.map((s) => s.language)])];
  const formats = [...new Set([...format, ...playing.map((s) => s.format)])];
  const firstDay = !date && shows.data && shows.data.theatres.length === 0 && shows.data.dates[0];

  return (
    <>
      <section className="movie-hero">
        {m.posterUrl && <div className="movie-hero-backdrop" style={{ backgroundImage: `url("${m.posterUrl}")` }} aria-hidden="true" />}
        <div className="container movie-hero-inner">
          <div className="movie-hero-poster">
            <Poster id={m.id} title={m.title} posterUrl={m.posterUrl} size="hero" eager />
          </div>
          <div className="movie-hero-info">
            <h1>{m.title}</h1>
            <p className="movie-meta">
              <span className="tag">{certificationName(m.certification)}</span>
              <span>{formatDuration(m.durationMinutes)}</span>
              <span className="movie-meta-dot">{formatDate(m.releaseDate)}</span>
            </p>
            <p className="movie-genres">{m.genres.map(genreName).join(', ')}</p>
            {languages.length > 0 && (
              <p className="movie-langs">
                {languages.map((l) => (
                  <span key={l}>{languageName(l)}</span>
                ))}
                {formats.map((f) => (
                  <span key={f}>{formatName(f)}</span>
                ))}
              </p>
            )}
            {city && (
              <a href="#showtimes" className="btn">
                Book tickets
              </a>
            )}
          </div>
          {m.description && <p className="movie-about">{m.description}</p>}
        </div>
      </section>

      <div className="container">
        {!city ? (
          <EmptyState title="Select a city to see showtimes">
            <Link className="btn" to={`/city?returnTo=/movies/${movieId}`}>
              Select city
            </Link>
          </EmptyState>
        ) : (
          <section id="showtimes" aria-label={`Showtimes in ${city.name}`}>
            {shows.isPending ? (
              <div style={{ paddingTop: 20, display: 'grid', gap: 12 }}>
                <Skeleton height={64} width={320} />
                <Skeleton height={90} />
                <Skeleton height={90} />
              </div>
            ) : shows.isError ? (
              <ErrorState error={shows.error} onRetry={() => shows.refetch()} />
            ) : firstDay ? (
              <EmptyState title="No more shows today">
                <button type="button" className="btn btn-outline btn-sm" onClick={() => set((p) => p.set('date', firstDay))}>
                  See shows on {formatLongDay(`${firstDay}T12:00:00+05:30`)}
                </button>
              </EmptyState>
            ) : (
              <>
                <div className="showtimes-bar">
                  {shows.data.dates.length > 0 && <DateStrip dates={shows.data.dates} selected={shows.data.date} onSelect={(d) => set((p) => p.set('date', d))} />}
                  <div className="filter-bar">
                    <FilterMenu label="Language" options={languages} selected={language} name={languageName} onChange={(v) => set((p) => { p.delete('language'); v.forEach((x) => p.append('language', x)); })} />
                    <FilterMenu label="Format" options={formats} selected={format} name={formatName} onChange={(v) => set((p) => { p.delete('format'); v.forEach((x) => p.append('format', x)); })} />
                  </div>
                </div>
                {shows.data.theatres.length === 0 ? (
                  <EmptyState title={shows.data.dates.length === 0 ? `Not playing in ${city.name}` : 'No shows match on this day'}>
                    <p>{shows.data.dates.length === 0 ? 'Try another city.' : 'Pick another date or clear the filters.'}</p>
                  </EmptyState>
                ) : (
                  <Showtimes groups={shows.data.theatres.map((t) => ({ key: t.id, title: t.name, subtitle: t.address, href: `/theatres/${t.id}`, shows: t.shows }))} />
                )}
              </>
            )}
          </section>
        )}
      </div>
    </>
  );
}

function MovieSkeleton() {
  return (
    <section className="movie-hero" aria-busy="true">
      <div className="container movie-hero-inner">
        <div className="skeleton" style={{ aspectRatio: '2 / 3', background: 'rgba(255,255,255,0.1)' }} />
        <div style={{ display: 'grid', gap: 12 }}>
          <div className="skeleton" style={{ height: 32, width: '60%', background: 'rgba(255,255,255,0.1)' }} />
          <div className="skeleton" style={{ height: 16, width: '40%', background: 'rgba(255,255,255,0.1)' }} />
        </div>
      </div>
    </section>
  );
}
