import { describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { ApiError } from './errors';
import { applyServerErrors, emailField, newPasswordField, numeric } from './forms';

describe('form rules mirror the backend', () => {
  it('requires a letter and a digit in new passwords', () => {
    expect(newPasswordField.safeParse('password').success).toBe(false);
    expect(newPasswordField.safeParse('12345678').success).toBe(false);
    expect(newPasswordField.safeParse('Secure2026').success).toBe(true);
  });

  it('trims and validates emails', () => {
    expect(emailField.parse('  asha@example.com ')).toBe('asha@example.com');
    expect(emailField.safeParse('not-an-email').success).toBe(false);
  });

  it('turns numeric text into numbers and blanks into undefined', () => {
    const schema = numeric(z.number().min(0).optional());
    expect(schema.parse('12.5')).toBe(12.5);
    expect(schema.parse('')).toBeUndefined();
    expect(schema.safeParse('abc').success).toBe(false);
  });
});

describe('applyServerErrors', () => {
  it('maps indexed backend paths onto form field names', () => {
    const setError = vi.fn();
    const applied = applyServerErrors(new ApiError(400, 'VALIDATION_FAILED', 'Invalid', { 'variants[1].sku': 'must be unique' }), setError);
    expect(applied).toBe(true);
    expect(setError).toHaveBeenCalledWith('variants.1.sku', { type: 'server', message: 'Must be unique' });
  });

  it('reports when nothing could be attached to a field', () => {
    expect(applyServerErrors(new ApiError(409, 'CONFLICT', 'Busy'), vi.fn())).toBe(false);
  });
});
