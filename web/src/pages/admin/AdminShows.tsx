import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { createShow, getScreen, getTheatre, listMovies } from '../../api/endpoints';
import { FORMATS, LANGUAGES, type Language, type SeatType, type ShowDetails, type ShowFormat } from '../../api/types';
import { Field } from '../../components/Field';
import { Notice } from '../../components/States';
import { formatDay, formatName, formatTime, languageName, seatTypeName } from '../../lib/format';
import { indiaLocalToInstant, indiaToday } from '../../lib/time';
import { formErrors, knownScreens, noErrors, rememberScreen, SaveBar, type FormErrors } from './shared';

export function AdminShows() {
  const [params] = useSearchParams();
  const [movieId, setMovieId] = useState('');
  const [screenId, setScreenId] = useState(params.get('screen') ?? '');
  const [date, setDate] = useState(indiaToday());
  const [time, setTime] = useState('19:30');
  const [language, setLanguage] = useState<Language>('TAMIL');
  const [format, setFormat] = useState<ShowFormat>('TWO_D');
  const [prices, setPrices] = useState<Partial<Record<SeatType, string>>>({});
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [created, setCreated] = useState<ShowDetails | null>(null);

  const movies = useQuery({ queryKey: ['movies', 'catalogue', 'all'], queryFn: () => listMovies({ size: 50 }) });
  const known = useMemo(() => knownScreens(), []);
  const screen = useQuery({ queryKey: ['screen', Number(screenId)], queryFn: () => getScreen(Number(screenId)), enabled: /^\d+$/.test(screenId), retry: false });
  const theatre = useQuery({ queryKey: ['theatre', screen.data?.theatreId], queryFn: () => getTheatre(screen.data!.theatreId), enabled: !!screen.data });
  const seatTypes = useMemo(() => [...new Set(screen.data?.rows.flatMap((r) => r.seats.map((s) => s.seatType)) ?? [])], [screen.data]);

  useEffect(() => {
    if (screen.data && theatre.data) rememberScreen(screen.data, theatre.data.name);
  }, [screen.data, theatre.data]);

  const save = useMutation({
    mutationFn: () =>
      createShow({
        movieId: Number(movieId) || (undefined as unknown as number),
        screenId: Number(screenId) || (undefined as unknown as number),
        startTime: date && time ? indiaLocalToInstant(date, time) : (undefined as unknown as string),
        language,
        format,
        // typed in rupees, sent in paise
        prices: Object.fromEntries(seatTypes.map((t) => [t, Math.round(Number(prices[t] || 0) * 100)])),
      }),
    onSuccess: (show) => {
      setErrors(noErrors);
      setCreated(show);
    },
    onError: (e) => {
      setCreated(null);
      setErrors(formErrors(e));
    },
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };
  const f = errors.fields;
  const priceError = f.prices ?? Object.entries(f).find(([k]) => k.startsWith('prices'))?.[1];

  return (
    <div className="admin-split">
      <form className="admin-form" onSubmit={submit} noValidate>
        <h2>Schedule show</h2>
        <Field label="Movie" error={f.movieId}>
          <select className="select" value={movieId} onChange={(e) => setMovieId(e.target.value)}>
            <option value="">Select movie</option>
            {movies.data?.items.map((m) => (
              <option key={m.id} value={m.id}>
                {m.title}
              </option>
            ))}
          </select>
        </Field>
        <Field
          label="Screen ID"
          error={f.screenId ?? (screen.isError ? 'No screen with this ID' : undefined)}
          hint={screen.data ? `${screen.data.name}${theatre.data ? ` at ${theatre.data.name}` : ''}, ${screen.data.totalSeats} seats` : 'Shown after saving a screen; recent screens are listed below.'}
        >
          <input className="input" inputMode="numeric" value={screenId} onChange={(e) => setScreenId(e.target.value.replace(/\D/g, ''))} />
        </Field>
        {known.length > 0 && (
          <div className="chips" aria-label="Recent screens">
            {known.map((s) => (
              <button type="button" key={s.id} className="chip" aria-pressed={String(s.id) === screenId} onClick={() => setScreenId(String(s.id))}>
                {s.theatreName}, {s.name}
              </button>
            ))}
          </div>
        )}
        <div className="form-row">
          <Field label="Date (IST)" error={f.startTime}>
            <input className="input" type="date" min={indiaToday()} value={date} onChange={(e) => setDate(e.target.value)} />
          </Field>
          <Field label="Start time">
            <input className="input" type="time" value={time} onChange={(e) => setTime(e.target.value)} />
          </Field>
        </div>
        <div className="form-row">
          <Field label="Language" error={f.language}>
            <select className="select" value={language} onChange={(e) => setLanguage(e.target.value as Language)}>
              {LANGUAGES.map((l) => (
                <option key={l} value={l}>
                  {languageName(l)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Format" error={f.format}>
            <select className="select" value={format} onChange={(e) => setFormat(e.target.value as ShowFormat)}>
              {FORMATS.map((x) => (
                <option key={x} value={x}>
                  {formatName(x)}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <fieldset className="field" data-invalid={priceError ? 'true' : undefined}>
          <legend className="field-label">Ticket prices (₹)</legend>
          {seatTypes.length === 0 ? (
            <span className="field-hint">Enter a screen ID to set prices for its seat types.</span>
          ) : (
            <div className="form-row">
              {seatTypes.map((t) => (
                <Field key={t} label={seatTypeName(t)}>
                  <input className="input" inputMode="decimal" placeholder="250" value={prices[t] ?? ''} onChange={(e) => setPrices((p) => ({ ...p, [t]: e.target.value.replace(/[^\d.]/g, '') }))} />
                </Field>
              ))}
            </div>
          )}
          {priceError && <span className="field-error">{priceError}</span>}
        </fieldset>
        <SaveBar busy={save.isPending} label="Schedule show" errors={errors} />
        {created && (
          <Notice tone="ok" title={`Show scheduled (ID ${created.id})`}>
            {created.movieTitle} on {formatDay(created.startTime)} at {formatTime(created.startTime)}, {created.screenName}. <Link to={`/shows/${created.id}`}>View seat layout</Link>
          </Notice>
        )}
      </form>
    </div>
  );
}
