export interface Page<T> { count: number; results: T[] }
export interface PokemonSummary {
  id: number; name: string; sprite: string | null; category: string; mass: number; skills: string[];
}
export interface PokemonDetail {
  id: number; name: string; image: string | null; category: string; mass: number; skills: string[];
  stats: { name: string; value: number }[]; description: string; evolution: { id: number; name: string }[];
}
export interface LocalPokemon {
  id: string; pokeApiId: number; name: string; image: string | null;
  customName: string | null; region: string | null; internalClassification: string | null;
}
export interface LocalFields { customName: string | null; region: string | null; internalClassification: string | null }
export interface TokenResponse { accessToken: string; tokenType: string; expiresIn: number }

export class ApiFailure extends Error {
  constructor(message: string, public readonly status: number, public readonly requestId?: string) {
    super(message);
    this.name = 'ApiFailure';
  }
}

export async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let response: Response;
  try {
    response = await fetch(path, { ...options, headers, credentials: 'omit' });
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error;
    throw new ApiFailure('Unable to reach PokeSync. Check your connection and try again.', 0);
  }
  if (!response.ok) {
    if (response.status === 401 && token && typeof window !== 'undefined') {
      window.dispatchEvent(new Event('pokesync:session-expired'));
    }
    const body = await response.json().catch(() => ({})) as { message?: string; requestId?: string };
    throw new ApiFailure(body.message || 'The request could not be completed.', response.status,
      body.requestId || response.headers.get('X-Request-ID') || undefined);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  browse: (offset: number, signal?: AbortSignal) => request<Page<PokemonSummary>>(`/api/v1/pokemon?limit=20&offset=${offset}`, { signal }),
  detail: (id: number, signal?: AbortSignal) => request<PokemonDetail>(`/api/v1/pokemon/${id}`, { signal }),
  login: (username: string, password: string) => request<TokenResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  register: (username: string, email: string, password: string) => request('/auth/register', { method: 'POST', body: JSON.stringify({ username, email, password }) }),
  local: (offset: number, token: string, signal?: AbortSignal) => request<Page<LocalPokemon>>(`/api/v1/local-pokemon?limit=20&offset=${offset}`, { signal }, token),
  localDetail: (id: string, token: string) => request<LocalPokemon>(`/api/v1/local-pokemon/${encodeURIComponent(id)}`, {}, token),
  synchronize: (pokeApiId: number, fields: LocalFields, token: string) => request<LocalPokemon>('/api/v1/local-pokemon', { method: 'POST', body: JSON.stringify({ pokeApiId, ...fields }) }, token),
  update: (id: string, fields: LocalFields, token: string) => request<LocalPokemon>(`/api/v1/local-pokemon/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(fields) }, token),
  remove: (id: string, token: string) => request<void>(`/api/v1/local-pokemon/${encodeURIComponent(id)}`, { method: 'DELETE' }, token),
};
