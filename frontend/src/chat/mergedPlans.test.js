import test from 'node:test';
import assert from 'node:assert/strict';
import { openReplyMenu, menuReplyContent } from './menuNavigation.js';

test('업무 플랜과 이체 목록은 단일 메뉴를 포함해도 자동 이동하거나 설명을 바꾸지 않는다', () => {
  for (const plans of [{ plan: { blocks: [] } }, { transferPlan: { tasks: [] } }]) {
    const reply = { type: 'ANSWER', menuIds: ['instant-transfer'], text: '목록을 확인해 주세요.', steps: [], ...plans };
    assert.equal(openReplyMenu(reply, () => assert.fail('플랜에서 자동 이동하면 안 됨')), false);
    assert.equal(menuReplyContent(reply).text, reply.text);
  }
});
