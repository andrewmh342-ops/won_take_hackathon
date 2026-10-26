// 담당: 공용 — 화면 상태와 이동, 동의 및 이벤트 연결
import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import * as api from '../api/client.js';
import { trackScreen, trackClick } from '../tracking/index.js';
import { findMenu } from '../data/menu.js';
const Navigation = createContext(null);
export const useNavigation = () => useContext(Navigation);
export function NavigationProvider({ children }) {
  const [route, setRoute] = useState({ screen: 'home', params: {}, key: 0 });
  const [agreed, setAgreed] = useState(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [bubble, setBubble] = useState(null);
  // 상단 상담 버튼: 강조 여부(대화 결과로 정함)와, 다른 화면에서 눌렀을 때 대화 화면이 이어서 열 요청
  const [handoffHint, setHandoffHint] = useState(false);
  const [handoffPending, setHandoffPending] = useState(false);
  const current = useRef({ route, agreed });
  const latestAnalysis = useRef(null);
  current.current = { route, agreed };
  // 지나온 화면 기록. 홈은 출발점이라 홈으로 가면 기록을 비운다.
  const history = useRef([]);
  const navigate = useCallback((screen, params = {}) => {
    const from = current.current.route;
    history.current =
      screen === 'home' ? [] : [...history.current, { screen: from.screen, params: from.params }];
    setBubble(null);
    setRoute((old) => ({ screen, params, key: old.key + 1 }));
  }, []);
  // 바로 전 화면으로 돌아간다. 돌아갈 곳이 없으면 홈으로 간다.
  const goBack = useCallback(() => {
    const previous = history.current.at(-1) ?? { screen: 'home', params: {} };
    history.current = history.current.slice(0, -1);
    setBubble(null);
    setRoute((old) => ({ screen: previous.screen, params: previous.params, key: old.key + 1 }));
  }, []);
  const load = useCallback(async () => {
    setError('');
    try {
      const result = await api.getConsent();
      setAgreed(result.agreed);
      if (result.agreed === null) navigate('consent');
      setReady(true);
    } catch (e) {
      setError(e.message);
    }
  }, [navigate]);
  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    let active = true;
    if (ready && !['consent', 'agent', 'login'].includes(route.screen))
      trackScreen(route.screen, agreed, route.params.menuId)
        .then(async (result) => {
          if (!result.analysisId) {
            if (active && current.current.agreed === true && result.bubble) setBubble(result.bubble);
            return;
          }
          // 빠르게 다른 메뉴로 이동해도 이미 감지한 행동 흐름의 분석 요청은 보낸다.
          latestAnalysis.current = result.analysisId;
          const analysis = await api.analyzeHelp(result.analysisId);
          const now = current.current;
          const category = findMenu(now.route.params.menuId)?.category;
          if (latestAnalysis.current === result.analysisId && now.agreed === true && analysis.bubble
            && !['chat', 'consent', 'agent', 'login'].includes(now.route.screen)
            && (!category || category === analysis.bubble.category)) setBubble(analysis.bubble);
        })
        .catch(() => {});
    return () => {
      active = false;
    };
  }, [route.key, ready, agreed]);
  const openHandoff = useCallback(() => {
    setHandoffPending(true);
    if (current.current.route.screen !== 'chat') navigate('chat');
  }, [navigate]);
  async function changeConsent(value) {
    await api.putConsent(value);
    setAgreed(value);
    setBubble(null);
    navigate('home');
  }
  function onClickCapture(event) {
    const element = event.target.closest('[data-track-id]');
    if (!element || ['consent', 'agent', 'login'].includes(route.screen)) return;
    const key = route.key;
    trackClick(route.screen, element.dataset.trackId, agreed)
      .then((result) => {
        if (current.current.route.key === key && current.current.agreed === true && result.bubble)
          setBubble(result.bubble);
      })
      .catch(() => {});
  }
  return (
    <Navigation.Provider
      value={{
        ...route, navigate, goBack, agreed, changeConsent, bubble, setBubble,
        handoffHint, setHandoffHint, handoffPending, setHandoffPending, openHandoff,
      }}
    >
      <div onClickCapture={onClickCapture}>
        {ready ? (
          children
        ) : (
          <div className="loading">
            <strong>WON테이크</strong>
            <p>{error || '화면을 준비하고 있어요'}</p>
            {error && <button onClick={load}>다시 시도</button>}
          </div>
        )}
      </div>
    </Navigation.Provider>
  );
}
