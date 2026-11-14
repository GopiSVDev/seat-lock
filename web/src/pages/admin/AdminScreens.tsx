import { useMutation } from '@tanstack/react-query';
import { useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { createScreen } from '../../api/endpoints';
import { SEAT_TYPES, type LayoutRow, type Screen, type SeatType, type Theatre } from '../../api/types';
import { Field } from '../../components/Field';
import { PlusIcon } from '../../components/Icons';
import { SeatMap } from '../../components/SeatMap';
import { Notice } from '../../components/States';
import { plural, seatTypeName } from '../../lib/format';
import { formErrors, noErrors, rememberScreen, SaveBar, TheatrePicker, type FormErrors } from './shared';

type DraftRow = { key: number; label: string; seatCount: string; seatType: SeatType };
let nextKey = 1;
const row = (label: string, seatCount: number, seatType: SeatType): DraftRow => ({ key: nextKey++, label, seatCount: String(seatCount), seatType });

const PRESETS: { name: string; rows: () => DraftRow[] }[] = [
  { name: 'Small', rows: () => ['A', 'B', 'C', 'D', 'E', 'F'].map((l) => row(l, 12, 'REGULAR')) },
  {
    name: 'Standard',
    rows: () => [
      ...'ABCDEFGH'.split('').map((l) => row(l, 18, 'REGULAR')),
      ...'JKL'.split('').map((l) => row(l, 16, 'PREMIUM')),
      row('M', 10, 'RECLINER'),
    ],
  },
  { name: 'Recliner lounge', rows: () => ['A', 'B', 'C'].map((l) => row(l, 8, 'RECLINER')) },
];

/** The next row letter after the last one (A..Z, then AA, AB...). */
export function nextLabel(rows: { label: string }[]): string {
  const last = rows[rows.length - 1]?.label ?? '';
  if (!/^[A-Z]+$/.test(last)) return String.fromCharCode(65 + rows.length);
  const chars = last.split('');
  let i = chars.length - 1;
  while (i >= 0 && chars[i] === 'Z') chars[i--] = 'A';
  if (i < 0) chars.unshift('A');
  else chars[i] = String.fromCharCode(chars[i].charCodeAt(0) + 1);
  return chars.join('');
}

export function AdminScreens() {
  const [theatre, setTheatre] = useState<Theatre | null>(null);
  const [name, setName] = useState('Screen 1');
  const [rows, setRows] = useState<DraftRow[]>(() => PRESETS[1].rows());
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [created, setCreated] = useState<Screen | null>(null);

  // the preview draws the hall exactly as the seat map will
  const preview: LayoutRow[] = useMemo(() => {
    let id = 0;
    return rows.map((r) => ({
      label: r.label || '?',
      seatType: r.seatType,
      price: 0,
      seats: Array.from({ length: Math.min(60, Math.max(0, Number(r.seatCount) || 0)) }, (_, i) => ({ id: ++id, number: i + 1 })),
    }));
  }, [rows]);
  const total = preview.reduce((sum, r) => sum + r.seats.length, 0);

  const save = useMutation({
    mutationFn: () =>
      createScreen(theatre?.id ?? 0, {
        name,
        rows: rows.map((r) => ({ label: r.label, seatCount: Number(r.seatCount) || 0, seatType: r.seatType })),
      }),
    onSuccess: (screen) => {
      setErrors(noErrors);
      setCreated(screen);
      rememberScreen(screen, theatre?.name ?? '');
    },
    onError: (e) => {
      setCreated(null);
      setErrors(formErrors(e));
    },
  });

  const update = (key: number, patch: Partial<DraftRow>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const move = (index: number, dir: -1 | 1) =>
    setRows((rs) => {
      const next = [...rs];
      const [item] = next.splice(index, 1);
      next.splice(index + dir, 0, item);
      return next;
    });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!theatre) return setErrors({ fields: { theatre: 'Select a cinema' }, message: 'Please fix the highlighted fields.' });
    save.mutate();
  };
  const f = errors.fields;

  return (
    <div className="screen-builder">
      <form className="admin-form" onSubmit={submit} noValidate>
        <h2>Add screen</h2>
        <TheatrePicker value={theatre} onChange={setTheatre} error={f.theatre} />
        <Field label="Screen name" error={f.name} hint="Unique within the cinema, like Audi 2 or Gold.">
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <div className="field">
          <span className="field-label">Template</span>
          <div className="chips">
            {PRESETS.map((p) => (
              <button type="button" key={p.name} className="chip" onClick={() => setRows(p.rows())}>
                {p.name}
              </button>
            ))}
          </div>
        </div>
        <fieldset className="row-editor" data-invalid={f.rows ? 'true' : undefined}>
          <legend className="field-label">Rows, from the screen to the back</legend>
          <ol>
            {rows.map((r, i) => (
              <li key={r.key} className="row-edit">
                <label>
                  <span className="visually-hidden">Row {i + 1} label</span>
                  <input className="input row-label-input" value={r.label} maxLength={3} onChange={(e) => update(r.key, { label: e.target.value.toUpperCase() })} aria-invalid={!!f[`rows[${i}].label`]} />
                </label>
                <label>
                  <span className="visually-hidden">Row {r.label} seats</span>
                  <input className="input row-count-input" inputMode="numeric" value={r.seatCount} onChange={(e) => update(r.key, { seatCount: e.target.value.replace(/\D/g, '') })} aria-invalid={!!f[`rows[${i}].seatCount`]} />
                </label>
                <label>
                  <span className="visually-hidden">Row {r.label} seat type</span>
                  <select className="select" value={r.seatType} onChange={(e) => update(r.key, { seatType: e.target.value as SeatType })}>
                    {SEAT_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {seatTypeName(t)}
                      </option>
                    ))}
                  </select>
                </label>
                <span className="row-tools">
                  <button type="button" onClick={() => move(i, -1)} disabled={i === 0} aria-label={`Move row ${r.label} forward`}>
                    ↑
                  </button>
                  <button type="button" onClick={() => move(i, 1)} disabled={i === rows.length - 1} aria-label={`Move row ${r.label} back`}>
                    ↓
                  </button>
                  <button type="button" onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))} disabled={rows.length === 1} aria-label={`Remove row ${r.label}`}>
                    ✕
                  </button>
                </span>
                {(f[`rows[${i}].label`] || f[`rows[${i}].seatCount`] || f[`rows[${i}].seatType`]) && (
                  <span className="field-error row-error">Row {i + 1}: {f[`rows[${i}].label`] ?? f[`rows[${i}].seatCount`] ?? f[`rows[${i}].seatType`]}</span>
                )}
              </li>
            ))}
          </ol>
          <button type="button" className="btn btn-outline btn-sm" onClick={() => setRows((rs) => [...rs, row(nextLabel(rs), Number(rs[rs.length - 1]?.seatCount) || 12, rs[rs.length - 1]?.seatType ?? 'REGULAR')])}>
            <PlusIcon /> Add row
          </button>
          {f.rows && <span className="field-error">{f.rows}</span>}
        </fieldset>
        <SaveBar busy={save.isPending} label={`Save screen (${plural(total, 'seat')})`} errors={errors} />
        {created && (
          <Notice tone="ok" title={`${created.name} saved (ID ${created.id}, ${plural(created.totalSeats, 'seat')})`}>
            <Link to={`/admin/shows?screen=${created.id}`}>Schedule a show</Link>
          </Notice>
        )}
      </form>
      <section className="builder-preview" aria-label="Screen preview">
        <SeatMap rows={preview} unavailable={new Set()} picked={new Set()} preview label="Screen preview" />
        <p className="muted builder-total">
          {plural(rows.length, 'row')}, {plural(total, 'seat')}
        </p>
      </section>
    </div>
  );
}
