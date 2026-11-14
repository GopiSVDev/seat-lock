import { dateParts } from '../lib/format';
import { indiaToday } from '../lib/time';

/** The next days that have shows (India dates). */
export function DateStrip({ dates, selected, onSelect }: { dates: string[]; selected: string; onSelect: (date: string) => void }) {
  const today = indiaToday();
  const all = dates.includes(selected) ? dates : [...dates, selected].sort();
  return (
    <div className="dates" role="group" aria-label="Date">
      {all.map((date) => {
        const { weekday, day, month } = dateParts(date);
        return (
          <button
            type="button"
            key={date}
            className="date"
            aria-current={date === selected ? 'date' : undefined}
            aria-label={`${date === today ? 'Today, ' : ''}${weekday} ${day} ${month}`}
            onClick={() => onSelect(date)}
          >
            <span>{date === today ? 'Today' : weekday}</span>
            <strong className="num">{day}</strong>
            <span>{month}</span>
          </button>
        );
      })}
    </div>
  );
}
