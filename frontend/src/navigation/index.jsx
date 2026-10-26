// 담당: 공용 — 화면 상태와 이동, 동의 및 이벤트 연결
import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import * as api from '../api/client.js';
import { trackScreen, trackClick } from '../tracking/index.js';
const Navigation = createContext(null);
export const useNavigation = () => useContext(Navigation);
export function NavigationProvider({ children }) {
  const [route, setRoute] = useState({ screen: 'home', params: {}, key: 0 });
  const [agreed, setAgreed] = useState(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [bubble, setBubble] = useState(null);
  const current = useRef({ route, agreed }); current.current = { route, agreed };
  const navigate = useCallback((screen, params = {}) => { setBubble(null); setRoute(old => ({ screen, params, key: old.key + 1 })); }, []);
  const load = useCallback(async () => {
    setError('');
    try { const result = await api.getConsent(); setAgreed(result.agreed); if (result.agreed === null) navigate('consent'); setReady(true); }
    catch (e) { setError(e.message); }
  }, [navigate]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    let active = true;
    if (ready && !['consent', 'agent'].includes(route.screen)) trackScreen(route.screen, agreed).then(result => { if (active && result.bubble) setBubble(result.bubble); }).catch(() => {});
    return () => { active = false; };
  }, [route.key, ready, agreed]);
  async function changeConsent(value) { await api.putConsent(value); setAgreed(value); setBubble(null); navigate('home'); }
  function onClickCapture(event) {
    const element = event.target.closest('[data-track-id]');
    if (!element || ['consent', 'agent'].includes(route.screen)) return;
    const key = route.key;
    trackClick(route.screen, element.dataset.trackId, agreed).then(result => { if (current.current.route.key === key && current.current.agreed === true && result.bubble) setBubble(result.bubble); }).catch(() => {});
  }
  return <Navigation.Provider value={{ ...route, navigate, agreed, changeConsent, bubble, setBubble }}><div onClickCapture={onClickCapture}>{ready ? children : <div className="loading"><strong>WON테이크</strong><p>{error || '화면을 준비하고 있어요'}</p>{error && <button onClick={load}>다시 시도</button>}</div>}</div></Navigation.Provider>;
}
