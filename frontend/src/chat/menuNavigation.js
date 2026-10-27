import { findMenu } from '../data/menu.js';

const menuIds = (reply) => reply.menuIds ?? (reply.menuId ? [reply.menuId] : []);
const sentInitialQuestions = new WeakSet();

// 뒤로가기는 Navigation의 같은 params 객체를 복원하지만 ChatScreen은 새로 마운트된다.
// 화면 진입 객체별로 한 번만 자동 전송해, 돌아온 즉시 같은 메뉴로 다시 이동하는 반복을 막는다.
// 새 검색은 별도 params 객체이므로 같은 질문도 다시 보낼 수 있다.
export function claimInitialQuestion(params) {
  if (!params.question || !(params.intentId || params.autoSend) || sentInitialQuestions.has(params)) return false;
  sentInitialQuestions.add(params);
  return true;
}

// 메뉴 버튼과 자동 이동은 같은 카탈로그를 사용한다. AI가 화면 경로나 URL을 정하지 않는다.
export function replyMenus(reply) {
  return [...new Set(menuIds(reply))].slice(0, 2).map(findMenu).filter(Boolean);
}

// 자동 이동 여부와 표시 문구가 어긋나지 않게 같은 조건으로 대상 메뉴를 고른다.
function automaticReplyMenu(reply) {
  if (reply.type !== 'ANSWER' || reply.plan || reply.transferPlan || reply.handoffSuggested || menuIds(reply).length !== 1) return null;
  return replyMenus(reply)[0] ?? null;
}

// 새 답변을 받은 자리에서만 부른다. 기록 복원 시 이동하거나, 두 후보 중 임의로 고르지 않는다.
export function openReplyMenu(reply, navigate) {
  const item = automaticReplyMenu(reply);
  if (!item) return false;
  navigate(item.screenId, { menuId: item.id });
  return true;
}

// 메뉴를 바로 여는 답은 경로 안내가 필요 없다. 렌더링 때 적용해 과거 대화의 긴 가이드도 같은 규칙으로 줄인다.
export function menuReplyContent(message) {
  const item = automaticReplyMenu(message);
  return item
    ? { text: `${item.name} 화면을 열어 드릴게요.`, steps: [] }
    : { text: message.text, steps: Array.isArray(message.steps) ? message.steps : [] };
}
