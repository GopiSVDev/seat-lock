import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { searchCities } from '../api/endpoints';
import type { City } from '../api/types';
import { SearchIcon } from '../components/Icons';
import { ErrorState, Skeleton } from '../components/States';
import { useCity } from '../hooks/useCity';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import '../styles/browse.css';

function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), ms);
    return () => window.clearTimeout(timer);
  }, [value, ms]);
  return debounced;
}

export function CityPage() {
  useDocumentTitle('Select your city');
  const [city, setCity] = useCity();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [text, setText] = useState('');
  const query = useDebounced(text.trim(), 200);

  const results = useQuery({
    queryKey: ['cities', 'search', query],
    queryFn: ({ signal }) => searchCities({ query, withTheatres: true }, signal),
    enabled: query.length > 0,
  });
  const withCinemas = useQuery({ queryKey: ['cities', 'with-theatres'], queryFn: ({ signal }) => searchCities({ withTheatres: true }, signal) });
  const popular = useQuery({ queryKey: ['cities', 'popular'], queryFn: ({ signal }) => searchCities({ popular: true }, signal) });

  const choose = (picked: Pick<City, 'id' | 'name'>) => {
    setCity({ id: picked.id, name: picked.name });
    const returnTo = params.get('returnTo');
    navigate(returnTo && !returnTo.startsWith('/city') ? returnTo : '/');
  };

  const open = new Set((withCinemas.data ?? []).map((c) => c.id));
  const popularOpen = (popular.data ?? []).filter((c) => open.has(c.id));
  const others = (withCinemas.data ?? []).filter((c) => !popularOpen.some((p) => p.id === c.id));

  return (
    <div className="container page city-page">
      <div className="page-intro">
        <h1 className="page-title">Select your city</h1>
        <p className="muted">Old names and short names work too, like Madras or Bombay.</p>
      </div>

      <div className="search">
        <SearchIcon size={20} />
        <label htmlFor="city-search" className="visually-hidden">
          Search for your city
        </label>
        <input
          id="city-search"
          className="input"
          type="search"
          autoComplete="off"
          autoFocus
          placeholder="Search for your city"
          value={text}
          maxLength={50}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && results.data?.[0]) choose(results.data[0]);
          }}
        />
      </div>

      {query && (
        <section aria-live="polite" className="city-results" aria-label="Matching cities">
          {results.isPending ? (
            <div style={{ padding: 16 }}>
              <Skeleton height={18} width="40%" />
            </div>
          ) : results.isError ? (
            <ErrorState error={results.error} onRetry={() => results.refetch()} title="Search isn't working" />
          ) : results.data.length === 0 ? (
            <p style={{ padding: '14px 16px' }} className="muted">
              No city with cinemas matches “{query}”.
            </p>
          ) : (
            <ul>
              {results.data.map((c) => (
                <li key={c.id}>
                  <button type="button" className="city-row" onClick={() => choose(c)}>
                    <span>
                      <strong>{c.name}</strong>
                      {c.matchedAlias && <span className="faint"> ({c.matchedAlias})</span>}
                    </span>
                    <span className="faint">{c.state}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      <section className="section" aria-labelledby="popular-title">
        <h2 id="popular-title" className="section-title">
          Popular cities
        </h2>
        {popular.isPending || withCinemas.isPending ? (
          <div className="city-tiles">
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} height={72} />
            ))}
          </div>
        ) : popular.isError ? (
          <ErrorState error={popular.error} onRetry={() => popular.refetch()} />
        ) : (
          <ul className="city-tiles">
            {popularOpen.map((c) => (
              <li key={c.id}>
                <button type="button" className="city-tile" aria-current={city?.id === c.id || undefined} onClick={() => choose(c)}>
                  <strong>{c.name}</strong>
                  <span>{c.state}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {others.length > 0 && (
        <section className="section" aria-labelledby="all-title">
          <h2 id="all-title" className="section-title">
            Other cities
          </h2>
          <ul className="city-list">
            {others.map((c) => (
              <li key={c.id}>
                <button type="button" onClick={() => choose(c)}>
                  {c.name}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
