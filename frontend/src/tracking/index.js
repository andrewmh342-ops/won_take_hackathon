// 담당: 역할 1 — 동의한 화면 진입과 클릭만 전송
import { postEvent } from '../api/client.js';
// planActive: 원테이크 플랜 진행 중(안 끝남). 서버는 기록만 남기고 R1 분석을 하지 않는다.
export function trackScreen(screenId, agreed, menuId, planActive = false) {
  return agreed === true
    ? postEvent({ type: 'SCREEN_ENTER', screenId, ...(menuId ? { elementId: menuId } : {}), ...(planActive ? { planActive: true } : {}) })
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
