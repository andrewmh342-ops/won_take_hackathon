// 담당: 공용 — 시연용 로그인 상태. DB·서버 없이 화면 안에서만 유지하고, 새로고침하면 로그아웃된다.
// 간편비밀번호는 저장하지 않고 길이(6자리)만 확인한다.
import { useSyncExternalStore } from 'react';

const DEMO_USER = { name: '데모 고객' };
let user = null;
const listeners = new Set();

function emit() {
  listeners.forEach((listener) => listener());
}

export const PIN_LENGTH = 6;

export function login(pin) {
  if (pin.length !== PIN_LENGTH) return false;
  user = DEMO_USER;
  emit();
  return true;
}

export function logout() {
  user = null;
  emit();
}

function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useAuth() {
  return useSyncExternalStore(subscribe, () => user);
}
