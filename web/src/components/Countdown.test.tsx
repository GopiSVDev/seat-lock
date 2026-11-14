import { act, render, renderHook, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { useCountdown, useRetryAfter } from '../hooks/useCountdown';
import { Countdown } from './Countdown';

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2030-01-01T10:00:00Z'));
});
afterEach(() => vi.useRealTimers());

describe('countdowns', () => {
  it('counts down to the payment deadline, turns urgent in the last minute and calls back once', () => {
    const onEnd = vi.fn();
    render(<Countdown endsAt="2030-01-01T10:10:00Z" onEnd={onEnd} />);
    expect(screen.getByRole('timer')).toHaveTextContent('10:00');
    expect(screen.getByRole('timer')).toHaveAccessibleName(/10:00 left to pay, until 3:40 pm/);

    act(() => vi.advanceTimersByTime(9 * 60_000 + 1_000));
    expect(screen.getByRole('timer')).toHaveTextContent('0:59');
    expect(document.querySelector('.countdown')).toHaveAttribute('data-urgent');
    expect(onEnd).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(60_000));
    expect(screen.getByRole('timer')).toHaveTextContent('0:00');
    expect(onEnd).toHaveBeenCalledTimes(1);
    act(() => vi.advanceTimersByTime(5_000));
    expect(onEnd).toHaveBeenCalledTimes(1);
  });

  it('waits out a Retry-After', () => {
    const { result } = renderHook(() => useRetryAfter());
    act(() => result.current[1](6));
    expect(result.current[0]).toBe(6);
    act(() => vi.advanceTimersByTime(6_000));
    expect(result.current[0]).toBe(0);
  });

  it('is zero without a deadline', () => {
    const { result } = renderHook(() => useCountdown(null));
    expect(result.current).toBe(0);
  });
});
