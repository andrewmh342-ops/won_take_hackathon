// 담당: 공용 — SPEC 2.4 API 계약
import { getUserId, getSessionId } from '../session/index.js';
import { mock } from './mock.js';
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
    throw new Error(error.message || `요청을 처리하지 못했어요 (${response.status})`);
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
export const getHistory = () => useMock ? mock.getHistory() : request(`/chat/history?${new URLSearchParams(identity())}`);
export const getHealth = () => useMock ? Promise.resolve({ aiMode: 'FRONTEND_MOCK' }) : request('/health');
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
export const getTickets = () => (useMock ? mock.getTickets() : request('/handoff/tickets'));
