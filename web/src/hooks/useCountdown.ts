import { useEffect, useState } from 'react';

/** Seconds left until `target` (an instant or epoch ms), ticking every quarter second; 0 once passed. */
export function useCountdown(target: string | number | null | undefined): number {
  const end = target === null || target === undefined ? null : typeof target === 'number' ? target : Date.parse(target);
  const compute = () => (end === null ? 0 : Math.max(0, (end - Date.now()) / 1000));
  const [left, setLeft] = useState(compute);

  useEffect(() => {
    if (end === null) {
      setLeft(0);
      return;
    }
    const tick = () => setLeft(Math.max(0, (end - Date.now()) / 1000));
    tick();
    const timer = window.setInterval(tick, 250);
    return () => window.clearInterval(timer);
  }, [end]);

  return left;
}

/** A Retry-After wait: start(seconds) and read the whole seconds still to wait (0 = go ahead). */
export function useRetryAfter(): [number, (seconds: number | null) => void] {
  const [until, setUntil] = useState<number | null>(null);
  const left = useCountdown(until);
  const start = (seconds: number | null) => setUntil(seconds ? Date.now() + seconds * 1000 : null);
  return [Math.ceil(left), start];
}
