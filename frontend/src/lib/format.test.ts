import { describe, expect, it } from 'vitest';
import { formatDay, formatPercent, formatPrice, initials } from './format';

describe('formatPrice', () => {
  it('formats rupees with Indian digit grouping', () => {
    expect(formatPrice(123456)).toBe('₹1,23,456');
    expect(formatPrice(1999.5)).toBe('₹1,999.5');
  });

  it('shows a dash for missing amounts', () => {
    expect(formatPrice(null)).toBe('-');
  });
});

describe('formatDay', () => {
  it('never shifts a calendar day across time zones', () => {
    expect(formatDay('2026-09-01')).toMatch(/^1 Sep/);
  });
});

describe('formatPercent and initials', () => {
  it('formats percentages', () => {
    expect(formatPercent(18.64, 1)).toBe('18.6%');
    expect(formatPercent(null)).toBe('-');
  });

  it('builds avatar initials', () => {
    expect(initials('Asha Rao')).toBe('AR');
    expect(initials('  meera  ')).toBe('M');
    expect(initials('')).toBe('?');
  });
});
