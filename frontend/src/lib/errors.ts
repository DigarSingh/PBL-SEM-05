import type { AxiosError } from 'axios';

interface ErrorBody {
  success?: boolean;
  message?: string;
  code?: string;
  errors?: { field: string; message: string }[];
  details?: Record<string, unknown>;
}

/** Normalised API failure with the backend's machine-readable code and field errors. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;
  readonly details: Record<string, unknown>;

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {},
              details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.details = details;
  }
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  const axiosError = error as AxiosError<ErrorBody>;
  if (axiosError?.isAxiosError) {
    if (!axiosError.response) {
      const offline = typeof navigator !== 'undefined' && !navigator.onLine;
      return new ApiError(0, offline ? 'OFFLINE' : 'NETWORK_ERROR',
        offline ? 'You appear to be offline. Check your connection and try again.'
          : 'Could not reach the server. Please try again.');
    }
    const body = axiosError.response.data ?? {};
    const fieldErrors: Record<string, string> = {};
    for (const violation of body.errors ?? []) {
      fieldErrors[violation.field] = violation.message;
    }
    return new ApiError(axiosError.response.status, body.code ?? 'ERROR',
      body.message ?? 'Something went wrong. Please try again.', fieldErrors, body.details ?? {});
  }
  return new ApiError(0, 'UNKNOWN', error instanceof Error ? error.message : 'Something went wrong');
}

export function errorMessage(error: unknown): string {
  return toApiError(error).message;
}
