import test from 'node:test';
import assert from 'node:assert/strict';
import { menu } from '../data/menu.js';
import { replyMenus, openReplyMenu, claimInitialQuestion } from './menuNavigation.js';

for (const item of menu) {
  test(`AI가 ${item.name} 하나를 고르면 해당 화면과 메뉴 ID로 이동한다`, () => {
    const calls = [];
    const opened = openReplyMenu({ type: 'ANSWER', menuIds: [item.id] }, (...args) => calls.push(args));
    assert.equal(opened, true);
    assert.deepEqual(calls, [[item.screenId, { menuId: item.id }]]);
  });
}

test('기존 단일 menuId 응답과 메뉴 버튼도 같은 카탈로그를 사용한다', () => {
  const calls = [];
  assert.equal(openReplyMenu({ type: 'ANSWER', menuId: 'transaction-history' }, (...args) => calls.push(args)), true);
  assert.deepEqual(calls, [['account', { menuId: 'transaction-history' }]]);
  assert.deepEqual(replyMenus({ menuIds: ['exchange-rate', 'exchange-apply'] }), menu.filter((item) => ['exchange-apply', 'exchange-rate'].includes(item.id)).reverse());
});

test('후보가 둘이거나 없으면 자동 이동하지 않고 후보 버튼을 남긴다', () => {
  const navigate = () => assert.fail('자동 이동하면 안 된다');
  assert.equal(openReplyMenu({ type: 'ANSWER', menuIds: ['transaction-history', 'transfer-history'] }, navigate), false);
  assert.equal(openReplyMenu({ type: 'ANSWER', menuIds: [] }, navigate), false);
  assert.equal(replyMenus({ menuIds: ['transaction-history', 'transfer-history'] }).length, 2);
});

test('작업 카드, 상담 권유, 범위 밖 안내와 찾지 못한 응답은 대화를 유지한다', () => {
  const navigate = () => assert.fail('대화를 유지해야 한다');
  for (const reply of [
    { type: 'PLAN', plan: {}, menuIds: ['instant-transfer'] },
    { type: 'ANSWER', plan: {}, menuIds: ['instant-transfer'] },
    { type: 'ANSWER', handoffSuggested: true, menuIds: ['device-manage'] },
    { type: 'OUT_OF_SCOPE', menuIds: ['instant-transfer'] },
    { type: 'NOT_FOUND', menuIds: ['instant-transfer'] },
  ]) assert.equal(openReplyMenu(reply, navigate), false);
});

test('없는 메뉴와 잘못된 후보 목록으로는 이동하지 않는다', () => {
  const navigate = () => assert.fail('잘못된 메뉴로 이동하면 안 된다');
  for (const menuIds of [['unknown'], ['transaction-history', 'unknown'], ['transaction-history', 'transaction-history']]) {
    assert.equal(openReplyMenu({ type: 'ANSWER', menuIds }, navigate), false);
  }
  assert.deepEqual(replyMenus({ menuIds: ['unknown'] }), []);
});

test('검색으로 진입한 대화는 뒤로 돌아와도 같은 요청을 다시 보내지 않는다', () => {
  const params = { question: '거래내역조회 하고싶어', autoSend: true };
  assert.equal(claimInitialQuestion(params), true);
  assert.equal(claimInitialQuestion(params), false);
  // 같은 질문이라도 사용자가 새로 검색한 화면 진입은 별도 요청이다.
  assert.equal(claimInitialQuestion({ ...params }), true);
});

test('일반 대화 진입은 자동 전송하지 않고 추천 질문도 진입당 한 번만 전송한다', () => {
  assert.equal(claimInitialQuestion({}), false);
  assert.equal(claimInitialQuestion({ question: '거래내역조회' }), false);
  const params = { question: '내 이체한도가 궁금해요', intentId: 'transfer-limit' };
  assert.equal(claimInitialQuestion(params), true);
  assert.equal(claimInitialQuestion(params), false);
});
