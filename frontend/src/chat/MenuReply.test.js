import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
import { openReplyMenu } from './menuNavigation.js';

test('확인 응답은 단일 후보라도 자동 이동하지 않는다', () => {
  assert.equal(openReplyMenu({ type: 'CONFIRM_MENU', menuIds: ['transfer-history'] }, () => assert.fail('확인 전에 이동함')), false);
});

test('확인할 메뉴의 정식 이름과 선택 버튼을 보여 준다', async () => {
  const server = await createServer({ configFile: false, server: { middlewareMode: true, hmr: false, ws: false } });
  try {
    const { default: MenuReply } = await server.ssrLoadModule('/src/chat/MenuReply.jsx');
    const render = (reply) => renderToStaticMarkup(React.createElement(MenuReply, { reply, navigate() {} }));
    const confirm = render({ type: 'CONFIRM_MENU', menuIds: ['transfer-history'] });
    assert.match(confirm, /이체내역조회 맞아요, 열기/);
    assert.match(confirm, /data-track-id="chat-menu-transfer-history"/);
    assert.match(render({ type: 'ANSWER', menuIds: ['transaction-history'] }), /거래내역조회 화면 보기/);
    assert.doesNotMatch(render({ type: 'CONFIRM_MENU', menuIds: ['unknown'] }), /<button/);
  } finally {
    await server.close();
  }
});
