// API client — centralizes all backend calls
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const getToken = () => localStorage.getItem('hf_token');

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });

  if (res.status === 401) {
    localStorage.removeItem('hf_token');
    localStorage.removeItem('hf_user');
    window.location.href = '/login';
    throw new Error('Session expired');
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({ message: res.statusText }));
    throw new ApiError(res.status, body.message || body.error || 'Request failed', body.fieldErrors);
  }

  const text = await res.text();
  return text ? JSON.parse(text) : (undefined as unknown as T);
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fieldErrors?: Record<string, string>
  ) {
    super(message);
  }
}

// ─── Auth ────────────────────────────────────────────────────────────────────
export interface AuthResponse {
  token: string;
  email: string;
  fullName: string;
  userId: number;
  tokenType: string;
}

export const auth = {
  register: (email: string, password: string, fullName?: string) =>
    request<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, fullName }),
    }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
};

// ─── Integrations ─────────────────────────────────────────────────────────────
export interface Integration {
  id: number;
  name: string;
  destinationUrl: string;
  eventTypes: string;
  enabled: boolean;
  description: string;
  createdAt: string;
  updatedAt: string;
  hasSigningSecret: boolean;
}

export const integrations = {
  list: () => request<Integration[]>('/api/v1/integrations'),
  get: (id: number) => request<Integration>(`/api/v1/integrations/${id}`),
  create: (data: Partial<Integration>) =>
    request<Integration>('/api/v1/integrations', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: Partial<Integration>) =>
    request<Integration>(`/api/v1/integrations/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  toggle: (id: number, enabled: boolean) =>
    request<void>(`/api/v1/integrations/${id}/toggle?enabled=${enabled}`, { method: 'PATCH' }),
  rotateSecret: (id: number) =>
    request<{ signingSecret: string; message: string }>(`/api/v1/integrations/${id}/rotate-secret`, { method: 'POST' }),
  delete: (id: number) =>
    request<void>(`/api/v1/integrations/${id}`, { method: 'DELETE' }),
};

// ─── Events ───────────────────────────────────────────────────────────────────
export interface DeliveryAttempt {
  id: number;
  attemptNumber: number;
  httpStatusCode: number;
  durationMs: number;
  success: boolean;
  responseBody: string;
  errorMessage: string;
  timestamp: string;
}

export interface Event {
  id: number;
  eventType: string;
  status: string;
  payload: string;
  retryCount: number;
  maxAttempts: number;
  createdAt: string;
  updatedAt: string;
  nextRetryAt: string | null;
  lastAttemptAt: string | null;
  errorMessage: string | null;
  integrationId: number | null;
  integrationName: string | null;
  deliveryLogs?: DeliveryAttempt[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface DashboardStats {
  totalEvents: number;
  successfulDeliveries: number;
  failedDeliveries: number;
  pendingEvents: number;
  retryingEvents: number;
  successRate: number;
  avgDeliveryDurationMs: number | null;
}

export const events = {
  submit: (data: { integrationId?: number; eventType: string; payload: string; idempotencyKey?: string }) =>
    request<Event>('/api/v1/events', { method: 'POST', body: JSON.stringify(data) }),
  list: (params: { status?: string; integrationId?: number; page?: number; size?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.status) q.set('status', params.status);
    if (params.integrationId) q.set('integrationId', String(params.integrationId));
    q.set('page', String(params.page ?? 0));
    q.set('size', String(params.size ?? 20));
    return request<PageResponse<Event>>(`/api/v1/events?${q}`);
  },
  get: (id: number) => request<Event>(`/api/v1/events/${id}`),
  retry: (id: number) => request<Event>(`/api/v1/events/${id}/retry`, { method: 'POST' }),
  stats: () => request<DashboardStats>('/api/v1/events/stats'),
};
