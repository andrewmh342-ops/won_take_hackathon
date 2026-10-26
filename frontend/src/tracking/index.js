// 담당: 역할 1 — 동의한 화면 진입과 클릭만 전송
import { postEvent } from '../api/client.js';
export function trackScreen(screenId, agreed) { return agreed === true ? postEvent({ type: 'SCREEN_ENTER', screenId }) : Promise.resolve({ bubble: null }); }
export function trackClick(screenId, elementId, agreed) { return agreed === true ? postEvent({ type: 'CLICK', screenId, elementId }) : Promise.resolve({ bubble: null }); }
