import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { menu } from '../data/menu.js';

test('백엔드 AI 메뉴와 실제 프런트 이동 대상이 일치한다', () => {
  const backend = JSON.parse(readFileSync(new URL('../../../backend/src/main/resources/data/menu.json', import.meta.url), 'utf8'));
  const identities = (items) => items.map(({ id, category, name, screenId }) => ({ id, category, name, screenId }));
  assert.deepEqual(identities(backend), identities(menu));
  assert.equal(new Set(backend.map((item) => item.id)).size, 21);
});
