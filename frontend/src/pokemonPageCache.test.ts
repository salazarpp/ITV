import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Page, PokemonSummary } from './api';
import { PokemonPageCache, POKEMON_PAGE_CACHE_CAPACITY, POKEMON_PAGE_CACHE_TTL_MS } from './pokemonPageCache';

const page = (id: number): Page<PokemonSummary> => ({ count: 1000, results: [{ id, name: `pokemon-${id}`, sprite: null, category: 'Pokemon', mass: 1, skills: [] }] });
const signal = () => new AbortController().signal;

beforeEach(() => { vi.useFakeTimers(); vi.setSystemTime(0); });
afterEach(() => vi.useRealTimers());

describe('visited public Pokemon pages', () => {
  it('reuses a previously visited page without another fetch', async () => {
    const cache = new PokemonPageCache();
    const first = page(1);
    const fetchPage = vi.fn().mockResolvedValueOnce(first).mockResolvedValueOnce(page(21));
    await cache.load(20, 0, fetchPage, signal());
    await cache.load(20, 20, fetchPage, signal());
    expect(cache.get(20, 0)).toBe(first);
    await expect(cache.load(20, 0, fetchPage, signal())).resolves.toBe(first);
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });

  it('expires at five minutes without extending expiry on reads', async () => {
    const cache = new PokemonPageCache();
    const first = page(1);
    const refreshed = page(2);
    const fetchPage = vi.fn().mockResolvedValueOnce(first).mockResolvedValueOnce(refreshed);
    await cache.load(20, 0, fetchPage, signal());
    vi.setSystemTime(POKEMON_PAGE_CACHE_TTL_MS - 1);
    expect(cache.get(20, 0)).toBe(first);
    vi.setSystemTime(POKEMON_PAGE_CACHE_TTL_MS);
    expect(cache.get(20, 0)).toBeUndefined();
    await expect(cache.load(20, 0, fetchPage, signal())).resolves.toBe(refreshed);
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });

  it('evicts the oldest inserted page beyond ten pages even when that page was read again', async () => {
    const cache = new PokemonPageCache();
    for (let index = 0; index < POKEMON_PAGE_CACHE_CAPACITY; index++) {
      await cache.load(20, index * 20, async () => page(index), signal());
    }
    expect(cache.get(20, 0)).toBeDefined();
    await cache.load(20, 200, async () => page(11), signal());
    expect(cache.get(20, 0)).toBeUndefined();
    expect(cache.get(20, 20)).toBeDefined();
    expect(cache.get(20, 200)).toBeDefined();
  });

  it('separates the same offset for different page sizes', async () => {
    const cache = new PokemonPageCache();
    const smaller = page(1);
    const larger = page(2);
    await cache.load(10, 0, async () => smaller, signal());
    await cache.load(20, 0, async () => larger, signal());
    expect(cache.get(10, 0)).toBe(smaller);
    expect(cache.get(20, 0)).toBe(larger);
  });

  it('does not retain failed responses and allows a subsequent retry', async () => {
    const cache = new PokemonPageCache();
    const failure = new Error('Provider unavailable');
    const fetchPage = vi.fn().mockRejectedValueOnce(failure).mockResolvedValueOnce(page(1));
    await expect(cache.load(20, 0, fetchPage, signal())).rejects.toBe(failure);
    expect(cache.get(20, 0)).toBeUndefined();
    await expect(cache.load(20, 0, fetchPage, signal())).resolves.toEqual(page(1));
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });

  it('rejects a pre-aborted cache hit without fetching or deleting the successful cached page', async () => {
    const cache = new PokemonPageCache();
    const first = page(1);
    await cache.load(20, 0, async () => first, signal());
    const controller = new AbortController();
    controller.abort();
    const fetchPage = vi.fn();
    await expect(cache.load(20, 0, fetchPage, controller.signal)).rejects.toMatchObject({ name: 'AbortError' });
    expect(fetchPage).not.toHaveBeenCalled();
    expect(cache.get(20, 0)).toBe(first);
  });

  it('does not cache a successful response when its caller aborted during the fetch', async () => {
    const cache = new PokemonPageCache();
    const controller = new AbortController();
    const fetchPage = vi.fn(async () => { controller.abort(); return page(1); });
    await expect(cache.load(20, 0, fetchPage, controller.signal)).rejects.toMatchObject({ name: 'AbortError' });
    expect(cache.get(20, 0)).toBeUndefined();
    const retry = vi.fn().mockResolvedValue(page(2));
    await expect(cache.load(20, 0, retry, signal())).resolves.toEqual(page(2));
    expect(retry).toHaveBeenCalledTimes(1);
  });
});
