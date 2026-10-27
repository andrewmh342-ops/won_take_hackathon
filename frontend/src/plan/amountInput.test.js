import test from 'node:test';
import assert from 'node:assert/strict';
import { amountMessage } from './amountInput.js';

test('빈 대상별 원 단위 금액을 한 번의 대화로 전달한다', () => {
  assert.equal(amountMessage(['집주인', '엄마'], { 집주인: '650000', 엄마: '300000' }), '집주인에게 650000원, 엄마에게 300000원');
  assert.equal(amountMessage(['집주인'], { 집주인: '9223372036854775807' }), '집주인에게 9223372036854775807원');
});

test('빈 값, 0, 음수, 소수와 long 범위 밖 금액은 전송하지 않는다', () => {
  for (const value of ['', '0', '-1', '1.5', 'abc', '9223372036854775808']) {
    assert.equal(amountMessage(['집주인'], { 집주인: value }), null);
  }
  assert.equal(amountMessage(['집주인', '엄마'], { 집주인: '650000' }), null);
  assert.equal(amountMessage([], {}), null);
});
