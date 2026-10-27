// 담당: 공용 — 말풍선(R1)을 눌러 열린 대화의 시작 카드. 역질문 한 줄 + 예상 질문 버튼(최대 3개).
const MAX_QUESTIONS = 3;
// 뒤로 가기는 같은 params 객체를 되살린다. 진입 객체별로 한 번 쓰면 다시 띄우지 않는다(menuNavigation과 같은 방식).
const used = new WeakSet();
export const markHelpUsed = (params) => { if (params && typeof params === 'object') used.add(params); };

/** 말풍선에서 오지 않았거나(helpQuestion 없음) 이미 질문을 보냈으면 null. 예상 질문이 없으면 [제안한 내용으로 안내받기]만. */
export function helpChoices(params, sent = false) {
  if (sent || !params?.helpQuestion || used.has(params)) return null;
  const questions = Array.isArray(params.helpQuestions) ? params.helpQuestions.slice(0, MAX_QUESTIONS) : [];
  return { title: params.helpQuestion, questions, fallback: questions.length === 0 };
}
