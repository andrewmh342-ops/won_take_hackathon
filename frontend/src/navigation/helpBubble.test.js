import test from 'node:test';
import assert from 'node:assert/strict';
import { canShowHelpBubble, hasUnfinishedPlan, endPlan } from './helpBubble.js';

test('열어 본 플랜 중 하나라도 안 끝났으면 진행 중이다', () => {
  assert.equal(hasUnfinishedPlan(new Map()), false);
  assert.equal(hasUnfinishedPlan(new Map([['p1', true]])), false);
  assert.equal(hasUnfinishedPlan(new Map([['p1', true], ['p2', false]])), true);
});

test('플랜을 한 번도 열지 않았으면 일반 화면에서 말풍선을 띄운다', () => {
  assert.equal(canShowHelpBubble('transfer', { menuId: 'instant-transfer' }, false), true);
});

test('플랜 진행 중에는 플랜에서 연 화면이든 다른 메뉴든 말풍선을 띄우지 않는다', () => {
  assert.equal(canShowHelpBubble('subscription', { planId: 'p1' }, true), false);
  assert.equal(canShowHelpBubble('transfer', { menuId: 'instant-transfer' }, true), false);
});

test('플랜을 마친 뒤에는 화면과 상관없이 원래 규칙대로 말풍선을 띄운다', () => {
  assert.equal(canShowHelpBubble('transfer', { menuId: 'instant-transfer' }, false), true);
  assert.equal(canShowHelpBubble('subscription', { planId: 'p1' }, false), true);
});

test('원테이크 화면("WON TAKE로 대응을 마쳤어요!" 포함)에서는 플랜을 마쳤어도 말풍선을 띄우지 않는다', () => {
  assert.equal(canShowHelpBubble('plan', { planId: 'p1' }, false), false);
});

test('[아니요, 끝낼게요]로 플랜을 끝내면 끝난 플랜으로 친다', () => {
  const plans = new Map([['p1', false]]);
  endPlan(plans, 'p1');
  assert.equal(hasUnfinishedPlan(plans), false);
  assert.equal(canShowHelpBubble('home', {}, hasUnfinishedPlan(plans)), true);
});

test('대화·동의·상담사·로그인·스플래시 화면은 원래대로 말풍선을 띄우지 않는다', () => {
  for (const screen of ['chat', 'consent', 'agent', 'login', 'splash']) assert.equal(canShowHelpBubble(screen, {}, false), false);
});
