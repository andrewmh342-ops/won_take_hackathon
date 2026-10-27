// 담당: 공용 — AI가 판단하는 동안 말풍선에 차례로 보여 줄 문장. 마지막 문장에서 멈춘다(같은 말을 되풀이하지 않는다).
export const THINKING = {
  // 카드 확인·정보 입력은 SPEC R5 문구("할 일을 다시 정리하고 있어요")로 시작한다.
  confirm: ['할 일을 다시 정리하고 있어요', '정지할 카드와 연결된 결제를 살펴보고 있어요', '급한 일부터 순서를 정하고 있어요'],
  answer: ['할 일을 다시 정리하고 있어요', '알려 주신 내용에 맞춰 바꾸고 있어요', '순서를 다시 맞추고 있어요'],
  review: ['지금 상태를 다시 살펴보고 있어요', '더 해 두면 좋은 일을 찾고 있어요', '거의 다 됐어요'],
  chat: ['말씀을 읽고 있어요', '맞는 메뉴를 찾고 있어요', '답을 정리하고 있어요'],
  load: ['할 일을 불러오고 있어요', '조금만 기다려 주세요'],
};

// 문장을 바꾸는 간격(ms)
export const THINKING_STEP_MS = 1400;

export function thinkingLine(kind, tick) {
  const lines = THINKING[kind] ?? THINKING.review;
  return lines[Math.min(Math.max(tick, 0), lines.length - 1)];
}
