import axios from "axios";

type ApiWrapper<T = unknown> = {
  code: number;
  message?: string;
  data: T;
  timestamp?: string;
};

export const ADMIN_AUTH_KEY = "resource-forum-admin-auth";
export const LEGACY_ADMIN_SESSION_KEY = "rsf_admin_session";
export const ADMIN_AUTH_EXPIRED_EVENT = "resource-forum-admin-auth-expired";

const apiOrigin = normalizeApiOrigin(import.meta.env.VITE_API_BASE_URL);
const apiPrefix = normalizeApiPrefix(import.meta.env.VITE_API_PREFIX || "/api");

export const apiClient = axios.create({
  baseURL: apiOrigin ? joinApiBase(apiOrigin, apiPrefix) : apiPrefix,
  timeout: 10000,
});

export function normalizeApiPayload<T>(payload: T): unknown {
  const data = isApiWrapper(payload) ? payload.data : payload;
  return normalizePagedResult(data);
}

export function clearStoredAdminAuth() {
  try {
    localStorage.removeItem(ADMIN_AUTH_KEY);
    localStorage.removeItem(LEGACY_ADMIN_SESSION_KEY);
  } catch {
    // Ignore storage failures; the response still reports the auth error.
  }
}

apiClient.interceptors.request.use((config) => {
  try {
    const persisted = localStorage.getItem(ADMIN_AUTH_KEY);
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
  (response) => {
    response.data = normalizeApiPayload(response.data);
    return response;
  },
  (error) => {
    if (error.response?.status === 401 && !isLoginRequest(error.config?.url)) {
      clearStoredAdminAuth();
      notifyAdminAuthExpired();
      return Promise.reject(new Error("登录状态已过期，请重新登录"));
    }
    const message =
      error.response?.data?.message || error.message || "请求失败，请稍后重试";
    return Promise.reject(new Error(message));
  },
);

function normalizeApiOrigin(value?: string) {
  return value?.trim().replace(/\/+$/, "") || "";
}

function normalizeApiPrefix(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return "/api";
  return `/${trimmed.replace(/^\/+|\/+$/g, "")}`;
}

function joinApiBase(origin: string, prefix: string) {
  if (/\/api(?:\/v1)?$/.test(origin)) return origin;
  return `${origin}${prefix}`;
}

function isLoginRequest(url?: string) {
  return Boolean(url && /\/auth\/login$/.test(url));
}

function notifyAdminAuthExpired() {
  try {
    window.dispatchEvent(new Event(ADMIN_AUTH_EXPIRED_EVENT));
  } catch {
    // Running in tests or non-browser contexts can skip the event.
  }
}

function isApiWrapper(value: unknown): value is ApiWrapper {
  return Boolean(
    value &&
    typeof value === "object" &&
    "code" in value &&
    "data" in value &&
    typeof (value as ApiWrapper).code === "number",
  );
}

function normalizePagedResult(value: unknown) {
  if (!value || typeof value !== "object") return value;
  const record = value as Record<string, unknown>;
  if (Array.isArray(record.list) && !Array.isArray(record.items)) {
    return {
      ...record,
      items: record.list,
      pageSize: record.size,
    };
  }
  return value;
}
