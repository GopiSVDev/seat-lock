// Browser storage can be missing or throw (private windows, blocked site data): never let it break a page.
export function readJson<T>(store: Storage | undefined, key: string): T | null {
  try {
    const raw = store?.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export function writeJson(store: Storage | undefined, key: string, value: unknown): void {
  try {
    if (value === null || value === undefined) store?.removeItem(key);
    else store?.setItem(key, JSON.stringify(value));
  } catch {
    // ignore: a convenience only
  }
}

export const local = typeof window === 'undefined' ? undefined : window.localStorage;
export const session = typeof window === 'undefined' ? undefined : window.sessionStorage;
