import test from 'node:test';
import assert from 'node:assert/strict';
import { planSteps, currentStepIndex, planChanges, goalWarnings, finishState, optionalBlocks, dateChips, actionLabel, stepLabel } from './planSteps.js';

const block = (index, blockId, extra = {}) => ({
  index, blockId, name: blockId, type: 'ACTION', menuId: '', targetRef: '', target: '', param: '', reason: '',
  status: 'PENDING', priority: 'REQUIRED', adjusted: false, detail: '', items: [], ...extra,
});
const plan = (blocks, extra = {}) => ({
  planId: 'p', events: ['카드 분실'], blocks, goals: [], goalStatus: [], complete: false, remaining: [],
  needsCardConfirm: false, suggestedCards: [], confirmedCards: null, ...extra,
});

test('탭은 필수 블록만 만들고 분실 카드 확인과 마무리를 앞뒤에 둔다', () => {
  const p = plan([block(0, 'card-lost-report', { targetRef: 'card-1' }), block(1, 'travel-insurance-guide', { priority: 'OPTIONAL', type: 'LINK' }), block(2, 'card-reissue', { targetRef: 'card-1' })],
    { needsCardConfirm: true, suggestedCards: ['card-1'] });
  assert.deepEqual(planSteps(p).map((s) => s.kind), ['CONFIRM', 'BLOCK', 'BLOCK', 'FINISH']);
  assert.deepEqual(planSteps(p).map((s) => s.label), ['카드 확인', '카드 정지', '재발급', '마무리']);
  assert.deepEqual(optionalBlocks(p).map((b) => b.blockId), ['travel-insurance-guide']);
});

test('확인 전에는 확인 단계, 확인 뒤에는 끝나지 않은 첫 필수 블록이 지금 단계다', () => {
  const blocks = [block(0, 'card-lost-report', { targetRef: 'card-1' }), block(1, 'card-reissue', { targetRef: 'card-1' })];
  assert.equal(currentStepIndex(plan(blocks, { needsCardConfirm: true })), 0);
  const confirmed = plan([{ ...blocks[0], status: 'DONE' }, blocks[1]], { confirmedCards: ['card-1'] });
  const steps = planSteps(confirmed);
  assert.equal(steps[0].kind, 'CONFIRM');
  assert.equal(steps[0].done, true);
  assert.equal(steps[currentStepIndex(confirmed)].block.blockId, 'card-reissue');
});

test('필수 블록을 모두 끝내면 마무리가 지금 단계다', () => {
  const p = plan([block(0, 'fx-rate-check', { type: 'CHECK', status: 'DONE', param: 'JPY' }), block(1, 'fx-exchange-apply', { priority: 'OPTIONAL', type: 'LINK', param: 'JPY' })]);
  const steps = planSteps(p);
  assert.equal(steps[currentStepIndex(p)].kind, 'FINISH');
});

test('재판단 뒤 늘어난 할 일 수와 새 탭을 알려 준다', () => {
  const before = plan([block(0, 'card-lost-report', { targetRef: 'card-2' }), block(1, 'ask-info', { type: 'INPUT', param: 'DEPARTURE_DATE' })]);
  const after = plan([block(0, 'card-lost-report', { targetRef: 'card-2', status: 'DONE' }), block(1, 'ask-info', { type: 'INPUT', param: 'DEPARTURE_DATE', status: 'DONE' }),
    block(2, 'alt-payment-check', { type: 'CHECK', targetRef: 'card-1' }), block(3, 'fx-exchange-apply', { type: 'LINK', param: 'JPY' })]);
  const change = planChanges(before, after);
  assert.equal(change.added, 2);
  assert.equal(change.message, '할 일이 2개 늘었어요');
  assert.ok(change.newKeys.has('alt-payment-check@card-1@'));
  assert.equal(planChanges(after, after).message, '');
  assert.equal(planChanges(after, plan([after.blocks[0], after.blocks[1]])).message, '할 일이 2개 줄었어요');
});

test('확보되지 않은 필수 목표만 짧은 경고로 보여 준다', () => {
  const p = plan([], {
    goals: [{ type: 'PAYMENT_ABROAD', required: true }, { type: 'CASH_ABROAD', required: false }, { type: 'CARD_PROTECTION', required: true }],
    goalStatus: [{ type: 'PAYMENT_ABROAD', status: 'UNSECURED' }, { type: 'CASH_ABROAD', status: 'NOT_READY' }, { type: 'CARD_PROTECTION', status: 'SECURED' }],
  });
  assert.deepEqual(goalWarnings(p), ['해외에서 쓸 카드가 아직 없어요']);
  const setup = plan([], { goals: [{ type: 'PAYMENT_ABROAD', required: true }], goalStatus: [{ type: 'PAYMENT_ABROAD', status: 'NEEDS_SETUP' }] });
  assert.deepEqual(goalWarnings(setup), ['해외결제를 켜야 해요']);
});

test('마무리 상태: 완료 / 남은 문제 / 진행 중', () => {
  const done = block(0, 'card-lost-report', { status: 'DONE' });
  assert.equal(finishState(plan([done], { complete: true })), 'COMPLETE');
  assert.equal(finishState(plan([done], { remaining: ['해외 결제 수단 — 미확보'] })), 'REMAINING');
  assert.equal(finishState(plan([block(0, 'card-lost-report')])), 'IN_PROGRESS');
});

test('출국일 칩은 오늘·내일·일주일 뒤', () => {
  assert.deepEqual(dateChips('2026-10-27'), [
    { label: '오늘', value: '2026-10-27' }, { label: '내일', value: '2026-10-28' }, { label: '일주일 뒤', value: '2026-11-03' },
  ]);
});

test('버튼과 탭 이름은 짧은 동사·명사로', () => {
  assert.equal(actionLabel(block(0, 'card-lost-report')), '카드 정지하기');
  assert.equal(actionLabel(block(0, 'counselor-handoff', { type: 'HANDOFF' })), '상담사 연결하기');
  assert.equal(stepLabel(block(0, 'ask-info', { param: 'DEPARTURE_DATE' })), '출국일');
  assert.equal(stepLabel(block(0, 'ask-info', { param: 'COUNTRY' })), '여행 나라');
});
