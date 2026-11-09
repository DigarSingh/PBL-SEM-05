import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import { toApiError } from './errors';
import { session } from './session';
import type { ApiEnvelope } from './types';

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

export const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
  timeout: 20_000,
  headers: { 'X-Requested-With': 'XMLHttpRequest' },
  // Repeat array parameters (brand=a&brand=b) as Spring expects.
  paramsSerializer: { indexes: null },
});

http.interceptors.request.use((config) => {
  const token = session.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status;
    const code = error.response?.data?.code;
    const isAuthCall = original?.url?.startsWith('/auth/');
    if (status === 401 && original && !original._retried && !isAuthCall && session.getToken() && code === 'INVALID_TOKEN') {
      original._retried = true;
      if (await session.refresh()) {
        original.headers.Authorization = `Bearer ${session.getToken()}`;
        return http(original);
      }
    }
    throw toApiError(error);
  },
);

export async function apiGet<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.get<ApiEnvelope<T>>(url, { ...config, params: cleanParams(params) });
  return response.data.data;
}

export async function apiPost<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.post<ApiEnvelope<T>>(url, body ?? {}, config);
  return response.data.data;
}

export async function apiPut<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.put<ApiEnvelope<T>>(url, body ?? {}, config);
  return response.data.data;
}

export async function apiPatch<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.patch<ApiEnvelope<T>>(url, body ?? {}, config);
  return response.data.data;
}

export async function apiDelete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.delete<ApiEnvelope<T>>(url, config);
  return response.data.data;
}

/** Posts and returns the whole envelope (when the server message matters to the UI). */
export async function apiPostEnvelope<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<ApiEnvelope<T>> {
  const response = await http.post<ApiEnvelope<T>>(url, body ?? {}, config);
  return response.data;
}

function cleanParams(params?: Record<string, unknown>): Record<string, unknown> | undefined {
  if (!params) {
    return undefined;
  }
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''
      && !(Array.isArray(value) && value.length === 0)),
  );
}
