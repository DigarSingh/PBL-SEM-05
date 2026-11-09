import { z } from 'zod';
import { emailField, newPasswordField, optionalPhoneField } from '@/lib/forms';

export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, 'Enter your password').max(72, 'Password is too long'),
});
export type LoginValues = z.output<typeof loginSchema>;

export const registerSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter your full name').max(120, 'Name is too long'),
  email: emailField,
  phone: optionalPhoneField,
  password: newPasswordField,
  confirmPassword: z.string().min(1, 'Confirm your password'),
}).refine((values) => values.password === values.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Passwords do not match',
});
export type RegisterValues = z.output<typeof registerSchema>;

export const emailOnlySchema = z.object({ email: emailField });
export type EmailOnlyValues = z.output<typeof emailOnlySchema>;

export const resetPasswordSchema = z.object({
  password: newPasswordField,
  confirmPassword: z.string().min(1, 'Confirm your new password'),
}).refine((values) => values.password === values.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Passwords do not match',
});
export type ResetPasswordValues = z.output<typeof resetPasswordSchema>;
