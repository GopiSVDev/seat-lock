import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type KeyboardEvent, type ReactNode } from 'react';
import type { LayoutRow, SeatType } from '../api/types';
import { formatRupees, seatTypeName } from '../lib/format';
import { MinusIcon, PlusIcon } from './Icons';
import '../styles/show.css';

export type SeatState = 'free' | 'picked' | 'taken' | 'lost';

export type SeatMapProps = {
  rows: LayoutRow[];
  /** booked or held by someone */
  unavailable: ReadonlySet<number>;
  picked: ReadonlySet<number>;
  /** booked by someone else a moment ago, after a failed attempt */
  lost?: ReadonlySet<number>;
  onSeat?: (seatId: number) => void;
  /** offered per section: book the best block of the chosen size */
  onBest?: (seatType: SeatType) => void;
  bestBusy?: boolean;
  /** a drawing only (admin preview) */
  preview?: boolean;
  label?: string;
  /** shown in the toolbar next to the zoom buttons */
  status?: ReactNode;
};

const STATE_TEXT: Record<SeatState, string> = {
  free: 'available',
  picked: 'selected',
  taken: 'sold',
  lost: 'just sold',
};
const MIN = 16;
const MAX = 28;

export function seatState(id: number, p: Pick<SeatMapProps, 'unavailable' | 'picked' | 'lost'>): SeatState {
  if (p.lost?.has(id)) return 'lost';
  if (p.unavailable.has(id)) return 'taken';
  if (p.picked.has(id)) return 'picked';
  return 'free';
}

/** Where an arrow key moves focus: along the row, or to the seat nearest by position in the next row. */
export function nextSeat(rows: LayoutRow[], row: number, index: number, key: string): [number, number] | null {
  const len = rows[row].seats.length;
  switch (key) {
    case 'ArrowLeft':
      return index > 0 ? [row, index - 1] : null;
    case 'ArrowRight':
      return index < len - 1 ? [row, index + 1] : null;
    case 'Home':
      return [row, 0];
    case 'End':
      return [row, len - 1];
    case 'ArrowUp':
    case 'ArrowDown': {
      const target = row + (key === 'ArrowUp' ? -1 : 1);
      if (target < 0 || target >= rows.length) return null;
      // rows are centred, so match by distance from the centre
      const offset = index - (len - 1) / 2;
      const targetLen = rows[target].seats.length;
      const i = Math.round((targetLen - 1) / 2 + offset);
      return [target, Math.min(targetLen - 1, Math.max(0, i))];
    }
    default:
      return null;
  }
}

const widthOf = (type: SeatType) => (type === 'RECLINER' ? 1.5 : 1);

/** The hall as the audience sees it: back rows on top, the screen at the bottom. */
export function SeatMap({ rows: frontFirst, unavailable, picked, lost, onSeat, onBest, bestBusy, preview = false, label = 'Seats', status }: SeatMapProps) {
  const rows = useMemo(() => [...frontFirst].reverse(), [frontFirst]);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState(28);
  const [focusPos, setFocusPos] = useState<[number, number]>([0, 0]);
  const widest = Math.max(1, ...rows.map((r) => r.seats.length * widthOf(r.seatType)));

  useLayoutEffect(() => {
    const width = (scrollRef.current?.clientWidth || 800) - (window.innerWidth < 600 ? 60 : 100);
    // seat + gap (a fifth of a seat) per column
    setSize(Math.max(MIN, Math.min(MAX, Math.floor(width / (widest * 1.2)))));
  }, [widest]);

  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (el && el.scrollWidth > el.clientWidth) el.scrollLeft = (el.scrollWidth - el.clientWidth) / 2;
  }, [size, widest]);

  useEffect(() => {
    if (focusPos[0] >= rows.length) setFocusPos([0, 0]);
  }, [rows.length, focusPos]);

  const sections = useMemo(() => {
    const out: { key: string; type: SeatType; price: number; rows: number[] }[] = [];
    rows.forEach((row, i) => {
      const last = out[out.length - 1];
      if (last && last.type === row.seatType && last.price === row.price) last.rows.push(i);
      else out.push({ key: `${i}-${row.seatType}`, type: row.seatType, price: row.price, rows: [i] });
    });
    return out;
  }, [rows]);

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>, row: number, index: number) => {
    const next = nextSeat(rows, row, index, event.key);
    if (!next) return;
    event.preventDefault();
    setFocusPos(next);
    scrollRef.current?.querySelector<HTMLButtonElement>(`[data-seat="${rows[next[0]].seats[next[1]].id}"]`)?.focus();
  };

  const style = { '--s': `${size}px`, '--g': `${Math.max(3, Math.round(size * 0.2))}px` } as CSSProperties;

  return (
    <div className={`seatmap${preview ? ' seatmap-preview' : ''}`}>
      {!preview && (
        <div className="seatmap-bar">
          <SeatLegend />
          <div className="seatmap-bar-end">
            {status}
            <div className="seatmap-zoom" role="group" aria-label="Zoom">
              <button type="button" onClick={() => setSize((s) => Math.max(MIN, s - 4))} disabled={size <= MIN} aria-label="Zoom out">
                <MinusIcon />
              </button>
              <button type="button" onClick={() => setSize((s) => Math.min(44, s + 4))} disabled={size >= 44} aria-label="Zoom in">
                <PlusIcon />
              </button>
            </div>
          </div>
        </div>
      )}
      <div className="seatmap-scroll" ref={scrollRef} style={style} data-numbers={size >= 22 || undefined}>
        <div className="seatmap-inner" role="group" aria-label={label}>
          {sections.map((section) => (
            <div className="section-block" key={section.key}>
              <div className="section-head">
                <span>
                  {!preview && <strong className="num">{formatRupees(section.price)}</strong>}
                  {seatTypeName(section.type)}
                </span>
                {onBest && (
                  <button type="button" className="section-best" onClick={() => onBest(section.type)} disabled={bestBusy}>
                    Best available
                  </button>
                )}
              </div>
              {section.rows.map((r) => {
                const row = rows[r];
                return (
                  <div className="srow" key={`${row.label}-${r}`} role="group" aria-label={`Row ${row.label}`}>
                    <span className="srow-label" aria-hidden="true">
                      {row.label}
                    </span>
                    <div className="srow-seats">
                      {row.seats.map((seat, i) => {
                        if (preview) return <span key={seat.id} className="seat" data-type={row.seatType} aria-hidden="true" />;
                        const state = seatState(seat.id, { unavailable, picked, lost });
                        const blocked = state === 'taken' || state === 'lost';
                        return (
                          <button
                            type="button"
                            key={seat.id}
                            data-seat={seat.id}
                            className="seat"
                            data-state={state}
                            data-type={row.seatType}
                            tabIndex={focusPos[0] === r && focusPos[1] === i ? 0 : -1}
                            aria-label={`${row.label}${seat.number}, ${seatTypeName(row.seatType)} ${formatRupees(row.price)}, ${STATE_TEXT[state]}`}
                            aria-pressed={state === 'picked'}
                            aria-disabled={blocked || undefined}
                            onFocus={() => setFocusPos([r, i])}
                            onKeyDown={(e) => onKeyDown(e, r, i)}
                            onClick={() => !blocked && onSeat?.(seat.id)}
                          >
                            {state === 'lost' ? '!' : <span className="seat-no">{seat.number}</span>}
                          </button>
                        );
                      })}
                    </div>
                    <span className="srow-label" aria-hidden="true" />
                  </div>
                );
              })}
            </div>
          ))}
          <div className="screen" aria-hidden="true">
            <svg viewBox="0 0 300 16" preserveAspectRatio="none">
              <path d="M2 14 Q150 -6 298 14" />
            </svg>
            <span>Screen</span>
          </div>
        </div>
      </div>
    </div>
  );
}

export function SeatLegend() {
  const items: [SeatState, string][] = [
    ['free', 'Available'],
    ['picked', 'Selected'],
    ['taken', 'Sold'],
  ];
  return (
    <ul className="legend" aria-label="Seat key">
      {items.map(([state, text]) => (
        <li key={state}>
          <span className="seat seat-key" data-state={state} aria-hidden="true" />
          {text}
        </li>
      ))}
    </ul>
  );
}
