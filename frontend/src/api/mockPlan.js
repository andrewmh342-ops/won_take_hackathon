// 담당: 공용 — R5 원테이크 플랜의 프런트 전용 모의 응답 (VITE_USE_MOCK=true). 백엔드 Plan 응답과 같은 모양을 쓴다.
const card = { ref: 'card-1', display: '우리 신용카드 끝자리 4821(가상)' };
const LOSS = ['card-lost-report', 'simple-pay-unlink', 'card-reissue', 'auto-payment-switch'];
const block = (index, blockId, name, type, menuId, extra = {}) => ({
  index, blockId, name, type, menuId, targetRef: '', target: '', param: '', reason: '(가짜) 모의 플랜',
  status: 'PENDING', priority: 'REQUIRED', adjusted: false, detail: '', items: [], ...extra,
});

/** 블록 상태로 목표 상태·완료·남은 문제를 다시 계산한다(백엔드 refresh와 같은 뜻의 단순판). */
function refresh(plan) {
  const lostDone = plan.blocks.some((b) => b.blockId === 'card-lost-report' && b.status !== 'PENDING');
  const goalStatus = plan.goals.map((goal) => goal.type === 'CARD_PROTECTION'
    ? { type: goal.type, label: '분실 카드 보호', status: lostDone ? 'SECURED' : 'PENDING', detail: lostDone ? '잃어버린 카드를 모두 정지했어요' : '정지하지 않은 카드가 있어요' }
    : { type: goal.type, label: '해외 결제 수단', status: 'SECURED', detail: '확보: 우리 체크카드 끝자리 3356(가상)(해외결제 켜짐)' });
  const unsecured = goalStatus.filter((g) => g.status !== 'SECURED');
  const requiredDone = plan.blocks.every((b) => b.priority === 'OPTIONAL' || b.status !== 'PENDING');
  return {
    ...plan, goalStatus,
    complete: requiredDone && unsecured.length === 0,
    remaining: unsecured.map((g) => `${g.label} — ${g.detail}`),
  };
}

export function buildMockPlan(text) {
  const blocks = [];
  const events = [];
  const goals = [];
  const considered = [];
  let needsCardConfirm = false;
  if (/잃어버|잃었|분실|도난/.test(text)) {
    events.push('카드 분실');
    goals.push({ type: 'CARD_PROTECTION', text: '(가짜) 잃어버린 카드 부정 사용 막기', required: true });
    const target = { targetRef: card.ref, target: card.display };
    blocks.push(block(0, 'card-lost-report', '카드 분실신고', 'ACTION', 'card-loss-report', { ...target, detail: '사용 중' }));
    blocks.push(block(1, 'card-reissue', '카드 재발급', 'ACTION', 'card-reissue', { ...target, detail: '아직 재발급 전이에요' }));
    considered.push({ blockId: 'simple-pay-unlink', name: '간편결제 해제', reason: '(가짜) 간편결제에 등록되지 않은 카드예요.' });
    needsCardConfirm = true;
  } else if (/여행|출국/.test(text)) {
    events.push('해외여행 준비');
    goals.push({ type: 'PAYMENT_ABROAD', text: '(가짜) 여행지에서 쓸 결제 수단 확보', required: true });
    blocks.push(block(0, 'ask-info', '정보 확인', 'INPUT', '', { param: 'DEPARTURE_DATE', reason: '(가짜) 출국일에 따라 준비가 달라요.' }));
    blocks.push(block(1, 'fx-rate-check', '환율 확인', 'CHECK', 'exchange-rate', { param: 'JPY', detail: '100 JPY(일본 엔) = 905원 · 시연용 가상 환율' }));
    blocks.push(block(2, 'travel-insurance-guide', '여행자 보험 가입 안내', 'LINK', 'travel-insurance', { priority: 'OPTIONAL', detail: '가입 안내 화면으로 이동해요(상품 추천 없음)' }));
  }
  if (blocks.length === 0) return null;
  return refresh({
    planId: `mock-${Date.now()}`,
    events,
    orderReason: '(가짜) 급한 일부터 배치했어요.',
    blocks,
    unhandled: [],
    aiInput: { question: text, customer: { cards: [{ ref: card.ref, kind: '신용' }], hasTravelCard: false, currencies: ['JPY'] } },
    goals,
    goalStatus: [],
    obstacles: [],
    considered,
    skipped: [],
    complete: false,
    remaining: [],
    needsCardConfirm,
    suggestedCards: needsCardConfirm ? [card.ref] : [],
    confirmedCards: null,
    notice: '',
  });
}

export function executeMockPlan(plan, index) {
  const blocks = plan.blocks.map((item) => ({ ...item }));
  const target = blocks[index];
  if (plan.needsCardConfirm && LOSS.includes(target.blockId)) throw new Error('먼저 잃어버린 카드를 확인해 주세요.');
  if (target.blockId === 'card-reissue' && blocks.some((item) => item.blockId === 'card-lost-report' && item.status === 'PENDING')) {
    throw new Error('먼저 카드 분실신고를 끝내 주세요.');
  }
  target.status = ['LINK', 'HANDOFF'].includes(target.type) ? 'VISITED' : 'DONE';
  if (target.blockId === 'card-lost-report') target.detail = '사용 정지됨(가상)';
  if (target.blockId === 'card-reissue') target.detail = '재발급 신청됨(가상) · 받기 전에는 쓸 수 없어요';
  return refresh({ ...plan, blocks });
}

/** 작업 23 모의: 확인한 카드로 카드 블록을 고정한다(모의에서는 카드 한 장만 다룬다). */
export function confirmMockPlan(plan, refs) {
  if (!refs?.length) throw new Error('잃어버린 카드를 골라 주세요.');
  if (plan.confirmedCards) throw new Error('이미 잃어버린 카드를 확인했어요.');
  return refresh({ ...plan, needsCardConfirm: false, confirmedCards: [...refs] });
}

/** 작업 29 모의: 출국일은 오늘~365일 뒤만 받는다. */
export function answerMockPlan(plan, index, value, today = new Date().toISOString().slice(0, 10)) {
  const blocks = plan.blocks.map((item) => ({ ...item }));
  const target = blocks[index];
  if (!target || target.blockId !== 'ask-info') throw new Error('정보를 묻는 단계가 아니에요.');
  if (target.param === 'DEPARTURE_DATE') {
    const limit = new Date(`${today}T00:00:00`);
    limit.setDate(limit.getDate() + 365);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value < today || new Date(`${value}T00:00:00`) > limit)
      throw new Error('출국일은 오늘부터 1년 안으로 골라 주세요.');
  } else if (!value.trim() || value.trim().length > 20) throw new Error('나라 이름을 1~20자로 적어 주세요.');
  target.status = 'DONE';
  target.detail = `답한 내용: ${value}`;
  return refresh({ ...plan, blocks });
}
