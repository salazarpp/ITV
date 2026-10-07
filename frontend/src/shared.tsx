import { useEffect, useState } from 'react';
import { ApiFailure } from './api';

export function ErrorMessage({ error }: { error: unknown }) {
  if (!error) return null;
  return <div className="notice error" role="alert">
    <span>{error instanceof Error ? error.message : 'An unexpected error occurred.'}</span>
    {error instanceof ApiFailure && error.requestId && <small>Support reference: {error.requestId}</small>}
  </div>;
}

export function useRemote<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(undefined); setData(undefined);
    load(controller.signal).then(value => {
      if (!controller.signal.aborted) setData(value);
    }).catch(cause => {
      if (!controller.signal.aborted) setError(cause);
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [load, reloadKey]);
  return { data, error, loading, reload: () => setReloadKey(key => key + 1) };
}

export function Pager({ offset, count, onChange }: { offset: number; count: number; onChange: (offset: number) => void }) {
  return <nav className="pager" aria-label="Pagination">
    <button className="secondary" disabled={offset === 0} onClick={() => onChange(Math.max(0, offset - 20))}>Previous</button>
    <span>{count === 0 ? 'No records' : `${offset + 1}–${Math.min(offset + 20, count)} of ${count}`}</span>
    <button className="secondary" disabled={offset + 20 >= count} onClick={() => onChange(offset + 20)}>Next</button>
  </nav>;
}

export function PokemonImage({ src, name }: { src: string | null; name: string }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  return src && !failed ? <img className="pokemon-image" src={src} alt={name} loading="lazy" onError={() => setFailed(true)} />
    : <div className="image-placeholder" aria-label={`Image unavailable for ${name}`}>?</div>;
}
