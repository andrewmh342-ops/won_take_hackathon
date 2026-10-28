// 담당: 공용 — 화면 상태와 이동, 동의 및 이벤트 연결
import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import * as api from '../api/client.js';
import { trackScreen, trackClick } from '../tracking/index.js';
import { findMenu } from '../data/menu.js';
import { canShowHelpBubble, hasUnfinishedPlan, endPlan } from './helpBubble.js';
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
  // 이 세션에서 연 원테이크 플랜 id → 마쳤는지. 마치지 않은 플랜이 있으면 R1 도움 제안 말풍선을 띄우지 않는다.
  const plans = useRef(new Map());
  const unfinishedPlan = () => hasUnfinishedPlan(plans.current);
  // 원테이크 화면(마친 화면 포함)에서는 띄우지 않는다.
  const bubbleBlocked = () => unfinishedPlan() || current.current.route.screen === 'plan';
  // 원테이크 화면·업무 화면이 플랜 상태를 받을 때마다 알려 준다.
  const notePlanProgress = useCallback((planId, done) => {
    if (planId) plans.current.set(planId, Boolean(done));
  }, []);
  // [아니요, 끝낼게요]: 고객이 끝낸 플랜으로 기록해, 원테이크 화면 밖에서는 말풍선이 다시 뜰 수 있게 한다.
  const finishPlan = useCallback((planId) => endPlan(plans.current, planId), []);
  // replace: 지금 화면을 기록에 남기지 않고 바꾼다(스플래시처럼 잠깐 보이는 화면).
  const navigate = useCallback((screen, params = {}, { replace = false } = {}) => {
    const from = current.current.route;
    if (params.planId && !plans.current.has(params.planId)) plans.current.set(params.planId, false);
    if (screen === 'home') history.current = [];
    else if (!replace) history.current = [...history.current, { screen: from.screen, params: from.params }];
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
    if (ready && !['consent', 'agent', 'login', 'splash'].includes(route.screen))
      trackScreen(route.screen, agreed, route.params.menuId, unfinishedPlan())
        .then(async (result) => {
          if (!result.analysisId) {
            if (active && current.current.agreed === true && result.bubble && !bubbleBlocked()) setBubble(result.bubble);
            return;
          }
          // 빠르게 다른 메뉴로 이동해도 이미 감지한 행동 흐름의 분석 요청은 보낸다.
          latestAnalysis.current = result.analysisId;
          const analysis = await api.analyzeHelp(result.analysisId);
          const now = current.current;
          const category = findMenu(now.route.params.menuId)?.category;
          if (latestAnalysis.current === result.analysisId && now.agreed === true && analysis.bubble
            && canShowHelpBubble(now.route.screen, now.route.params, unfinishedPlan())
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
    if (!element || ['consent', 'agent', 'login', 'splash'].includes(route.screen)) return;
    const key = route.key;
    trackClick(route.screen, element.dataset.trackId, agreed)
      .then((result) => {
        if (current.current.route.key === key && current.current.agreed === true && result.bubble && !bubbleBlocked())
          setBubble(result.bubble);
      })
      .catch(() => {});
  }
  return (
    <Navigation.Provider
      value={{
        ...route, navigate, goBack, agreed, changeConsent, bubble, setBubble, notePlanProgress, finishPlan,
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
