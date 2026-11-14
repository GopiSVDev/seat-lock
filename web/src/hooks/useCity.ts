import { useCallback, useSyncExternalStore } from 'react';
import type { CityInfo } from '../api/types';
import { local, readJson, writeJson } from '../lib/storage';

const KEY = 'seatlock.city';
const listeners = new Set<() => void>();
let cached: CityInfo | null | undefined;

function read(): CityInfo | null {
  if (cached === undefined) cached = readJson<CityInfo>(local, KEY);
  return cached;
}

/** The city the visitor picked, remembered in this browser. */
export function useCity(): [CityInfo | null, (city: CityInfo | null) => void] {
  const city = useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    read,
    () => null,
  );
  const setCity = useCallback((next: CityInfo | null) => {
    cached = next ? { id: next.id, name: next.name } : null;
    writeJson(local, KEY, cached);
    listeners.forEach((l) => l());
  }, []);
  return [city, setCity];
}
