import test from 'node:test';
import assert from 'node:assert/strict';
import { won, riskBadge, taskNote, blockerNotes } from './planView.js';

test('금액은 원 단위로 쉼표를 넣어 보여 준다', () => {
  assert.equal(won(650000), '650,000원');
  assert.equal(won(0), '0원');
});

test('위험도 코드마다 짧은 이름과 강조 여부가 정해져 있다', () => {
  assert.deepEqual(riskBadge('HIGH'), { label: '주의', warn: true });
  assert.deepEqual(riskBadge('MEDIUM'), { label: '보통', warn: false });
  assert.deepEqual(riskBadge('MEDIUM_LOW'), { label: '낮음', warn: false });
});

test('모르는 위험도 코드는 숨기지 않고 코드 그대로 보여 준다', () => {
  assert.deepEqual(riskBadge('NEW_LEVEL'), { label: 'NEW_LEVEL', warn: true });
});

test('작업 설명은 상태가 먼저, 그다음 위험 사유다', () => {
  assert.equal(taskNote({ status: 'NEEDS_AMOUNT', riskReasons: ['FREQUENT_RECIPIENT'] }), '금액을 말씀하지 않았어요');
  assert.equal(taskNote({ status: 'NEEDS_ACCOUNT', riskReasons: ['NEW_RECIPIENT'] }), '주소록에 없는 받는 분이에요');
  assert.equal(taskNote({ status: 'READY', riskReasons: ['ABNORMAL_AMOUNT'] }), '평소 보낸 금액보다 훨씬 커요');
  assert.equal(taskNote({ status: 'READY', riskReasons: ['FREQUENT_RECIPIENT'] }), '자주 보낸 분이에요');
  assert.equal(taskNote({ status: 'READY', riskReasons: ['OWN_ACCOUNT'] }), '내 계좌로 옮겨요');
});

test('위험 사유가 비어 오면 빈 문구로 넘기지 않고 드러낸다', () => {
  assert.equal(taskNote({ status: 'READY', riskReasons: [] }), '위험 사유를 받지 못했어요');
});

test('승인을 막는 이유는 서버가 준 순서대로 문장으로 바꾼다', () => {
  assert.deepEqual(blockerNotes(['NEEDS_ACCOUNT', 'INSUFFICIENT_BALANCE']), [
    '받는 분 계좌를 먼저 확인해야 해요',
    '잔액보다 보낼 돈이 많아요',
  ]);
  assert.deepEqual(blockerNotes([]), []);
});
