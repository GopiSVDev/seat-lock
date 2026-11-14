import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router';
import { listTheatres } from '../api/endpoints';
import { ChevronRight, SearchIcon } from '../components/Icons';
import { Pager } from '../components/Pager';
import { EmptyState, ErrorState, Skeleton } from '../components/States';
import { useCity } from '../hooks/useCity';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import '../styles/browse.css';

export function TheatresPage() {
  const [city] = useCity();
  const [params, setParams] = useSearchParams();
  const query = params.get('query') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [text, setText] = useState(query);
  useDocumentTitle(city ? `Cinemas in ${city.name}` : 'Cinemas');

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (text.trim() !== query) setParams(text.trim() ? { query: text.trim() } : {}, { replace: true });
    }, 200);
    return () => window.clearTimeout(timer);
  }, [text, query, setParams]);

  const theatres = useQuery({
    queryKey: ['theatres', city?.id, query, page],
    queryFn: () => listTheatres({ cityId: city!.id, query, page, size: 20 }),
    enabled: !!city,
    placeholderData: keepPreviousData,
  });

  if (!city) return <Navigate to="/city?returnTo=/theatres" replace />;

  return (
    <div className="container page" style={{ maxWidth: 840 }}>
      <div className="page-intro">
        <h1 className="page-title">Cinemas in {city.name}</h1>
      </div>
      <div className="search" style={{ marginBottom: 20 }}>
        <SearchIcon size={20} />
        <label htmlFor="theatre-search" className="visually-hidden">
          Search cinemas
        </label>
        <input id="theatre-search" className="input" type="search" placeholder="Search cinemas" value={text} maxLength={50} onChange={(e) => setText(e.target.value)} />
      </div>
      {theatres.isPending ? (
        <div style={{ display: 'grid', gap: 8 }}>
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} height={68} />
          ))}
        </div>
      ) : theatres.isError ? (
        <ErrorState error={theatres.error} onRetry={() => theatres.refetch()} />
      ) : theatres.data.items.length === 0 ? (
        <EmptyState title={query ? `No cinemas match “${query}”` : `No cinemas in ${city.name} yet`}>
          <p>{query ? 'Try part of the name.' : 'Try another city.'}</p>
        </EmptyState>
      ) : (
        <>
          <ul className="list-card">
            {theatres.data.items.map((t) => (
              <li key={t.id}>
                <Link to={`/theatres/${t.id}`} className="list-row">
                  <span className="list-row-body">
                    <strong>{t.name}</strong>
                    <span>{t.address}</span>
                  </span>
                  <ChevronRight />
                </Link>
              </li>
            ))}
          </ul>
          <Pager page={page} totalPages={theatres.data.totalPages} onPage={(p) => setParams({ ...(query ? { query } : {}), page: String(p) })} />
        </>
      )}
    </div>
  );
}
