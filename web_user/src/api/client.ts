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
        return Promise.reject(new Error(formatApiError(response.config, code, undefined, message)));
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

export function getErrorMessage(error: AxiosError | Error) {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ERR_NETWORK') {
      return formatApiError(error.config, undefined, undefined, '网络错误，后端无法访问');
    }
    const responseData = error.response?.data;
    const responseMessage = getResponseMessage(responseData) || error.message;
    return formatApiError(error.config, error.response?.status, error.response?.statusText, responseMessage);
  }
  return error.message || 'Request failed, please try again later';
}

function getResponseMessage(responseData: unknown) {
  if (isApiEnvelope(responseData)) return responseData.message || '';
  if (responseData && typeof responseData === 'object' && 'message' in responseData) {
    return String((responseData as { message?: unknown }).message || '');
  }
  if (typeof responseData === 'string') return responseData;
  return '';
}

function formatApiError(config: { method?: string; url?: string } | undefined, status?: number, statusText?: string, message?: string) {
  const endpoint = describeEndpoint(config);
  const statusPart = status ? `（${describeStatus(status, statusText)}）` : '';
  const messagePart = message ? `：${message}` : '';
  return `接口请求失败：${endpoint}${statusPart}${messagePart}`;
}

function describeStatus(status: number, statusText?: string) {
  const hint: Record<number, string> = {
    400: '请求参数错误',
    401: '未登录或权限不足',
    403: '无权访问',
    404: '接口不存在',
    405: '请求方法不支持',
    500: '后端内部错误',
    501: '后端未实现',
  };
  return [`HTTP ${status}`, statusText, hint[status]].filter(Boolean).join('，');
}

function describeEndpoint(config?: { method?: string; url?: string }) {
  const method = (config?.method || 'GET').toUpperCase();
  let url = config?.url || '';
  if (url.startsWith('/') && !url.startsWith(`${apiPrefix}/`) && url !== apiPrefix) {
    url = `${apiPrefix}${url}`;
  }
  return `${method} ${url || 'unknown endpoint'}`;
}
