import { useEffect, useState } from 'react';
import type { TokenResponse } from './api';
import AuthPanel from './AuthPanel';
import Browse from './Browse';
import Collection from './Collection';
import Detail from './Detail';

type Session = { token: string; username: string; expiresAt: number };

export default function App() {
  const [session, setSession] = useState<Session>();
  const [tab, setTab] = useState<'explore' | 'collection'>('explore');
  const [selectedId, setSelectedId] = useState<number>();
  const [authOpen, setAuthOpen] = useState(false);
  const [notice, setNotice] = useState('');
  useEffect(() => {
    const expire = () => { setSession(undefined); setTab('explore'); setNotice('Your session is no longer valid. Sign in again to manage your collection.'); };
    window.addEventListener('pokesync:session-expired', expire);
    return () => window.removeEventListener('pokesync:session-expired', expire);
  }, []);
  useEffect(() => {
    if (!session) return;
    const timer = setTimeout(() => { setSession(undefined); setTab('explore'); setNotice('Your session has expired. Sign in again to manage your collection.'); }, Math.max(0, session.expiresAt - Date.now()));
    return () => clearTimeout(timer);
  }, [session]);
  function login(token: TokenResponse, username: string) {
    setSession({ token: token.accessToken, username, expiresAt: Date.now() + token.expiresIn * 1000 });
    setAuthOpen(false); setNotice(`Signed in as ${username}.`);
  }
  return <>
    <a className="skip-link" href="#main-content">Skip to content</a>
    <header className="topbar"><a className="brand" href="/" aria-label="PokeSync home"><span className="brand-mark" aria-hidden="true">◒</span>Poke<span>Sync</span></a>
      <nav aria-label="Main navigation"><button className={tab === 'explore' ? 'nav-button active' : 'nav-button'} aria-current={tab === 'explore' ? 'page' : undefined} onClick={() => { setTab('explore'); setSelectedId(undefined); }}>Explore</button>
        <button className={tab === 'collection' ? 'nav-button active' : 'nav-button'} aria-current={tab === 'collection' ? 'page' : undefined} onClick={() => session ? (setTab('collection'), setSelectedId(undefined)) : setAuthOpen(true)}>Collection</button></nav>
      <div className="account">{session ? <><span>{session.username}</span><button className="secondary" onClick={() => { setSession(undefined); setTab('explore'); setNotice('Signed out.'); }}>Sign out</button></> : <button onClick={() => setAuthOpen(true)}>Sign in</button>}</div>
    </header>
    <main id="main-content">
      <div className="hero"><div><p className="eyebrow">Explore. Sync. Personalize.</p><h1>A world of Pokemon.<br /><span>A collection of your own.</span></h1><p>Discover every detail, then bring your favorites into your workspace.</p></div><div className="hero-orbit" aria-hidden="true"><span>◒</span><i className="orbit-dot one" /><i className="orbit-dot two" /><i className="orbit-dot three" /></div></div>
      {notice && <div className="notice" role="status">{notice}<button className="text-button" aria-label="Dismiss notification" onClick={() => setNotice('')}>×</button></div>}
      {authOpen && <AuthPanel onLogin={login} onClose={() => setAuthOpen(false)} />}
      {tab === 'collection' && session ? <Collection token={session.token} /> : selectedId !== undefined ? <Detail key={selectedId} id={selectedId} token={session?.token} onBack={() => setSelectedId(undefined)} onSelect={setSelectedId} onSignIn={() => setAuthOpen(true)} onSynchronized={() => { setSelectedId(undefined); setTab('collection'); setNotice('Pokemon synchronized to your collection.'); }} /> : <Browse onSelect={setSelectedId} />}
    </main><footer><span>PokeSync</span><p>Pokemon data provided by PokeAPI.</p><a href="https://pokeapi.co/" target="_blank" rel="noreferrer">About PokeAPI ↗</a></footer>
  </>;
}
