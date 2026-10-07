import { useState, type FormEvent } from 'react';
import { api, type TokenResponse } from './api';
import { ErrorMessage } from './shared';

export default function AuthPanel({ onLogin, onClose }: { onLogin: (token: TokenResponse, username: string) => void; onClose: () => void }) {
  const [register, setRegister] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [notice, setNotice] = useState('');
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const username = String(form.get('username'));
    const password = String(form.get('password'));
    setBusy(true); setError(undefined); setNotice('');
    try {
      if (register) {
        await api.register(username, String(form.get('email')), password);
        setRegister(false); setNotice('Account created. Sign in to start your collection.');
      } else onLogin(await api.login(username, password), username);
    } catch (cause) { setError(cause); } finally { setBusy(false); }
  }
  return <section className="panel auth-panel" aria-labelledby="auth-title">
    <div className="section-heading"><h2 id="auth-title">{register ? 'Create your account' : 'Welcome back'}</h2><button className="text-button" onClick={onClose}>Close</button></div>
    <p>Sign in to synchronize Pokemon and personalize your collection.</p>
    <ErrorMessage error={error} />
    {notice && <p className="notice" role="status">{notice}</p>}
    <form onSubmit={submit}>
      <label>Username<input name="username" required maxLength={255} autoComplete="username" /></label>
      {register && <label>Email<input name="email" type="email" required maxLength={255} autoComplete="email" /></label>}
      <label>Password<input name="password" type="password" required autoComplete={register ? 'new-password' : 'current-password'} /></label>
      <button disabled={busy}>{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}</button>
    </form>
    <button className="text-button" disabled={busy} onClick={() => { setRegister(!register); setError(undefined); setNotice(''); }}>
      {register ? 'Already have an account? Sign in' : 'New here? Create an account'}
    </button>
  </section>;
}
