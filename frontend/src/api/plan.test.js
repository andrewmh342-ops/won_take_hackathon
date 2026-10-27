import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildMockPlan, executeMockPlan, confirmMockPlan, answerMockPlan } from './mockPlan.js';
import { menu } from '../data/menu.js';

const blocks = JSON.parse(readFileSync(new URL('../../../backend/src/main/resources/data/blocks.json', import.meta.url), 'utf8'));
// 백엔드 Plan·PlanBlock 공개 필드와 같아야 화면이 두 모드에서 똑같이 그려진다.
const PLAN_KEYS = ['aiInput', 'blocks', 'complete', 'confirmedCards', 'considered', 'events', 'goalStatus', 'goals', 'needsCardConfirm',
  'notice', 'obstacles', 'orderReason', 'planId', 'remaining', 'skipped', 'suggestedCards', 'unhandled'];
const BLOCK_KEYS = ['adjusted', 'blockId', 'detail', 'index', 'items', 'menuId', 'name', 'param', 'priority', 'reason', 'status', 'target', 'targetRef', 'type'];

test('모의 플랜 모양이 백엔드 응답과 같다', () => {
  for (const text of ['카드 잃어버렸어', '일본 여행 가요']) {
    const plan = buildMockPlan(text);
    assert.deepEqual(Object.keys(plan).sort(), PLAN_KEYS);
    for (const item of plan.blocks) {
      assert.deepEqual(Object.keys(item).sort(), BLOCK_KEYS);
      const known = blocks.find((b) => b.id === item.blockId);
      assert.ok(known, item.blockId);
      assert.equal(item.type, known.type);
      if (known.menuId) assert.ok(menu.some((m) => m.id === item.menuId), item.menuId);
    }
  }
  assert.equal(buildMockPlan('오늘 날씨'), null);
});

test('백엔드 Plan 공개 필드가 모의와 같다', () => {
  const source = readFileSync(new URL('../../../backend/src/main/java/com/wontake/assist/Plan.java', import.meta.url), 'utf8');
  const outer = source.slice(source.lastIndexOf('}', source.indexOf('public String planId')));
  const fields = [...outer.matchAll(/^\s+public (?!static|class)[\w.<>, ]+? (\w+)(?: = [^;]+)?;/gm)].map((m) => m[1]);
  assert.deepEqual(fields.sort(), PLAN_KEYS);
});

test('블록 사전의 메뉴는 모두 실제 화면으로 이동할 수 있다', () => {
  for (const item of blocks) if (item.menuId) assert.ok(menu.some((m) => m.id === item.menuId), item.menuId);
});

test('모의 실행도 카드 확인 전과 분실신고 전 재발급을 막는다', () => {
  const plan = buildMockPlan('카드 잃어버렸어');
  assert.throws(() => executeMockPlan(plan, 0), /잃어버린 카드를 확인/);
  const confirmed = confirmMockPlan(plan, ['card-1']);
  assert.throws(() => executeMockPlan(confirmed, 1), /분실신고/);
  const done = executeMockPlan(executeMockPlan(confirmed, 0), 1);
  assert.deepEqual(done.blocks.map((b) => b.status), ['DONE', 'DONE']);
  assert.equal(done.complete, true);
});

test('모의 출국일은 과거를 막고 오늘부터 받는다', () => {
  const plan = buildMockPlan('일본 여행 가요');
  assert.throws(() => answerMockPlan(plan, 0, '2026-10-26', '2026-10-27'), /1년 안/);
  assert.throws(() => answerMockPlan(plan, 0, '내일', '2026-10-27'), /1년 안/);
  const next = answerMockPlan(plan, 0, '2026-10-28', '2026-10-27');
  assert.equal(next.blocks[0].status, 'DONE');
  assert.equal(next.complete, false); // 필수 환율 확인이 남음
});
