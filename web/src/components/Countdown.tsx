import { useEffect, useRef, useState } from 'react';
import { useCountdown } from '../hooks/useCountdown';
import { formatClock, formatTime } from '../lib/format';
import { ClockIcon } from './Icons';

/** Time left to pay; red in the last minute. Announces whole minutes to screen readers. */
export function Countdown({ endsAt, onEnd }: { endsAt: string; onEnd?: () => void }) {
  const left = useCountdown(endsAt);
  const ended = useRef(false);
  const announcement = useAnnouncement(left);

  useEffect(() => {
    if (left <= 0 && !ended.current) {
      ended.current = true;
      onEnd?.();
    }
    if (left > 0) ended.current = false;
  }, [left, onEnd]);

  return (
    <div className="countdown" data-urgent={(left > 0 && left <= 60) || undefined}>
      <ClockIcon size={16} />
      <span role="timer" aria-label={`${formatClock(left)} left to pay, until ${formatTime(endsAt)}`}>
        <strong className="num">{formatClock(left)}</strong> left to complete payment
      </span>
      <span className="visually-hidden" aria-live="polite">
        {announcement}
      </span>
    </div>
  );
}

function useAnnouncement(left: number): string {
  const [text, setText] = useState('');
  const mark = Math.ceil(left);
  useEffect(() => {
    if (mark > 0 && (mark % 60 === 0 || mark === 30)) setText(mark >= 60 ? `${mark / 60} minute${mark === 60 ? '' : 's'} left` : '30 seconds left');
  }, [mark]);
  return text;
}
