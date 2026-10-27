import test from 'node:test';
import assert from 'node:assert/strict';
import { menu, categories } from './menu.js';
import { CATEGORY_EMOJI, MENU_EMOJI, menuEmoji, TAB_EMOJI } from './emoji.js';

test('모든 메뉴에 이모지가 있고 메뉴끼리 겹치지 않는다', () => {
  for (const item of menu) assert.ok(MENU_EMOJI[item.id], `${item.id} 이모지 없음`);
  const used = menu.map((item) => MENU_EMOJI[item.id]);
  assert.equal(new Set(used).size, used.length);
});

test('모든 분류에 이모지가 있다', () => {
  for (const category of categories) assert.ok(CATEGORY_EMOJI[category], `${category} 이모지 없음`);
});

test('홈 빠른 메뉴와 전체 메뉴가 같은 이모지를 쓴다', () => {
  assert.equal(menuEmoji('overseas-remittance'), MENU_EMOJI['overseas-remittance']);
  assert.equal(menuEmoji('없는-메뉴'), '•');
});

test('하단 탭 5개에 이모지가 있다', () => {
  assert.deepEqual(Object.keys(TAB_EMOJI), ['상품', '자산소비', '홈', '주식', '혜택']);
});
