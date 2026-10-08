import { api, type Page, type PokemonSummary } from './api';

export const POKEMON_PAGE_LIMIT = 20;
export const POKEMON_PAGE_CACHE_TTL_MS = 5 * 60 * 1000;
export const POKEMON_PAGE_CACHE_CAPACITY = 10;

type PokemonPage = Page<PokemonSummary>;
type CacheEntry = { page: PokemonPage; expiresAt: number };

export class PokemonPageCache {
  private readonly entries = new Map<string, CacheEntry>();

  get(limit: number, offset: number): PokemonPage | undefined {
    const key = `${limit}:${offset}`;
    const entry = this.entries.get(key);
    if (!entry) return undefined;
    if (entry.expiresAt <= Date.now()) {
      this.entries.delete(key);
      return undefined;
    }
    return entry.page;
  }

  async load(limit: number, offset: number, fetchPage: () => Promise<PokemonPage>, signal: AbortSignal): Promise<PokemonPage> {
    signal.throwIfAborted();
    const cached = this.get(limit, offset);
    if (cached) return cached;
    const page = await fetchPage();
    signal.throwIfAborted();
    const now = Date.now();
    for (const [key, entry] of this.entries) {
      if (entry.expiresAt <= now) this.entries.delete(key);
    }
    const key = `${limit}:${offset}`;
    this.entries.delete(key);
    this.entries.set(key, { page, expiresAt: now + POKEMON_PAGE_CACHE_TTL_MS });
    // FIFO bounds visited pages; reads never extend their absolute expiry.
    while (this.entries.size > POKEMON_PAGE_CACHE_CAPACITY) {
      this.entries.delete(this.entries.keys().next().value!);
    }
    return page;
  }
}

// Public Pokemon metadata only; retained until expiry, eviction or a full reload.
export const pokemonPageCache = new PokemonPageCache();

export function loadPokemonPage(offset: number, signal: AbortSignal): Promise<PokemonPage> {
  return pokemonPageCache.load(POKEMON_PAGE_LIMIT, offset, () => api.browse(offset, signal), signal);
}
