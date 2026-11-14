import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { addCityAlias, createCity } from '../../api/endpoints';
import type { City } from '../../api/types';
import { Field } from '../../components/Field';
import { CityPicker, formErrors, noErrors, SaveBar, type FormErrors } from './shared';

export function AdminCities() {
  const queryClient = useQueryClient();
  const [city, setCity] = useState<City | null>(null);
  const [alias, setAlias] = useState('');
  const [aliasErrors, setAliasErrors] = useState<FormErrors>(noErrors);
  const [aliasSaved, setAliasSaved] = useState<string | null>(null);
  const [newCity, setNewCity] = useState({ name: '', state: '' });
  const [cityErrors, setCityErrors] = useState<FormErrors>(noErrors);
  const [citySaved, setCitySaved] = useState<string | null>(null);

  const saveAlias = useMutation({
    mutationFn: () => addCityAlias(city?.id ?? 0, alias),
    onSuccess: (a) => {
      setAliasErrors(noErrors);
      setAliasSaved(`Searching “${a.alias}” now finds ${city?.name}.`);
      setAlias('');
      queryClient.invalidateQueries({ queryKey: ['cities'] });
    },
    onError: (e) => {
      setAliasSaved(null);
      setAliasErrors(formErrors(e));
    },
  });
  const saveCity = useMutation({
    mutationFn: () => createCity({ name: newCity.name, state: newCity.state }),
    onSuccess: (c) => {
      setCityErrors(noErrors);
      setCitySaved(`${c.name}, ${c.state} added (ID ${c.id}).`);
      setNewCity({ name: '', state: '' });
      queryClient.invalidateQueries({ queryKey: ['cities'] });
    },
    onError: (e) => {
      setCitySaved(null);
      setCityErrors(formErrors(e));
    },
  });

  return (
    <div className="admin-split">
      <form
        className="admin-form"
        noValidate
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          if (!city) return setAliasErrors({ fields: { city: 'Pick a city first' }, message: null });
          saveAlias.mutate();
        }}
      >
        <h2>Add alternate name</h2>
        <p className="muted">Old names, short forms and common spellings that should find the city in search.</p>
        <CityPicker value={city} onChange={setCity} error={aliasErrors.fields.city} />
        <Field label="Alternate name" error={aliasErrors.fields.alias} hint="For example, Tiruchi for Tiruchirappalli.">
          <input className="input" value={alias} onChange={(e) => setAlias(e.target.value)} />
        </Field>
        <SaveBar busy={saveAlias.isPending} label="Add name" errors={aliasErrors} saved={aliasSaved} />
      </form>
      <form
        className="admin-form"
        noValidate
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          saveCity.mutate();
        }}
      >
        <h2>Add city</h2>
        <Field label="City" error={cityErrors.fields.name}>
          <input className="input" value={newCity.name} onChange={(e) => setNewCity((c) => ({ ...c, name: e.target.value }))} />
        </Field>
        <Field label="State" error={cityErrors.fields.state}>
          <input className="input" value={newCity.state} onChange={(e) => setNewCity((c) => ({ ...c, state: e.target.value }))} />
        </Field>
        <SaveBar busy={saveCity.isPending} label="Add city" errors={cityErrors} saved={citySaved} />
      </form>
    </div>
  );
}
