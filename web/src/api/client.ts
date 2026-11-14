// Every call goes to the same origin (nginx proxies /api/ to the gateway), so no base URL and no CORS.
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly errors: Record<string, string>;
  readonly retryAfter: number | null;

  constructor(status: number, title: string, detail: string, errors: Record<string, string> = {}, retryAfter: number | null = null) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.errors = errors;
    this.retryAfter = retryAfter;
  }

  get detail(): string {
    return this.message;
  }
}

const NETWORK_DETAIL = "Couldn't reach SeatLock. Check your connection and try again.";

/** Retry-After in seconds (the API only sends seconds; an HTTP date is handled too). */
export function parseRetryAfter(value: string | null, now: number = Date.now()): number | null {
  if (!value) return null;
  const seconds = Number(value);
  if (Number.isFinite(seconds)) return Math.max(0, Math.ceil(seconds));
  const date = Date.parse(value);
  return Number.isNaN(date) ? null : Math.max(0, Math.ceil((date - now) / 1000));
}

export async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = parseRetryAfter(response.headers.get('Retry-After'));
  let body: { title?: string; detail?: string; errors?: Record<string, string> } = {};
  try {
    body = await response.json();
  } catch {
    // not a problem document (e.g. an nginx error page)
  }
  const detail = body.detail ?? fallbackDetail(response.status);
  return new ApiError(response.status, body.title ?? response.statusText, detail, body.errors ?? {}, retryAfter);
}

function fallbackDetail(status: number): string {
  if (status === 502 || status === 504) return 'SeatLock is not answering right now. Try again in a moment.';
  if (status === 503) return 'SeatLock is busy right now. Try again in a moment.';
  if (status === 401) return 'Log in to continue.';
  return 'Something went wrong. Try again.';
}

type TokenSource = {
  /** The current access token, or null when logged out. */
  get: () => string | null;
  /** Called once after a 401: renew silently and return a new token, or null to give up (and log in again). */
  renew: () => Promise<string | null>;
};

let tokens: TokenSource = { get: () => null, renew: async () => null };

export function setTokenSource(source: TokenSource): void {
  tokens = source;
}

export type RequestOptions = {
  method?: 'GET' | 'POST' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | (string | number)[] | null | undefined>;
  /** Send the token; on a 401 renew it once and retry. */
  auth?: boolean;
  signal?: AbortSignal;
  keepalive?: boolean;
};

export function buildUrl(path: string, query?: RequestOptions['query']): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value === null || value === undefined || value === '') continue;
    for (const item of Array.isArray(value) ? value : [value]) params.append(key, String(item));
  }
  const search = params.toString();
  return `/api/v1${path}${search ? `?${search}` : ''}`;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, auth = false, signal, keepalive } = options;
  const send = (token: string | null) => {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (token) headers.Authorization = `Bearer ${token}`;
    return fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
      keepalive,
    });
  };

  let response: Response;
  try {
    response = await send(auth ? tokens.get() : null);
    if (auth && response.status === 401) {
      const renewed = await tokens.renew();
      if (renewed) response = await send(renewed);
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0, 'Network error', NETWORK_DETAIL);
  }

  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** The message a person should see for any thrown error. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.detail;
  return 'Something went wrong. Try again.';
}
