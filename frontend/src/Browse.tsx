import { useCallback, useState } from 'react';
import { api } from './api';
import { ErrorMessage, Pager, PokemonImage, useRemote } from './shared';

export default function Browse({ onSelect }: { onSelect: (id: number) => void }) {
  const [offset, setOffset] = useState(0);
  const load = useCallback((signal: AbortSignal) => api.browse(offset, signal), [offset]);
  const { data, error, loading, reload } = useRemote(load);
  return <section aria-labelledby="browse-title">
    <div className="section-heading"><div><p className="eyebrow">The field guide</p><h2 id="browse-title">Discover Pokemon</h2></div><span className="count-pill">{data ? `${data.count} Pokemon` : 'PokeAPI collection'}</span></div>
    <ErrorMessage error={error} />
    {Boolean(error) && <button className="secondary" onClick={reload}>Try again</button>}
    {loading && <p className="loading" role="status">Loading Pokemon…</p>}
    {data && <>
      {data.results.length === 0 && <p className="empty">No Pokemon found.</p>}
      <div className="pokemon-grid">{data.results.map(pokemon => <button className="pokemon-card" key={pokemon.id} onClick={() => onSelect(pokemon.id)} aria-label={`View ${pokemon.name}`}>
        <div className="card-top"><span className="pokemon-number">#{String(pokemon.id).padStart(3, '0')}</span><span className="category">{pokemon.category}</span></div>
        <PokemonImage src={pokemon.sprite} name={pokemon.name} />
        <h3>{pokemon.name}</h3>
        <div className="card-meta"><span>Mass <strong>{pokemon.mass} kg</strong></span><span>{pokemon.skills.length} skills</span></div>
        <p className="skill-list">{pokemon.skills.join(' · ')}</p>
      </button>)}</div>
      <Pager offset={offset} count={data.count} onChange={setOffset} />
    </>}
  </section>;
}
