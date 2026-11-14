import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import { theatreShowtimes } from '../api/endpoints';
import type { Language, ShowFormat } from '../api/types';
import { DateStrip } from '../components/DateStrip';
import { FilterMenu } from '../components/FilterMenu';
import { BackIcon } from '../components/Icons';
import { Poster } from '../components/Poster';
import { Showtimes } from '../components/Showtimes';
import { EmptyState, ErrorState, Skeleton } from '../components/States';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { certificationName, formatDuration, formatLongDay, formatName, languageName } from '../lib/format';
import '../styles/browse.css';

export function TheatrePage() {
  const theatreId = Number(useParams().id);
  const [params, setParams] = useSearchParams();
  const date = params.get('date') ?? undefined;
  const language = params.getAll('language') as Language[];
  const format = params.getAll('format') as ShowFormat[];

  const data = useQuery({
    queryKey: ['theatre-shows', theatreId, date, language, format],
    queryFn: () => theatreShowtimes(theatreId, { date, language, format }),
    placeholderData: keepPreviousData,
  });
  const everything = useQuery({ queryKey: ['theatre-shows', theatreId, date], queryFn: () => theatreShowtimes(theatreId, { date }) });
  useDocumentTitle(data.data?.theatre.name);

  if (data.isPending) {
    return (
      <div className="container cinema-head" aria-busy="true">
        <Skeleton height={32} width="50%" />
        <Skeleton height={16} width="35%" style={{ marginTop: 10 }} />
      </div>
    );
  }
  if (data.isError) return <ErrorState error={data.error} onRetry={() => data.refetch()} title="Couldn't load this cinema" />;
  const { theatre, movies, dates } = data.data;
  const set = (mutate: (p: URLSearchParams) => void) => {
    const next = new URLSearchParams(params);
    mutate(next);
    setParams(next, { replace: true });
  };
  const playing = everything.data?.movies.flatMap((m) => m.shows) ?? [];
  const firstDay = !date && movies.length === 0 && dates[0];

  return (
    <div className="container">
      <div className="cinema-head">
        <Link to="/theatres" className="back">
          <BackIcon size={16} /> All cinemas
        </Link>
        <h1 style={{ marginTop: 12 }}>{theatre.name}</h1>
        <p className="muted">
          {theatre.address}, {theatre.city.name}
        </p>
      </div>
      {firstDay ? (
        <EmptyState title="No more shows today">
          <button type="button" className="btn btn-outline btn-sm" onClick={() => set((p) => p.set('date', firstDay))}>
            See shows on {formatLongDay(`${firstDay}T12:00:00+05:30`)}
          </button>
        </EmptyState>
      ) : (
        <>
          <div className="showtimes-bar">
            {dates.length > 0 && <DateStrip dates={dates} selected={data.data.date} onSelect={(d) => set((p) => p.set('date', d))} />}
            <div className="filter-bar">
              <FilterMenu label="Language" options={[...new Set([...language, ...playing.map((s) => s.language)])]} selected={language} name={languageName} onChange={(v) => set((p) => { p.delete('language'); v.forEach((x) => p.append('language', x)); })} />
              <FilterMenu label="Format" options={[...new Set([...format, ...playing.map((s) => s.format)])]} selected={format} name={formatName} onChange={(v) => set((p) => { p.delete('format'); v.forEach((x) => p.append('format', x)); })} />
            </div>
          </div>
          {movies.length === 0 ? (
            <EmptyState title={dates.length === 0 ? 'No upcoming shows here' : 'No shows match on this day'}>
              <p>{dates.length === 0 ? 'Check back later.' : 'Pick another date or clear the filters.'}</p>
            </EmptyState>
          ) : (
            <Showtimes
              groups={movies.map((m) => ({
                key: m.id,
                title: m.title,
                subtitle: `${certificationName(m.certification)} · ${formatDuration(m.durationMinutes)}`,
                href: `/movies/${m.id}`,
                lead: <Poster id={m.id} title={m.title} posterUrl={m.posterUrl} size="thumb" />,
                shows: m.shows,
              }))}
            />
          )}
        </>
      )}
    </div>
  );
}
