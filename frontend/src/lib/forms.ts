import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { z } from 'zod';
import { toApiError } from './errors';

// Mirrors the backend's Bean Validation rules so users see problems before submitting.
export const PHONE_PATTERN = /^\+?[0-9][0-9 -]{6,18}$/;
export const PIN_CODE_PATTERN = /^[1-9][0-9]{5}$/;
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).+$/;

export const emailField = z.string().trim()
  .min(1, 'Enter your email address')
  .max(255, 'Email address is too long')
  .pipe(z.email('Enter a valid email address'));

export const newPasswordField = z.string()
  .min(8, 'Use at least 8 characters')
  .max(72, 'Use at most 72 characters')
  .regex(PASSWORD_PATTERN, 'Include at least one letter and one number');

export const optionalPhoneField = z.string().trim()
  .max(20, 'Phone number is too long')
  .refine((value) => value === '' || PHONE_PATTERN.test(value), 'Enter a valid phone number');

function toNumber(value: unknown): unknown {
  if (value === '' || value === null || value === undefined) {
    return undefined;
  }
  if (typeof value === 'number') {
    return Number.isNaN(value) ? undefined : value;
  }
  const parsed = Number(String(value).trim());
  return Number.isNaN(parsed) ? value : parsed;
}

/** Number schema for text inputs: "12.5" becomes 12.5 and an empty field becomes undefined. */
export function numeric<T extends z.ZodType>(schema: T) {
  return z.preprocess(toNumber, schema);
}

export function emptyToNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

/**
 * Copies the backend's field validation errors onto form fields (`variants[0].sku` becomes
 * `variants.0.sku`). Returns false when no error could be attached to a field.
 */
export function applyServerErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>,
                                                         fields?: readonly string[]): boolean {
  const apiError = toApiError(error);
  let applied = false;
  for (const [rawField, message] of Object.entries(apiError.fieldErrors)) {
    const field = rawField.replace(/\[(\d+)\]/g, '.$1');
    if (fields && !fields.includes(field)) {
      continue;
    }
    setError(field as Path<T>, { type: 'server', message: message.charAt(0).toUpperCase() + message.slice(1) });
    applied = true;
  }
  return applied;
}
