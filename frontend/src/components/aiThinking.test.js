import test from 'node:test';
import assert from 'node:assert/strict';
import { THINKING, thinkingLine } from './aiThinking.js';

test('상황마다 AI가 말하는 문장이 2개 이상 있다', () => {
  for (const kind of ['confirm', 'answer', 'review', 'chat', 'load']) {
    assert.ok(THINKING[kind].length >= 2, `${kind} 문장 부족`);
  }
});

test('시간이 지나면 다음 문장으로 넘어가고 마지막 문장에서 멈춘다', () => {
  const lines = THINKING.confirm;
  assert.equal(thinkingLine('confirm', 0), lines[0]);
  assert.equal(thinkingLine('confirm', 1), lines[1]);
  assert.equal(thinkingLine('confirm', 99), lines.at(-1));
});

test('카드 확인·정보 입력은 SPEC 문구로 시작한다', () => {
  assert.equal(thinkingLine('confirm', 0), '할 일을 다시 정리하고 있어요');
  assert.equal(thinkingLine('answer', 0), '할 일을 다시 정리하고 있어요');
});

test('모르는 상황은 기본 문장을 쓴다', () => {
  assert.equal(thinkingLine('없는-상황', 0), THINKING.review[0]);
});
