import { afterEach, vi } from 'vitest';
import { ApiError, buildUrl, errorMessage, parseRetryAfter, request, setTokenSource, toApiError } from './client';

const problem = (status: number, body: object, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/problem+json', ...headers } });

afterEach(() => {
  vi.unstubAllGlobals();
  setTokenSource({ get: () => null, renew: async () => null });
});

describe('error mapping', () => {
  it("keeps the problem's detail, field errors and Retry-After", async () => {
    const error = await toApiError(problem(429, { title: 'Too Many Requests', status: 429, detail: 'Too many hold requests, try again in 6 seconds' }, { 'Retry-After': '6' }));
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(429);
    expect(error.detail).toBe('Too many hold requests, try again in 6 seconds');
    expect(error.retryAfter).toBe(6);

    const invalid = await toApiError(problem(400, { detail: 'Validation failed', errors: { quantity: 'must be less than or equal to 10' } }));
    expect(invalid.errors).toEqual({ quantity: 'must be less than or equal to 10' });
  });

  it('explains a gateway error page that is not a problem document', async () => {
    const error = await toApiError(new Response('<html>Bad Gateway</html>', { status: 502 }));
    expect(error.detail).toMatch(/not answering/);
  });

  it('reads Retry-After as seconds or as a date', () => {
    expect(parseRetryAfter('23')).toBe(23);
    expect(parseRetryAfter(new Date(Date.UTC(2030, 0, 1, 0, 0, 30)).toUTCString(), Date.UTC(2030, 0, 1))).toBe(30);
    expect(parseRetryAfter(null)).toBeNull();
  });

  it('turns anything thrown into a sentence', () => {
    expect(errorMessage(new ApiError(409, 'Conflict', 'One or more seats are already held or booked'))).toBe('One or more seats are already held or booked');
    expect(errorMessage(new TypeError('x'))).toBe('Something went wrong. Try again.');
  });
});

describe('requests', () => {
  it('repeats filters and skips empty ones', () => {
    expect(buildUrl('/movies', { cityId: 5, language: ['TAMIL', 'ENGLISH'], genre: [], query: '' })).toBe('/api/v1/movies?cityId=5&language=TAMIL&language=ENGLISH');
  });

  it('renews the token once after a 401 and retries', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(problem(401, { detail: 'Invalid or expired token' }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 1 }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const renew = vi.fn().mockResolvedValue('fresh');
    setTokenSource({ get: () => 'stale', renew });

    await expect(request('/bookings/1', { auth: true })).resolves.toEqual({ id: 1 });
    expect(renew).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer stale');
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer fresh');
  });

  it('never sends a token to public endpoints', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    setTokenSource({ get: () => 'secret', renew: async () => null });
    await request('/cities');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('reports a lost connection plainly', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(request('/cities')).rejects.toMatchObject({ status: 0, detail: expect.stringMatching(/Couldn't reach SeatLock/) });
  });
});
