import { apiPost, apiPostEnvelope } from '@/lib/api';
import type { AuthResponse } from '@/lib/types';

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  phone: string | null;
}

export interface RegisterResult {
  userId: number;
  email: string;
  emailVerificationRequired: boolean;
}

export const authApi = {
  login: (email: string, password: string) => apiPost<AuthResponse>('/auth/login', { email, password }),
  register: (payload: RegisterPayload) => apiPostEnvelope<RegisterResult>('/auth/register', payload),
  logout: () => apiPost<void>('/auth/logout'),
  verifyEmail: (token: string) => apiPostEnvelope<void>('/auth/verify-email', { token }),
  resendVerification: (email: string) => apiPostEnvelope<void>('/auth/resend-verification', { email }),
  forgotPassword: (email: string) => apiPostEnvelope<void>('/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) =>
    apiPostEnvelope<void>('/auth/reset-password', { token, newPassword }),
};
