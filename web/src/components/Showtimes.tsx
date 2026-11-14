import { useQueryClient } from '@tanstack/react-query';
import type React from 'react';
import { Link } from 'react-router';
import { getAvailability, getLayout, getShow } from '../api/endpoints';
import type { Showtime } from '../api/types';
import { formatName, formatTime, languageName } from '../lib/format';

export type ShowGroup = { key: number; title: string; subtitle?: string; href?: string; lead?: React.ReactNode; shows: Showtime[] };

/** Showtimes grouped by cinema (movie page) or by film (cinema page). */
export function Showtimes({ groups }: { groups: ShowGroup[] }) {
  const queryClient = useQueryClient();
  // the seat map is the next page: start loading it on hover
  const prefetch = (id: number) => {
    queryClient.prefetchQuery({ queryKey: ['show', id], queryFn: () => getShow(id) });
    queryClient.prefetchQuery({ queryKey: ['layout', id], queryFn: () => getLayout(id), staleTime: Infinity });
    queryClient.prefetchQuery({ queryKey: ['availability', id], queryFn: () => getAvailability(id), staleTime: 2000 });
  };
  return (
    <ul className="venues">
      {groups.map((group) => (
        <li key={group.key} className="venue" data-lead={group.lead ? '' : undefined}>
          {group.lead}
          <div className="venue-head">
            <h3>{group.href ? <Link to={group.href}>{group.title}</Link> : group.title}</h3>
            {group.subtitle && <p>{group.subtitle}</p>}
          </div>
          <ul className="times" aria-label={`Shows, ${group.title}`}>
            {group.shows.map((show) => (
              <li key={show.id}>
                <Link
                  className="time"
                  to={`/shows/${show.id}`}
                  onPointerEnter={() => prefetch(show.id)}
                  onFocus={() => prefetch(show.id)}
                  aria-label={`${formatTime(show.startTime)}, ${languageName(show.language)} ${formatName(show.format)}, ${show.screenName}`}
                >
                  <strong className="num">{formatTime(show.startTime)}</strong>
                  <span>
                    {languageName(show.language)} · {formatName(show.format)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </li>
      ))}
    </ul>
  );
}
