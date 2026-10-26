// 담당: 역할 1 — 동의한 화면 진입과 클릭만 전송
import { postEvent } from '../api/client.js';
export function trackScreen(screenId, agreed, menuId) {
  return agreed === true
    ? postEvent({ type: 'SCREEN_ENTER', screenId, ...(menuId ? { elementId: menuId } : {}) })
    : Promise.resolve({ bubble: null });
}
export function trackSearch(screenId, query, agreed) {
  return agreed === true && query.trim()
    ? postEvent({ type: 'SEARCH', screenId, query: query.trim().slice(0, 2000) })
    : Promise.resolve({ bubble: null });
}
export function trackClick(screenId, elementId, agreed) {
  return agreed === true
    ? postEvent({ type: 'CLICK', screenId, elementId })
    : Promise.resolve({ bubble: null });
}
