import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { createTheatre, listTheatres } from '../../api/endpoints';
import type { City } from '../../api/types';
import { Field } from '../../components/Field';
import { Pager } from '../../components/Pager';
import { ErrorState, Skeleton } from '../../components/States';
import { CityPicker, formErrors, noErrors, SaveBar, type FormErrors } from './shared';

export function AdminTheatres() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [city, setCity] = useState<City | null>(null);
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [saved, setSaved] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const theatres = useQuery({ queryKey: ['theatres', 'all', page], queryFn: () => listTheatres({ page, size: 10 }), placeholderData: keepPreviousData });

  const save = useMutation({
    mutationFn: () => createTheatre({ name, address, cityId: city?.id ?? (undefined as unknown as number) }),
    onSuccess: (t) => {
      setErrors(noErrors);
      setSaved(`${t.name}, ${t.city.name} added (ID ${t.id}). Add its screens next.`);
      setName('');
      setAddress('');
      queryClient.invalidateQueries({ queryKey: ['theatres'] });
      queryClient.invalidateQueries({ queryKey: ['cities'] });
    },
    onError: (e) => {
      setSaved(null);
      setErrors(formErrors(e));
    },
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };
  const f = errors.fields;

  return (
    <div className="admin-split">
      <form className="admin-form" onSubmit={submit} noValidate>
        <h2>Add cinema</h2>
        <Field label="Name" error={f.name}>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <CityPicker value={city} onChange={setCity} error={f.cityId} />
        <Field label="Address" error={f.address}>
          <input className="input" value={address} onChange={(e) => setAddress(e.target.value)} />
        </Field>
        <SaveBar busy={save.isPending} label="Add cinema" errors={errors} saved={saved} />
      </form>
      <section className="admin-list" aria-labelledby="cinemas-title">
        <h2 id="cinemas-title">Cinemas</h2>
        {theatres.isPending ? (
          <Skeleton height={200} />
        ) : theatres.isError ? (
          <ErrorState error={theatres.error} onRetry={() => theatres.refetch()} />
        ) : theatres.data.items.length === 0 ? (
          <p className="muted">No cinemas yet.</p>
        ) : (
          <>
            <ul className="record-list">
              {theatres.data.items.map((t) => (
                <li key={t.id}>
                  <span className="record-body">
                    <strong>{t.name}</strong>
                    <span className="muted">
                      #{t.id} · {t.address}, {t.city.name}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
            <Pager page={page} totalPages={theatres.data.totalPages} onPage={setPage} />
          </>
        )}
      </section>
    </div>
  );
}
