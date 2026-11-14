import { dateParts, formatClock, formatDate, formatRupees, formatTime, plural } from './format';
import { indiaDate, indiaLocalToInstant, indiaToday, secondsUntil } from './time';

describe('money', () => {
  it('shows paise as rupees', () => {
    expect(formatRupees(15000)).toBe('₹150');
    expect(formatRupees(12345)).toBe('₹123.45');
    expect(formatRupees(25000000)).toBe('₹2,50,000');
  });
});

describe('India time, whatever the machine zone (tests run in UTC)', () => {
  it('shows a UTC instant as the time in India', () => {
    expect(formatTime('2030-01-01T13:30:00Z')).toBe('7:00 pm');
    expect(formatTime('2030-01-01T04:00:00Z')).toBe('9:30 am');
  });

  it('puts 23:59 IST on its own day and 00:01 IST on the next', () => {
    expect(indiaDate('2026-12-02T18:29:00Z')).toBe('2026-12-02');
    expect(indiaDate('2026-12-02T18:31:00Z')).toBe('2026-12-03');
    expect(indiaToday(new Date('2026-12-02T18:31:00Z'))).toBe('2026-12-03');
  });

  it('reads an admin-typed date and time as India time', () => {
    expect(indiaLocalToInstant('2030-01-01', '19:00')).toBe('2030-01-01T13:30:00.000Z');
  });

  it('splits a date for the date strip', () => {
    expect(dateParts('2030-01-01')).toEqual({ weekday: 'Tue', day: '1', month: 'Jan' });
    expect(formatDate('2026-10-24')).toBe('24 Oct 2026');
  });
});

describe('countdown text', () => {
  it('rounds up to whole seconds and never goes negative', () => {
    expect(formatClock(300)).toBe('5:00');
    expect(formatClock(59.2)).toBe('1:00');
    expect(formatClock(9)).toBe('0:09');
    expect(formatClock(-4)).toBe('0:00');
  });

  it('counts seconds to an instant', () => {
    expect(secondsUntil('2030-01-01T00:00:10Z', Date.parse('2030-01-01T00:00:00Z'))).toBe(10);
  });

  it('pluralises', () => {
    expect(plural(1, 'seat')).toBe('1 seat');
    expect(plural(3, 'seat')).toBe('3 seats');
  });
});
