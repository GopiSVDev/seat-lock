import { useEffect, useRef } from 'react';
import { ChevronDown } from './Icons';

/** A dropdown of checkboxes; closes on outside click or Escape. */
export function FilterMenu<T extends string>({
  label,
  options,
  selected,
  onChange,
  name,
}: {
  label: string;
  options: T[];
  selected: T[];
  onChange: (next: T[]) => void;
  name: (value: T) => string;
}) {
  const ref = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    const close = (e: Event) => {
      const el = ref.current;
      if (!el?.open) return;
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !el.contains(e.target as Node)) {
        el.open = false;
        if (e instanceof KeyboardEvent) el.querySelector('summary')?.focus();
      }
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', close);
    };
  }, []);

  if (options.length === 0) return null;
  return (
    <details className="filter" ref={ref} data-active={selected.length > 0 || undefined}>
      <summary aria-label={selected.length ? `${label}, ${selected.length} selected` : label}>
        {label}
        {selected.length > 0 ? <span className="filter-count">{selected.length}</span> : <ChevronDown size={14} />}
      </summary>
      <div className="filter-panel" role="group" aria-label={label}>
        {options.map((option) => {
          const on = selected.includes(option);
          return (
            <label key={option} className="filter-option">
              <input type="checkbox" checked={on} onChange={() => onChange(on ? selected.filter((s) => s !== option) : [...selected, option])} />
              {name(option)}
            </label>
          );
        })}
      </div>
    </details>
  );
}
