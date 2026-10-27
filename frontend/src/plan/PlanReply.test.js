import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

test('빈 금액 질문은 카드 뒤에 한 번 표시하고 완성된 목록의 안내는 앞에 표시한다', async () => {
  const previousStorage = globalThis.localStorage;
  globalThis.localStorage = { getItem: () => null };
  const server = await createServer({ configFile: false, server: { middlewareMode: true, hmr: false } });
  try {
    const { default: PlanReply } = await server.ssrLoadModule('/src/plan/PlanReply.jsx');
    const text = '서버가 보낸 금액 질문';
    const task = { order: 1, target: '집주인', amount: 0, status: 'NEEDS_AMOUNT', risk: 'MEDIUM', riskReasons: ['FREQUENT_RECIPIENT'] };
    const plan = { tasks: [task], blockers: ['NEEDS_AMOUNT'], totalAmount: 0, expectedBalance: 2450000, canConfirm: false };
    const pending = renderToStaticMarkup(React.createElement(PlanReply, { text, plan, onFill: () => {} }));
    assert.ok(pending.indexOf(text) > pending.indexOf('</section>'));
    assert.equal(pending.split(text).length - 1, 1);
    assert.match(pending, /집주인 이체 금액\(원\)/);
    assert.match(pending, /inputMode="numeric"/);
    assert.match(pending, /금액 입력/);
    assert.match(pending, /<button[^>]*disabled/);
    const old = renderToStaticMarkup(React.createElement(PlanReply, { text, plan, onFill: () => {}, active: false }));
    assert.doesNotMatch(old, /<form/);
    const ready = renderToStaticMarkup(React.createElement(PlanReply, {
      text, plan: { ...plan, tasks: [{ ...task, amount: 650000, status: 'READY' }], blockers: [] },
    }));
    assert.ok(ready.indexOf(text) < ready.indexOf('class="transfer-plan-card"'));
    assert.doesNotMatch(ready, /이체 금액\(원\)/);
    assert.equal(renderToStaticMarkup(React.createElement(PlanReply, { text })), `<p>${text}</p>`);
  } finally {
    await server.close();
    if (previousStorage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = previousStorage;
  }
});
