import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { createMovie, listMovies } from '../../api/endpoints';
import { CERTIFICATIONS, GENRES, type Certification, type Genre } from '../../api/types';
import { Field } from '../../components/Field';
import { Pager } from '../../components/Pager';
import { Poster } from '../../components/Poster';
import { ErrorState, Skeleton } from '../../components/States';
import { certificationName, formatDuration, genreName } from '../../lib/format';
import { formErrors, noErrors, SaveBar, type FormErrors } from './shared';

const blank = { title: '', description: '', durationMinutes: '', certification: 'UA' as Certification, releaseDate: '', posterUrl: '', genres: [] as Genre[] };

export function AdminMovies() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(blank);
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [saved, setSaved] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const movies = useQuery({ queryKey: ['movies', 'catalogue', page], queryFn: () => listMovies({ page, size: 10 }), placeholderData: keepPreviousData });

  const save = useMutation({
    mutationFn: () =>
      createMovie({
        title: form.title,
        description: form.description || undefined,
        durationMinutes: Number(form.durationMinutes) || 0,
        certification: form.certification,
        releaseDate: form.releaseDate,
        posterUrl: form.posterUrl || undefined,
        genres: form.genres,
      }),
    onSuccess: (m) => {
      setErrors(noErrors);
      setSaved(`${m.title} added (ID ${m.id}).`);
      setForm(blank);
      queryClient.invalidateQueries({ queryKey: ['movies'] });
    },
    onError: (e) => {
      setSaved(null);
      setErrors(formErrors(e));
    },
  });

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) => setForm((f) => ({ ...f, [key]: value }));
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };
  const f = errors.fields;

  return (
    <div className="admin-split">
      <form className="admin-form" onSubmit={submit} noValidate>
        <h2>Add movie</h2>
        <Field label="Title" error={f.title}>
          <input className="input" value={form.title} onChange={(e) => set('title', e.target.value)} />
        </Field>
        <Field label="Synopsis" error={f.description}>
          <textarea className="textarea" value={form.description} onChange={(e) => set('description', e.target.value)} />
        </Field>
        <div className="form-row">
          <Field label="Runtime (minutes)" error={f.durationMinutes}>
            <input className="input" inputMode="numeric" value={form.durationMinutes} onChange={(e) => set('durationMinutes', e.target.value.replace(/\D/g, ''))} />
          </Field>
          <Field label="Release date" error={f.releaseDate}>
            <input className="input" type="date" value={form.releaseDate} onChange={(e) => set('releaseDate', e.target.value)} />
          </Field>
        </div>
        <fieldset className="field" data-invalid={f.certification ? 'true' : undefined}>
          <legend className="field-label">Certificate (CBFC)</legend>
          <div className="chips">
            {CERTIFICATIONS.map((c) => (
              <button type="button" key={c} className="chip" aria-pressed={form.certification === c} onClick={() => set('certification', c)}>
                {certificationName(c)}
              </button>
            ))}
          </div>
          {f.certification && <span className="field-error">{f.certification}</span>}
        </fieldset>
        <fieldset className="field" data-invalid={f.genres ? 'true' : undefined}>
          <legend className="field-label">Genres (up to 3)</legend>
          <div className="chips">
            {GENRES.map((g) => {
              const on = form.genres.includes(g);
              return (
                <button type="button" key={g} className="chip" aria-pressed={on} disabled={!on && form.genres.length >= 3} onClick={() => set('genres', on ? form.genres.filter((x) => x !== g) : [...form.genres, g])}>
                  {genreName(g)}
                </button>
              );
            })}
          </div>
          {f.genres && <span className="field-error">{f.genres}</span>}
        </fieldset>
        <Field label="Poster URL" error={f.posterUrl} hint="Optional. A 2:3 image link.">
          <input className="input" type="url" placeholder="https://" value={form.posterUrl} onChange={(e) => set('posterUrl', e.target.value)} />
        </Field>
        <SaveBar busy={save.isPending} label="Add movie" errors={errors} saved={saved} />
      </form>

      <section className="admin-list" aria-labelledby="films-title">
        <h2 id="films-title">Catalogue</h2>
        {movies.isPending ? (
          <Skeleton height={200} />
        ) : movies.isError ? (
          <ErrorState error={movies.error} onRetry={() => movies.refetch()} />
        ) : movies.data.items.length === 0 ? (
          <p className="muted">No movies yet.</p>
        ) : (
          <>
            <ul className="record-list">
              {movies.data.items.map((m) => (
                <li key={m.id}>
                  <Poster id={m.id} title={m.title} posterUrl={m.posterUrl} size="thumb" />
                  <span className="record-body">
                    <strong>{m.title}</strong>
                    <span className="muted">
                      #{m.id} · {certificationName(m.certification)} · {formatDuration(m.durationMinutes)} · {m.genres.map(genreName).join(', ')}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
            <Pager page={page} totalPages={movies.data.totalPages} onPage={setPage} />
          </>
        )}
      </section>
    </div>
  );
}
