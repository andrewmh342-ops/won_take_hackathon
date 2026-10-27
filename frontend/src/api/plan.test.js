import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildMockPlan, executeMockPlan } from './mockPlan.js';
import { menu } from '../data/menu.js';

const blocks = JSON.parse(readFileSync(new URL('../../../backend/src/main/resources/data/blocks.json', import.meta.url), 'utf8'));
// 백엔드 Plan·PlanBlock 공개 필드와 같아야 화면이 두 모드에서 똑같이 그려진다.
const PLAN_KEYS = ['aiInput', 'blocks', 'events', 'orderReason', 'planId', 'unhandled'];
const BLOCK_KEYS = ['adjusted', 'blockId', 'detail', 'index', 'items', 'menuId', 'name', 'param', 'reason', 'status', 'target', 'targetRef', 'type'];

test('모의 플랜 모양이 백엔드 응답과 같다', () => {
  for (const text of ['카드 잃어버렸어', '일본 여행 가요']) {
    const plan = buildMockPlan(text);
    assert.deepEqual(Object.keys(plan).sort(), PLAN_KEYS);
    for (const item of plan.blocks) {
      assert.deepEqual(Object.keys(item).sort(), BLOCK_KEYS);
      const known = blocks.find((b) => b.id === item.blockId);
      assert.ok(known, item.blockId);
      assert.equal(item.type, known.type);
      assert.ok(menu.some((m) => m.id === item.menuId), item.menuId);
    }
  }
  assert.equal(buildMockPlan('오늘 날씨'), null);
});

test('블록 사전의 메뉴는 모두 실제 화면으로 이동할 수 있다', () => {
  for (const item of blocks) if (item.menuId) assert.ok(menu.some((m) => m.id === item.menuId), item.menuId);
});

test('모의 실행도 분실신고 전 재발급을 막는다', () => {
  const plan = buildMockPlan('카드 잃어버렸어');
  assert.throws(() => executeMockPlan(plan, 1), /분실신고/);
  const done = executeMockPlan(executeMockPlan(plan, 0), 1);
  assert.deepEqual(done.blocks.map((b) => b.status), ['DONE', 'DONE']);
});
