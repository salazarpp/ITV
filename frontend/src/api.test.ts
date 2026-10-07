import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiFailure, request } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('API transport', () => {
  it('keeps the access token in the Authorization header and serializes full local metadata', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'local-id' }), { status: 201 }));
    vi.stubGlobal('fetch', fetchMock);
    await api.synchronize(1, { customName: 'Leaf', region: 'Kanto', internalClassification: 'Starter' }, 'test-access-token');
    const [path, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe('/api/v1/local-pokemon');
    expect(new Headers(options.headers).get('Authorization')).toBe('Bearer test-access-token');
    expect(JSON.parse(String(options.body))).toEqual({ pokeApiId: 1, customName: 'Leaf', region: 'Kanto', internalClassification: 'Starter' });
    expect(options.credentials).toBe('omit');
  });

  it('does not add authentication to public Pokemon reads', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ count: 0, results: [] })));
    vi.stubGlobal('fetch', fetchMock);
    await api.browse(20);
    const [path, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe('/api/v1/pokemon?limit=20&offset=20');
    expect(new Headers(options.headers).has('Authorization')).toBe(false);
  });

  it('preserves the API error message and correlation reference for support', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Pokemon not found.', requestId: 'trace-404' }), { status: 404 })));
    await expect(request('/api/v1/pokemon/999999')).rejects.toMatchObject({ status: 404, message: 'Pokemon not found.', requestId: 'trace-404' });
  });

  it('uses header correlation when an upstream error response is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('bad gateway', { status: 502, headers: { 'X-Request-ID': 'trace-502' } })));
    await expect(request('/api/v1/pokemon/1')).rejects.toMatchObject({ status: 502, requestId: 'trace-502' });
  });

  it('accepts an empty delete response without trying to decode JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(api.remove('local-id', 'test-token')).resolves.toBeUndefined();
  });

  it('turns connection failure into a useful message without exposing implementation errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('sensitive internal transport text')));
    await expect(request('/api/v1/pokemon')).rejects.toBeInstanceOf(ApiFailure);
    await expect(request('/api/v1/pokemon')).rejects.toMatchObject({ status: 0, message: 'Unable to reach PokeSync. Check your connection and try again.' });
  });

  it('propagates cancellation to avoid presenting an error after navigation', async () => {
    const abort = new DOMException('Aborted', 'AbortError');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abort));
    await expect(request('/api/v1/pokemon')).rejects.toBe(abort);
  });
});
