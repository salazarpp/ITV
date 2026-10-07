import { useCallback, useState, type FormEvent } from 'react';
import { api, type LocalPokemon } from './api';
import { fieldsFromForm, LocalFieldsForm } from './Detail';
import { ErrorMessage, Pager, PokemonImage, useRemote } from './shared';

export default function Collection({ token }: { token: string }) {
  const [offset, setOffset] = useState(0);
  const load = useCallback((signal: AbortSignal) => api.local(offset, token, signal), [offset, token]);
  const { data, error, loading, reload } = useRemote(load);
  const [selected, setSelected] = useState<LocalPokemon>();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<unknown>();
  const [notice, setNotice] = useState('');
  async function select(id: string) {
    setBusy(true); setActionError(undefined); setNotice(''); setConfirmDelete(false);
    try { setSelected(await api.localDetail(id, token)); }
    catch (cause) { setActionError(cause); } finally { setBusy(false); }
  }
  async function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const fields = fieldsFromForm(event.currentTarget);
    setBusy(true); setActionError(undefined);
    try { setSelected(await api.update(selected.id, fields, token)); reload(); setNotice('Pokemon updated.'); }
    catch (cause) { setActionError(cause); } finally { setBusy(false); }
  }
  async function remove() {
    if (!selected) return;
    setBusy(true); setActionError(undefined);
    try {
      await api.remove(selected.id, token); setSelected(undefined); setConfirmDelete(false); setNotice('Pokemon removed from your collection.');
      if (data?.results.length === 1 && offset > 0) setOffset(offset - 20); else reload();
    } catch (cause) { setActionError(cause); } finally { setBusy(false); }
  }
  return <section aria-labelledby="collection-title">
    <div className="section-heading"><div><p className="eyebrow">Your workspace</p><h2 id="collection-title">Local collection</h2></div><span className="count-pill">{data?.count || 0} saved</span></div>
    {notice && <p className="notice" role="status">{notice}</p>}<ErrorMessage error={error} /><ErrorMessage error={actionError} />
    {Boolean(error) && <button className="secondary" onClick={reload}>Try again</button>}
    {loading && <p className="loading" role="status">Loading collection…</p>}
    <div className="collection-layout"><div>
      {data && <>{data.results.length === 0 && <div className="empty"><h3>Your collection starts here</h3><p>Explore a Pokemon and synchronize it to add your first entry.</p></div>}
        <div className="local-list">{data.results.map(pokemon => <button className="local-card" key={pokemon.id} disabled={busy} onClick={() => select(pokemon.id)} aria-label={`Edit ${pokemon.customName || pokemon.name}`}>
          <PokemonImage src={pokemon.image} name={pokemon.name} /><div><h3>{pokemon.customName || pokemon.name}</h3><p>{pokemon.name} · #{pokemon.pokeApiId}</p><span>{pokemon.region || 'No region'} · {pokemon.internalClassification || 'Unclassified'}</span></div><span aria-hidden="true">→</span>
        </button>)}</div><Pager offset={offset} count={data.count} onChange={setOffset} /></>}
    </div>{selected && <aside className="panel editor" aria-labelledby="editor-title"><div className="section-heading"><h2 id="editor-title">Edit {selected.name}</h2><button className="text-button" disabled={busy} onClick={() => { setSelected(undefined); setConfirmDelete(false); }}>Close</button></div>
      <form key={selected.id} onSubmit={update}><LocalFieldsForm values={selected} /><button disabled={busy}>{busy ? 'Please wait…' : 'Save changes'}</button></form>
      {confirmDelete ? <div className="delete-confirm"><p>Remove {selected.customName || selected.name} from the local collection?</p><button className="danger" disabled={busy} onClick={remove}>Confirm removal</button><button className="text-button" disabled={busy} onClick={() => setConfirmDelete(false)}>Cancel</button></div>
        : <button className="text-button danger-text" disabled={busy} onClick={() => setConfirmDelete(true)}>Remove Pokemon</button>}
    </aside>}</div>
  </section>;
}
