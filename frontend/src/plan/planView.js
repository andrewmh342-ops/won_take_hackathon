// 담당: 역할 4 — 한 번에 맡기기(/api/chat 답의 plan 필드) 코드를 화면 문구로 바꾸는 규칙
// 코드의 주인은 백엔드다(plan/PlanService, plan/RiskRules). 백엔드에 코드가 늘면 여기 문구를 같이 고친다.
export const won = (amount) => `${amount.toLocaleString('ko-KR')}원`;

const RISK = {
  HIGH: { label: '주의', warn: true },
  MEDIUM: { label: '보통', warn: false },
  MEDIUM_LOW: { label: '낮음', warn: false },
};
// 모르는 코드는 기본값으로 숨기지 않고 코드 그대로 경고로 보여 준다.
export const riskBadge = (level) => RISK[level] ?? { label: level, warn: true };

const STATUS = {
  NEEDS_AMOUNT: '금액을 말씀하지 않았어요',
  NEEDS_ACCOUNT: '주소록에 없는 받는 분이에요',
};
const REASON = {
  ABNORMAL_AMOUNT: '평소 보낸 금액보다 훨씬 커요',
  NEW_RECIPIENT: '처음 보내는 분이에요',
  FREQUENT_RECIPIENT: '자주 보낸 분이에요',
  OWN_ACCOUNT: '내 계좌로 옮겨요',
};
// 사용자가 먼저 해결할 일(상태)을 위험 사유보다 앞에 보여 준다.
export function taskNote({ status, riskReasons }) {
  if (STATUS[status]) return STATUS[status];
  // RiskRules는 사유를 늘 하나 이상 준다. 비어 오면 계약이 깨진 것이라 빈 문구로 넘기지 않는다.
  const reason = riskReasons[0];
  if (reason === undefined) return '위험 사유를 받지 못했어요';
  return REASON[reason] ?? reason;
}

const BLOCKER = {
  NEEDS_ACCOUNT: '받는 분 계좌를 먼저 확인해야 해요',
  NEEDS_AMOUNT: '빈 금액을 먼저 채워야 해요',
  INSUFFICIENT_BALANCE: '잔액보다 보낼 돈이 많아요',
};
export const blockerNotes = (blockers) => blockers.map((code) => BLOCKER[code] ?? code);
