import test from 'node:test';
import assert from 'node:assert/strict';
import { menu } from '../data/menu.js';
import { menuReplyContent } from './menuNavigation.js';

test('자동 이동하는 모든 메뉴는 긴 가이드 대신 정식 이름의 한 문장만 보여 준다', () => {
  for (const item of menu) {
    const content = menuReplyContent({ type: 'ANSWER', menuIds: [item.id], text: '보고 싶으시군요. 계좌를 고르고 기간을 정해 보세요.', steps: ['계좌 선택', '기간 선택'] });
    assert.deepEqual(content, { text: `${item.name} 화면을 열어 드릴게요.`, steps: [] });
  }
});

test('복원한 구형 단일 메뉴 응답도 같은 문구로 표시하며 이동은 실행하지 않는다', () => {
  const message = { role: 'ASSISTANT', type: 'ANSWER', menuId: 'transfer-history', text: '이체내역을 보고 싶으시군요. 다음 순서로 확인하세요.', steps: ['계좌 선택'] };
  assert.deepEqual(menuReplyContent(message), { text: '이체내역조회 화면을 열어 드릴게요.', steps: [] });
  assert.equal(message.text, '이체내역을 보고 싶으시군요. 다음 순서로 확인하세요.');
  assert.deepEqual(message.steps, ['계좌 선택']);
});

test('확인 질문과 상담·작업 카드·찾지 못함·잘못된 메뉴의 안내는 보존한다', () => {
  for (const reply of [
    { type: 'CONFIRM_MENU', menuIds: ['transfer-history'] },
    { type: 'ANSWER', menuIds: ['transfer-history', 'transaction-history'] },
    { type: 'ANSWER', menuIds: ['transfer-history'], handoffSuggested: true },
    { type: 'PLAN', plan: {}, menuIds: [] },
    { type: 'NOT_FOUND', menuIds: [] },
    { type: 'ANSWER', menuIds: ['unknown'] },
  ]) {
    assert.deepEqual(menuReplyContent({ ...reply, text: '유지할 안내', steps: ['필요한 도움'] }), { text: '유지할 안내', steps: ['필요한 도움'] });
  }
});

test('사용자가 입력한 문장과 단계가 없는 응답은 그대로 표시한다', () => {
  assert.deepEqual(menuReplyContent({ role: 'USER', text: '최근 이체내역 보여줘' }), { text: '최근 이체내역 보여줘', steps: [] });
});
