// 담당: 공용 — 시연 세션
const sessionId = crypto.randomUUID();
// 각 탭은 서로 다른 가상 고객이다. 두 흐름을 나란히 시연해도 동의·대화가 섞이지 않는다.
export const getUserId = () => `demo-${sessionId}`;
export const getSessionId = () => sessionId;
