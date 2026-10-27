// 담당: 공용 — R5 원테이크 플랜의 프런트 전용 모의 응답 (VITE_USE_MOCK=true). 백엔드 Plan 응답과 같은 모양을 쓴다.
const card = { ref: 'card-1', display: '우리 신용카드 끝자리 4821(가상)' };
const block = (index, blockId, name, type, menuId, extra = {}) => ({
  index, blockId, name, type, menuId, targetRef: '', target: '', param: '', reason: '(가짜) 모의 플랜',
  status: 'PENDING', adjusted: false, detail: '', items: [], ...extra,
});

export function buildMockPlan(text) {
  const blocks = [];
  const events = [];
  if (/잃어버|잃었|분실|도난/.test(text)) {
    events.push('카드 분실');
    const target = { targetRef: card.ref, target: card.display };
    blocks.push(block(0, 'card-lost-report', '카드 분실신고', 'ACTION', 'card-loss-report', { ...target, detail: '사용 중' }));
    blocks.push(block(1, 'card-reissue', '카드 재발급', 'ACTION', 'card-reissue', { ...target, detail: '아직 재발급 전이에요' }));
  } else if (/여행|출국/.test(text)) {
    events.push('해외여행 준비');
    blocks.push(block(0, 'fx-rate-check', '환율 확인', 'CHECK', 'exchange-rate', { param: 'JPY', detail: '100 JPY(일본 엔) = 905원 · 시연용 가상 환율' }));
    blocks.push(block(1, 'travel-insurance-guide', '여행자 보험 가입 안내', 'LINK', 'travel-insurance', { detail: '가입 안내 화면으로 이동해요(상품 추천 없음)' }));
  }
  if (blocks.length === 0) return null;
  return {
    planId: `mock-${Date.now()}`,
    events,
    orderReason: '(가짜) 급한 일부터 배치했어요.',
    blocks,
    unhandled: [],
    aiInput: { cards: [{ ref: card.ref, kind: '신용' }], hasTravelCard: false, currencies: ['JPY'] },
  };
}

export function executeMockPlan(plan, index) {
  const blocks = plan.blocks.map((item) => ({ ...item }));
  const target = blocks[index];
  if (target.blockId === 'card-reissue' && blocks.some((item) => item.blockId === 'card-lost-report' && item.status === 'PENDING')) {
    throw new Error('먼저 카드 분실신고를 끝내 주세요.');
  }
  target.status = ['LINK', 'HANDOFF'].includes(target.type) ? 'VISITED' : 'DONE';
  if (target.blockId === 'card-lost-report') target.detail = '사용 정지됨(가상)';
  if (target.blockId === 'card-reissue') target.detail = '재발급 신청됨(가상)';
  return { ...plan, blocks };
}
