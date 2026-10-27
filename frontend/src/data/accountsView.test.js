import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

test('세 화면은 조회 결과의 잔액을 표시하고 로딩·실패 때 고정 잔액을 보여 주지 않는다', async () => {
  const storage = globalThis.localStorage;
  globalThis.localStorage = { getItem: () => null };
  const server = await createServer({ configFile: false, server: { middlewareMode: true, hmr: false, ws: false }, plugins: [{
    name: '계좌 화면 의존성 대역',
    enforce: 'pre',
    resolveId(source) {
      if (source.endsWith('/useAccounts.js')) return '\0test-accounts';
      if (source.endsWith('/navigation/index.jsx')) return '\0test-navigation';
      if (source.endsWith('/session/auth.js')) return '\0test-auth';
    },
    load(id) {
      if (id === '\0test-accounts') return 'export const useAccounts = () => globalThis.__accountViewState;';
      if (id === '\0test-navigation') return 'export const useNavigation = () => ({navigate(){}, goBack(){}});';
      if (id === '\0test-auth') return 'export const useAuth = () => ({name:"데모 고객"}); export function logout(){}';
    },
  }] });
  try {
    const data = { accounts: [
      { name: '서버 생활통장', number: 'DEMO-SERVER-01', balance: 123456, withdrawal: true },
      { name: '서버 적금', number: 'DEMO-SERVER-02', balance: 654321, withdrawal: false },
    ], totalBalance: 777777 };
    for (const [name, expected] of [['Home', '777,777'], ['Account', '123,456'], ['Transfer', '123,456']]) {
      const { default: Screen } = await server.ssrLoadModule(`/src/screens/${name}Screen.jsx`);
      const render = () => renderToStaticMarkup(React.createElement(Screen));
      globalThis.__accountViewState = { data, error: '', reload() {} };
      const ready = render();
      assert.ok(ready.includes(expected), `${name}: 서버 잔액 사용`);
      if (name === 'Account') assert.ok(ready.includes('654,321'));
      assert.doesNotMatch(ready, /2,450,000|8,250,000|5,800,000/);
      globalThis.__accountViewState = { data: null, error: '', reload() {} };
      assert.match(render(), /잔액을 불러오는 중/);
      globalThis.__accountViewState = { data: null, error: '서버에 연결할 수 없어요', reload() {} };
      const failed = render();
      assert.match(failed, /서버에 연결할 수 없어요/);
      assert.match(failed, /다시 시도/);
      assert.doesNotMatch(failed, /2,450,000|8,250,000|5,800,000/);
    }
  } finally {
    delete globalThis.__accountViewState;
    await server.close();
    if (storage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = storage;
  }
});

test('계좌 API는 현재 고객 ID로 조회하고 서버 응답을 그대로 사용한다', async () => {
  const storage = globalThis.localStorage;
  const fetch = globalThis.fetch;
  globalThis.localStorage = { getItem: () => null };
  const server = await createServer({ configFile: false, server: { middlewareMode: true, hmr: false, ws: false } });
  try {
    const calls = [];
    const data = { accounts: [{ balance: 123456 }], totalBalance: 123456 };
    globalThis.fetch = async (url) => { calls.push(url); return { ok: true, status: 200, json: async () => data }; };
    const api = await server.ssrLoadModule('/src/api/client.js');
    const { getUserId } = await server.ssrLoadModule('/src/session/index.js');
    assert.deepEqual(await api.getAccounts(), data);
    assert.equal(calls[0], `/api/accounts?userId=${encodeURIComponent(getUserId())}`);
    const { mock } = await server.ssrLoadModule('/src/api/mock.js');
    const before = await mock.getAccounts();
    const reply = await mock.postChat({ message: '엄마한테 30만원 보내줘', inputType: 'TEXT' });
    await mock.confirmPlan(reply.transferPlan.planId);
    const after = await mock.getAccounts();
    assert.equal(after.totalBalance, before.totalBalance - reply.transferPlan.totalAmount);
    assert.equal(after.accounts.find(a => a.withdrawal).balance, before.accounts.find(a => a.withdrawal).balance - reply.transferPlan.totalAmount);
  } finally {
    await server.close();
    globalThis.fetch = fetch;
    if (storage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = storage;
  }
});
