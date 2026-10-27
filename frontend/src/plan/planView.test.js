import test from 'node:test';
import assert from 'node:assert/strict';
import { won, riskBadge, taskNote, blockerNotes, hasWarning, confirmedNote, confirmError } from './planView.js';

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

test('주의 작업이 하나라도 있으면 승인 전에 경고한다', () => {
  assert.equal(hasWarning([{ risk: 'MEDIUM' }, { risk: 'HIGH' }]), true);
  assert.equal(hasWarning([{ risk: 'MEDIUM' }, { risk: 'MEDIUM_LOW' }]), false);
  assert.equal(hasWarning([{ risk: 'UNKNOWN' }]), true);
});

test('승인 결과는 보낸 돈과 남은 잔액을 원 단위로 알려 준다', () => {
  assert.equal(
    confirmedNote({ totalAmount: 950000, balanceAfter: 1500000 }),
    '승인했어요. 가상 생활통장에서 950,000원이 빠져 1,500,000원 남았어요.',
  );
});

test('승인이 막히면 카드와 같은 문구로, 그 밖의 오류는 서버 문장으로 보여 준다', () => {
  assert.equal(confirmError({ code: 'NEEDS_AMOUNT', message: '서버 문장' }), '빈 금액을 먼저 채워야 해요');
  assert.equal(confirmError({ code: 'PASSWORD_MISMATCH', message: '비밀번호가 맞지 않아요.' }), '비밀번호가 맞지 않아요.');
  assert.equal(confirmError(new Error('연결 실패')), '연결 실패');
});
