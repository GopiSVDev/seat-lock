import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import type { LayoutRow } from '../api/types';
import { blockFrom, selectSeats } from '../lib/seats';
import { nextSeat, SeatMap } from './SeatMap';

const rows: LayoutRow[] = [
  { label: 'A', seatType: 'REGULAR', price: 15000, seats: [1, 2, 3, 4, 5, 6].map((n) => ({ id: n, number: n })) },
  { label: 'B', seatType: 'RECLINER', price: 40000, seats: [7, 8].map((n, i) => ({ id: n, number: i + 1 })) },
];

function Picker({ taken = [], quantity = 2 }: { taken?: number[]; quantity?: number }) {
  const [picked, setPicked] = useState<number[]>([]);
  const unavailable = new Set(taken);
  return <SeatMap rows={rows} unavailable={unavailable} picked={new Set(picked)} onSeat={(id) => setPicked((p) => selectSeats(rows, unavailable, p, id, quantity))} />;
}

const seat = (name: RegExp) => screen.getByRole('button', { name });

describe('seat selection', () => {
  it('selects as many seats side by side as asked for, with one tap', async () => {
    render(<Picker quantity={3} />);
    await userEvent.click(seat(/^A2, Regular ₹150, available/));
    for (const label of ['A2', 'A3', 'A4']) expect(seat(new RegExp(`^${label}, .*selected`))).toHaveAttribute('aria-pressed', 'true');
    expect(seat(/^A1, .*available/)).toHaveAttribute('aria-pressed', 'false');
  });

  it('a tap elsewhere moves the whole block; a tap on a selected seat unselects just that one', async () => {
    render(<Picker />);
    await userEvent.click(seat(/^A1/));
    await userEvent.click(seat(/^A5/));
    expect(screen.getAllByRole('button', { pressed: true }).map((b) => b.textContent)).toEqual(['5', '6']);
    await userEvent.click(seat(/^A6/));
    expect(screen.getAllByRole('button', { pressed: true }).map((b) => b.textContent)).toEqual(['5']);
    await userEvent.click(seat(/^A2/));
    expect(screen.getAllByRole('button', { pressed: true }).map((b) => b.textContent)).toEqual(['2', '5']);
  });

  it("can't select a sold seat, and says so without colour", async () => {
    render(<Picker taken={[3]} />);
    const a3 = seat(/^A3, .*sold/);
    expect(a3).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(a3);
    expect(seat(/^A3, .*sold/)).toHaveAttribute('aria-pressed', 'false');
  });

  it('moves with the arrow keys and selects with Enter', async () => {
    render(<Picker quantity={1} />);
    const user = userEvent.setup();
    // back rows are drawn first (the screen is at the bottom), so B1 is the first seat in tab order
    const b1 = seat(/^B1/);
    expect(b1).toHaveAttribute('tabindex', '0');
    await user.tab();
    await user.tab();
    await user.tab();
    expect(b1).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(seat(/^A3/)).toHaveFocus();
    await user.keyboard('{ArrowRight}{Enter}');
    expect(seat(/^A4, .*selected/)).toHaveFocus();
  });
});

describe('blocks of seats', () => {
  const free = () => false;
  it('extends to the right first, then slides left at the end of a row', () => {
    expect(blockFrom(rows, free, 2, 3)).toEqual([2, 3, 4]);
    expect(blockFrom(rows, free, 6, 3)).toEqual([4, 5, 6]);
  });

  it('stays inside the gap between sold seats', () => {
    const sold = (id: number) => id === 1 || id === 4;
    expect(blockFrom(rows, sold, 2, 4)).toEqual([2, 3]);
    expect(blockFrom(rows, sold, 5, 2)).toEqual([5, 6]);
  });

  it('tops up a partial selection instead of replacing it', () => {
    expect(selectSeats(rows, new Set([4]), [2, 3], 5, 4)).toEqual([2, 3, 5, 6]);
    expect(selectSeats(rows, new Set(), [1, 2], 5, 2)).toEqual([5, 6]);
    expect(selectSeats(rows, new Set([5]), [1], 5, 2)).toEqual([1]);
  });

  it('goes up and down to the seat nearest by position in a centred row', () => {
    expect(nextSeat(rows, 0, 0, 'ArrowDown')).toEqual([1, 0]);
    expect(nextSeat(rows, 0, 5, 'ArrowDown')).toEqual([1, 1]);
    expect(nextSeat(rows, 1, 0, 'ArrowUp')).toEqual([0, 2]);
    expect(nextSeat(rows, 0, 0, 'ArrowLeft')).toBeNull();
    expect(nextSeat(rows, 0, 1, 'End')).toEqual([0, 5]);
  });
});
