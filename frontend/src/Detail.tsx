import { useCallback, useState, type FormEvent } from 'react';
import { api, type LocalFields } from './api';
import { ErrorMessage, PokemonImage, useRemote } from './shared';

export function fieldsFromForm(form: HTMLFormElement): LocalFields {
  const values = new FormData(form);
  return { customName: String(values.get('customName') || ''), region: String(values.get('region') || ''), internalClassification: String(values.get('internalClassification') || '') };
}

export function LocalFieldsForm({ values }: { values?: LocalFields }) {
  return <>
    <label>Custom name<input name="customName" defaultValue={values?.customName || ''} maxLength={255} /></label>
    <label>Region<input name="region" defaultValue={values?.region || ''} maxLength={255} /></label>
    <label>Classification<input name="internalClassification" defaultValue={values?.internalClassification || ''} maxLength={255} /></label>
  </>;
}

export default function Detail({ id, token, onBack, onSelect, onSignIn, onSynchronized }: {
  id: number; token?: string; onBack: () => void; onSelect: (id: number) => void; onSignIn: () => void; onSynchronized: () => void;
}) {
  const load = useCallback((signal: AbortSignal) => api.detail(id, signal), [id]);
  const { data, error, loading, reload } = useRemote(load);
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<unknown>();
  async function synchronize(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    const fields = fieldsFromForm(event.currentTarget);
    setBusy(true); setSaveError(undefined);
    try { await api.synchronize(id, fields, token); onSynchronized(); }
    catch (cause) { setSaveError(cause); } finally { setBusy(false); }
  }
  return <section aria-labelledby="detail-title">
    <button className="text-button back-button" onClick={onBack}>← Back to explorer</button>
    <ErrorMessage error={error} />
    {Boolean(error) && <button className="secondary" onClick={reload}>Try again</button>}
    {loading && <p className="loading" role="status">Loading Pokemon details…</p>}
    {data && <div className="detail-layout">
      <article className="panel detail-card">
        <div className="detail-art"><span className="pokemon-number">#{String(data.id).padStart(3, '0')}</span><PokemonImage src={data.image} name={data.name} /></div>
        <div className="detail-copy"><p className="eyebrow">{data.category}</p><h2 id="detail-title">{data.name}</h2><p className="description">{data.description || 'No description available.'}</p>
          <p><strong>Mass:</strong> {data.mass} kg</p><h3>Skills</h3><div className="tags">{data.skills.map(skill => <span key={skill}>{skill}</span>)}</div>
          <h3>Core statistics</h3><dl className="stats">{data.stats.map(stat => <div key={stat.name}><dt>{stat.name}</dt><dd>{stat.value}</dd></div>)}</dl>
          <h3>Evolution lineage</h3><div className="evolution">{data.evolution.length ? data.evolution.map(stage => <button className="secondary" key={stage.id} disabled={stage.id === id} onClick={() => onSelect(stage.id)}>{stage.name}</button>) : <p>No evolution information available.</p>}</div>
        </div>
      </article>
      <aside className="panel sync-panel"><p className="eyebrow">Make it yours</p><h2>Save to collection</h2><p>Keep a local snapshot with your own name, region and classification.</p>
        {token ? <form onSubmit={synchronize}><LocalFieldsForm /><ErrorMessage error={saveError} /><button disabled={busy}>{busy ? 'Synchronizing…' : 'Synchronize Pokemon'}</button></form> : <button onClick={onSignIn}>Sign in to synchronize</button>}
      </aside>
    </div>}
  </section>;
}
