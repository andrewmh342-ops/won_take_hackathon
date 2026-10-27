import test from 'node:test';
import assert from 'node:assert/strict';
import { toKrw, overLimit, EXCHANGE_LIMIT_KRW } from './fx.js';

const JPY = { unit: 100, krw: 905 };

test('원화 환산은 원 미만을 절사한다 (백엔드와 같은 경계값)', () => {
  assert.equal(toKrw(15000, JPY), 135750);
  assert.equal(toKrw(15001, JPY), 135759);
  assert.equal(toKrw(1449, { unit: 1, krw: 1380 }), 1999620);
});

test('1회 환전 한도는 200만 원이다', () => {
  assert.equal(EXCHANGE_LIMIT_KRW, 2000000);
  assert.equal(overLimit(2000000), false);
  assert.equal(overLimit(2000001), true);
});

test('외화 금액은 1 이상 정수만 받는다', () => {
  assert.equal(toKrw(0, JPY), null);
  assert.equal(toKrw(-1, JPY), null);
  assert.equal(toKrw(1.5, JPY), null);
});

// 작업 37: 플랜에서 들어온 환율 화면은 환전 신청을 따로 띄우지 않는다(같은 통화의 환전 단계와 겹치지 않게)
test('플랜에서_들어온_환율_화면은_다음_단계로', async () => {
  const { rateScreenAction } = await import('./fx.js');
  assert.equal(rateScreenAction({ planId: 'p-1' }), 'NEXT');
  assert.equal(rateScreenAction({ menuId: 'exchange-rate' }), 'EXCHANGE');
  assert.equal(rateScreenAction(undefined), 'EXCHANGE');
});
