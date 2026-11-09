import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { ApiError, toApiError } from './errors';

function axiosError(status: number, data: unknown): AxiosError {
  const headers = new AxiosHeaders();
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', { headers }, null, {
    status, statusText: '', headers, config: { headers }, data,
  });
}

describe('toApiError', () => {
  it('keeps the backend code, message and field errors', () => {
    const error = toApiError(axiosError(400, {
      success: false, code: 'VALIDATION_FAILED', message: 'Invalid request',
      errors: [{ field: 'variants[0].sku', message: 'must not be blank' }],
    }));
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.fieldErrors).toEqual({ 'variants[0].sku': 'must not be blank' });
  });

  it('reports network failures without a response', () => {
    const error = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'));
    expect(error.status).toBe(0);
    expect(['NETWORK_ERROR', 'OFFLINE']).toContain(error.code);
  });
});
