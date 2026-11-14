import { useQuery } from '@tanstack/react-query';
import { useEffect, useId, useState } from 'react';
import { NavLink, Outlet } from 'react-router';
import { ApiError, errorMessage } from '../../api/client';
import { listTheatres, searchCities } from '../../api/endpoints';
import type { City, Screen, Theatre } from '../../api/types';
import { RequireRole } from '../../auth/guards';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { local, readJson, writeJson } from '../../lib/storage';
import '../../styles/admin.css';

export function AdminLayout() {
  useDocumentTitle('Admin');
  return (
    <RequireRole role="ADMIN">
      <div className="container page admin">
        <h1 className="page-title" style={{ marginBottom: 16 }}>
          Admin
        </h1>
        <nav className="tabs" aria-label="Admin sections">
          <NavLink to="/admin/movies">Movies</NavLink>
          <NavLink to="/admin/theatres">Cinemas</NavLink>
          <NavLink to="/admin/screens">Screens</NavLink>
          <NavLink to="/admin/shows">Shows</NavLink>
          <NavLink to="/admin/cities">Cities</NavLink>
        </nav>
        <Outlet />
      </div>
    </RequireRole>
  );
}

/** Field messages from a 400's `errors` map, plus the overall message. */
export type FormErrors = { fields: Record<string, string>; message: string | null };

// Bean Validation's default messages, in the words of a form
const FRIENDLY: [RegExp, string][] = [
  [/^must not be (null|blank)$/, 'Required'],
  [/^must not be empty$/, 'Choose at least one'],
  [/^must be greater than 0$/, 'Must be more than 0'],
  [/^must be a well-formed URL|^must be a valid URL$/, 'Enter a full link starting with https://'],
];
const friendly = (message: string) => {
  const hit = FRIENDLY.find(([pattern]) => pattern.test(message));
  return hit ? hit[1] : message.charAt(0).toUpperCase() + message.slice(1);
};

export function formErrors(error: unknown): FormErrors {
  if (error instanceof ApiError) {
    const fields = Object.fromEntries(Object.entries(error.errors ?? {}).map(([k, v]) => [k, friendly(v)]));
    const hasFields = Object.keys(fields).length > 0;
    return { fields, message: hasFields ? 'Please fix the highlighted fields.' : error.detail };
  }
  return { fields: {}, message: errorMessage(error) };
}

export const noErrors: FormErrors = { fields: {}, message: null };

function useDebounced(value: string, ms = 250) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = window.setTimeout(() => setV(value), ms);
    return () => window.clearTimeout(t);
  }, [value, ms]);
  return v;
}

/** Search-as-you-type city chooser over all cities (not only those with cinemas). */
export function CityPicker({ value, onChange, error, label = 'City' }: { value: City | null; onChange: (city: City | null) => void; error?: string; label?: string }) {
  const id = useId();
  const [text, setText] = useState('');
  const query = useDebounced(text.trim());
  const results = useQuery({ queryKey: ['cities', 'admin', query], queryFn: () => searchCities({ query }), enabled: query.length > 0 });
  return (
    <div className="field" data-invalid={error ? 'true' : undefined}>
      <label htmlFor={id}>{label}</label>
      {value ? (
        <div className="picked">
          <span>
            <strong>{value.name}</strong>, {value.state}
          </span>
          <button type="button" className="link" onClick={() => onChange(null)}>
            Change
          </button>
        </div>
      ) : (
        <>
          <input id={id} className="input" placeholder="Search city" value={text} onChange={(e) => setText(e.target.value)} autoComplete="off" />
          {results.data && results.data.length > 0 && (
            <ul className="pick-list">
              {results.data.map((c) => (
                <li key={c.id}>
                  <button type="button" onClick={() => onChange(c)}>
                    {c.name}
                    {c.matchedAlias && ` (${c.matchedAlias})`}
                    <span className="muted"> {c.state}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {results.data && results.data.length === 0 && <span className="field-hint">No city matches.</span>}
        </>
      )}
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}

export function TheatrePicker({ value, onChange, error }: { value: Theatre | null; onChange: (t: Theatre | null) => void; error?: string }) {
  const id = useId();
  const [text, setText] = useState('');
  const query = useDebounced(text.trim());
  const results = useQuery({ queryKey: ['theatres', 'admin', query], queryFn: () => listTheatres({ query, size: 8 }) });
  return (
    <div className="field" data-invalid={error ? 'true' : undefined}>
      <label htmlFor={id}>Cinema</label>
      {value ? (
        <div className="picked">
          <span>
            <strong>{value.name}</strong>, {value.city.name}
          </span>
          <button type="button" className="link" onClick={() => onChange(null)}>
            Change
          </button>
        </div>
      ) : (
        <>
          <input id={id} className="input" placeholder="Search cinema" value={text} onChange={(e) => setText(e.target.value)} autoComplete="off" />
          {results.data && (
            <ul className="pick-list">
              {results.data.items.map((t) => (
                <li key={t.id}>
                  <button type="button" onClick={() => onChange(t)}>
                    {t.name}
                    <span className="muted"> {t.city.name}</span>
                  </button>
                </li>
              ))}
              {results.data.items.length === 0 && <li className="field-hint">No cinema found. Add it under Cinemas first.</li>}
            </ul>
          )}
        </>
      )}
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}

// The API can't list a cinema's screens, so screens seen in this browser are remembered for the show form.
export type KnownScreen = { id: number; name: string; theatreName: string; seatTypes: string[]; totalSeats: number };
const SCREENS_KEY = 'seatlock.admin.screens';

export function knownScreens(): KnownScreen[] {
  return readJson<KnownScreen[]>(local, SCREENS_KEY) ?? [];
}

export function rememberScreen(screen: Screen, theatreName: string): void {
  const seatTypes = [...new Set(screen.rows.flatMap((r) => r.seats.map((s) => s.seatType)))];
  const entry: KnownScreen = { id: screen.id, name: screen.name, theatreName, seatTypes, totalSeats: screen.totalSeats };
  writeJson(local, SCREENS_KEY, [entry, ...knownScreens().filter((s) => s.id !== screen.id)].slice(0, 30));
}

export function SaveBar({ busy, label, errors, saved }: { busy: boolean; label: string; errors: FormErrors; saved?: string | null }) {
  return (
    <div className="save-bar">
      <button type="submit" className="btn" disabled={busy}>
        {busy ? 'Saving…' : label}
      </button>
      <div aria-live="polite">
        {errors.message && <span className="field-error">{errors.message}</span>}
        {!errors.message && saved && <span className="saved">{saved}</span>}
      </div>
    </div>
  );
}
