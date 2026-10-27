// 담당: 공용 — 원테이크 화면의 단계 계산(SPEC R5 플랜 화면). 화면과 분리해 Node 테스트로 확인한다.

const LOSS = ['card-lost-report', 'simple-pay-unlink', 'card-reissue', 'auto-payment-switch'];

/** 탭에 쓰는 짧은 이름. */
const TAB = {
  'card-lost-report': '카드 정지', 'simple-pay-unlink': '간편결제', 'card-reissue': '재발급', 'auto-payment-switch': '자동결제',
  'overseas-payment-check': '해외결제', 'alt-payment-check': '다른 카드', 'fx-rate-check': '환율', 'fx-exchange-apply': '환전',
  'travel-card-check': '트래블카드', 'travel-card-apply': '카드 신청', 'travel-insurance-guide': '보험', 'counselor-handoff': '상담',
};
/** 단계 카드의 주 버튼. */
const ACTION = {
  'card-lost-report': '카드 정지하기', 'simple-pay-unlink': '간편결제 해제하기', 'card-reissue': '재발급 신청하기',
  'auto-payment-switch': '자동결제 옮기기', 'overseas-payment-check': '해외결제 확인하기', 'alt-payment-check': '이 카드 확인하기',
  'fx-rate-check': '환율 보기', 'fx-exchange-apply': '환전 신청하기', 'travel-card-check': '트래블카드 보기',
  'travel-card-apply': '트래블카드 신청하기', 'travel-insurance-guide': '보험 안내 보기', 'counselor-handoff': '상담사 연결하기',
};
/** 단계 카드의 큰 제목(고객에게 하는 말). */
const TITLE = {
  'card-lost-report': '먼저 카드를 정지할게요', 'simple-pay-unlink': '휴대폰 결제도 막아요', 'card-reissue': '새 카드를 신청해요',
  'auto-payment-switch': '자동결제를 옮겨요', 'overseas-payment-check': '해외에서 결제되게 해요', 'alt-payment-check': '이 카드는 해외에서 쓸 수 있을까요?',
  'fx-rate-check': '환율을 확인해요', 'fx-exchange-apply': '현지 돈을 준비해요', 'travel-card-check': '트래블카드를 확인해요',
  'travel-card-apply': '트래블카드를 신청해요', 'travel-insurance-guide': '여행자 보험을 알아봐요', 'counselor-handoff': '상담사가 이어서 도와드려요',
};
const WARN = {
  PAYMENT_ABROAD: { UNSECURED: '해외에서 쓸 카드가 아직 없어요', NEEDS_SETUP: '해외결제를 켜야 해요' },
  CARD_PROTECTION: { PENDING: '아직 정지하지 않은 카드가 있어요' },
  CASH_ABROAD: { NOT_READY: '현지 돈이 아직 준비되지 않았어요' },
};

export const isLoss = (block) => LOSS.includes(block.blockId);
export const blockKey = (block) => `${block.blockId}@${block.targetRef}@${block.param}`;
export const required = (plan) => plan.blocks.filter((block) => block.priority !== 'OPTIONAL');
export const optionalBlocks = (plan) => plan.blocks.filter((block) => block.priority === 'OPTIONAL');

export function stepLabel(block) {
  if (block.blockId === 'ask-info') return block.param === 'COUNTRY' ? '여행 나라' : '출국일';
  return TAB[block.blockId] ?? block.name;
}
export function stepTitle(block) {
  if (block.blockId === 'ask-info') return block.param === 'COUNTRY' ? '어느 나라로 가세요?' : '언제 출국하세요?';
  return TITLE[block.blockId] ?? block.name;
}
export const actionLabel = (block) => ACTION[block.blockId] ?? '화면 열기';

/** 탭 목록: [분실 카드 확인] + 필수 블록 + [마무리]. 선택 블록은 마무리 카드에서 보여 준다. */
export function planSteps(plan) {
  const steps = [];
  if (plan.needsCardConfirm || plan.confirmedCards)
    steps.push({ key: 'confirm', kind: 'CONFIRM', label: '카드 확인', done: !plan.needsCardConfirm });
  for (const block of required(plan))
    steps.push({ key: blockKey(block), kind: 'BLOCK', label: stepLabel(block), done: block.status !== 'PENDING', block });
  steps.push({ key: 'finish', kind: 'FINISH', label: '마무리', done: finishState(plan) === 'COMPLETE' });
  return steps;
}

/** 지금 할 단계: 확인 전이면 확인, 아니면 끝나지 않은 첫 필수 블록, 다 끝났으면 마무리. */
export function currentStepIndex(plan) {
  const steps = planSteps(plan);
  if (plan.needsCardConfirm) return 0;
  const next = steps.findIndex((step) => step.kind === 'BLOCK' && !step.done);
  return next >= 0 ? next : steps.length - 1;
}

/** 재판단 전후 필수 할 일 변화(고객에게 한 줄로 알린다). */
export function planChanges(before, after) {
  const was = new Set(required(before).map(blockKey));
  const now = required(after).map(blockKey);
  const newKeys = new Set(now.filter((key) => !was.has(key)));
  const removed = [...was].filter((key) => !now.includes(key)).length;
  const added = newKeys.size;
  const message = added > 0 ? `할 일이 ${added}개 늘었어요` : removed > 0 ? `할 일이 ${removed}개 줄었어요` : '';
  return { added, removed, newKeys, message };
}

/** 확보되지 않은 필수 목표만 짧은 경고로. */
export function goalWarnings(plan) {
  const need = new Set((plan.goals ?? []).filter((goal) => goal.required).map((goal) => goal.type));
  return (plan.goalStatus ?? []).filter((goal) => need.has(goal.type)).map((goal) => WARN[goal.type]?.[goal.status]).filter(Boolean);
}

export function finishState(plan) {
  if (plan.needsCardConfirm || required(plan).some((block) => block.status === 'PENDING')) return 'IN_PROGRESS';
  if (plan.complete) return 'COMPLETE';
  return (plan.remaining ?? []).length > 0 ? 'REMAINING' : 'COMPLETE';
}

function addDays(date, days) {
  const next = new Date(`${date}T00:00:00`);
  next.setDate(next.getDate() + days);
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-${String(next.getDate()).padStart(2, '0')}`;
}
export const maxDate = (today) => addDays(today, 365);
export const dateChips = (today) => [
  { label: '오늘', value: today }, { label: '내일', value: addDays(today, 1) }, { label: '일주일 뒤', value: addDays(today, 7) },
];
