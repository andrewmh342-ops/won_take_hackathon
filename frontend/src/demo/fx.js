// 담당: 공용 — R5 환전 계산. 백엔드 DemoActionService.toKrw와 같은 규칙(원 미만 절사, 1회 200만 원).
export const EXCHANGE_LIMIT_KRW = 2000000;

/** 원화 = ⌊외화 × 환율 ÷ 기준 단위⌋. 외화는 1 이상 정수만, 아니면 null. */
export function toKrw(amount, rate) {
  if (!Number.isInteger(amount) || amount < 1 || !rate) return null;
  return Math.floor((amount * rate.krw) / rate.unit);
}

export const overLimit = (krw) => krw > EXCHANGE_LIMIT_KRW;

export const won = (value) => `${Number(value).toLocaleString('ko-KR')}원`;
