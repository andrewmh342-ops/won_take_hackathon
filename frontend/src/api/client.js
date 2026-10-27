// 담당: 공용 — SPEC 2.4 API 계약
import { getUserId, getSessionId } from '../session/index.js';
import { mock } from './mock.js';
import { mockDemo } from './mockDemo.js';
import { createEventQueue } from './eventQueue.js';
export const useMock = import.meta.env.VITE_USE_MOCK === 'true';
const eventQueue = createEventQueue();
async function request(path, method = 'GET', body, raw = false) {
  const response = await fetch(`/api${path}`, {
    method,
    headers:
      body === undefined
        ? {}
        : { 'Content-Type': raw ? 'application/octet-stream' : 'application/json' },
    body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(55000),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    // code는 화면이 서버 코드를 자기 문구로 바꿀 때 쓴다(예: 작업 카드 승인, plan/planView.js).
    throw Object.assign(new Error(error.message || `요청을 처리하지 못했어요 (${response.status})`), { code: error.code });
  }
  return response.status === 204 ? undefined : response.json();
}
const identity = () => ({ userId: getUserId(), sessionId: getSessionId() });
export const getConsent = () =>
  useMock ? mock.getConsent() : request(`/consent?userId=${encodeURIComponent(getUserId())}`);
export const putConsent = async (agreed) => {
  await eventQueue.flush().catch(() => {});
  return useMock ? mock.putConsent(agreed) : request('/consent', 'PUT', { userId: getUserId(), agreed });
};
export const postEvent = (event) =>
  eventQueue.enqueue(() => useMock ? mock.postEvent(event) : request('/events', 'POST', { ...identity(), ...event }));
export const closeBubble = (screenId, action) =>
  useMock
    ? mock.closeBubble(screenId, action)
    : request('/bubble/close', 'POST', { ...identity(), screenId, action });
export const postChat = async (input) => {
  await eventQueue.flush();
  return useMock ? mock.postChat(input) : request('/chat', 'POST', { ...identity(), ...input });
};
export const confirmPlan = (planId, password) =>
  useMock ? mock.confirmPlan(planId) : request('/plan/confirm', 'POST', { ...identity(), planId, password });
export const getHistory = () => useMock ? mock.getHistory() : request(`/chat/history?${new URLSearchParams(identity())}`);
export const getHealth = () => useMock ? Promise.resolve({ aiMode: 'FRONTEND_MOCK' }) : request('/health');
export const getAccounts = () => useMock ? mock.getAccounts() : request(`/accounts?userId=${encodeURIComponent(getUserId())}`);
export const analyzeHelp = (contextId) => useMock ? Promise.resolve({ bubble: null }) : request('/assist/analyze', 'POST', { ...identity(), contextId });
export const postVoice = (blob) =>
  useMock
    ? mock.postVoice(blob)
    : request(`/voice?${new URLSearchParams(identity())}`, 'POST', blob, true);
export const getHandoffSummary = () =>
  useMock
    ? mock.getHandoffSummary()
    : request('/handoff/summary', 'POST', identity());
export const postHandoff = (summary) =>
  useMock
    ? mock.postHandoff(summary)
    : request('/handoff', 'POST', { ...identity(), summary });
// R5 원테이크 플랜: 블록 실행(가상 상태 변경)과 시연 고객 A·B 전환
export const executePlan = (planId, index, options) =>
  useMock ? mock.executePlan(planId, index) : request('/plan/execute', 'POST', { ...identity(), planId, index, options });
// R5 목표 중심: 분실 카드 확인(작업 23)과 정보 확인 블록 답변(작업 29). 둘 다 AI가 다시 판단한 플랜을 돌려준다.
export const confirmCards = (planId, cardRefs) =>
  useMock ? mock.confirmCards(planId, cardRefs) : request('/plan/confirm-cards', 'POST', { ...identity(), planId, cardRefs });
export const answerPlan = (planId, index, value) =>
  useMock ? mock.answerPlan(planId, index, value) : request('/plan/answer', 'POST', { ...identity(), planId, index, value });
// 작업 32: 원테이크 화면을 다시 열 때 현재 플랜, [추가로 할 일 찾기](AI 재판단)
export const getPlan = (planId) =>
  useMock ? mock.getPlan(planId) : request(`/plan?${new URLSearchParams({ ...identity(), planId })}`);
export const reviewPlan = (planId) =>
  useMock ? mock.reviewPlan(planId) : request('/plan/review', 'POST', { ...identity(), planId });
export const setDemoProfile = (profileId) =>
  useMock ? Promise.resolve() : request('/demo/profile', 'PUT', { ...identity(), profileId });
// R5 업무 화면: 화면용 가상 상태와 처리(실제 거래 없음)
export const getDemoState = () =>
  useMock ? mockDemo.getState() : request(`/demo/state?${new URLSearchParams(identity())}`);
export const demoAction = (payload) =>
  useMock ? mockDemo.act(payload) : request('/demo/action', 'POST', { ...identity(), ...payload });
export const getTickets = () => (useMock ? mock.getTickets() : request('/handoff/tickets'));
