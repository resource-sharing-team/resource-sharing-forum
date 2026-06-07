import axios, { type AxiosError, type AxiosResponse } from 'axios';

type ApiEnvelope<T = unknown> = {
  code?: number;
  message?: string;
  data?: T;
  timestamp?: string;
};

const rawBaseUrl = trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || '');
const configuredPrefix = normalizePrefix(import.meta.env.VITE_API_PREFIX || '/api');
const embeddedPrefix = rawBaseUrl.match(/\/api(?:\/v1)?$/)?.[0];

export const apiPrefix = embeddedPrefix || configuredPrefix;
export const apiBaseURL = embeddedPrefix ? rawBaseUrl : `${rawBaseUrl}${apiPrefix}`;
export const apiHostURL = embeddedPrefix ? rawBaseUrl.slice(0, -embeddedPrefix.length) || window.location.origin : rawBaseUrl || window.location.origin;
export const usesV1Api = /\/api\/v1$/.test(apiPrefix);
export const profileBasePath = usesV1Api ? '/user/profile' : '/me';

export const apiClient = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000,
});

apiClient.interceptors.request.use((config) => {
  try {
    const persisted = localStorage.getItem('resource-forum-auth');
    const token = persisted ? JSON.parse(persisted).state?.token : null;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  } catch {
    // Ignore malformed local storage and continue unauthenticated.
  }
  return config;
});

apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    if (isApiEnvelope(response.data)) {
      const { code, message, data } = response.data;
      if (typeof code === 'number' && code >= 400) {
        return Promise.reject(new Error(message || 'Request failed'));
      }
      response.data = data;
    }
    return response;
  },
  (error) => Promise.reject(new Error(getErrorMessage(error))),
);

function normalizePrefix(prefix: string) {
  const normalized = prefix.trim();
  if (!normalized) return '/api';
  return `/${normalized.replace(/^\/+|\/+$/g, '')}`;
}

function trimTrailingSlash(value: string) {
  return value.replace(/\/+$/g, '');
}

function isApiEnvelope(value: unknown): value is ApiEnvelope {
  return Boolean(
    value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      'code' in value &&
      'message' in value &&
      'data' in value,
  );
}

function getErrorMessage(error: AxiosError | Error) {
  if (axios.isAxiosError(error)) {
    const endpoint = describeEndpoint(error);
    if (error.code === 'ERR_NETWORK') {
      return `后端没有实现或无法访问这个接口：${endpoint}`;
    }
    if (error.response?.status && [404, 405, 501].includes(error.response.status)) {
      return `后端没有实现这个接口：${endpoint}`;
    }
    const responseData = error.response?.data;
    if (isApiEnvelope(responseData)) return responseData.message || 'Request failed';
    if (responseData && typeof responseData === 'object' && 'message' in responseData) {
      return String((responseData as { message?: unknown }).message || 'Request failed');
    }
  }
  return error.message || 'Request failed, please try again later';
}

function describeEndpoint(error: AxiosError) {
  const method = (error.config?.method || 'GET').toUpperCase();
  const url = error.config?.url || '';
  return `${method} ${url}`;
}
