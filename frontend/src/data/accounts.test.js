import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { myAccounts, withdrawalAccount, totalBalance } from './accounts.js';

test('화면의 내 계좌는 백엔드 finance.json(주인)과 같다', () => {
  const backend = JSON.parse(readFileSync(new URL('../../../backend/src/main/resources/data/finance.json', import.meta.url), 'utf8'));
  const pick = (items) => items.map(({ name, number, balance, withdrawal }) => ({ name, number, balance, withdrawal }));
  assert.deepEqual(pick(myAccounts), pick(backend.myAccounts));
});

test('출금 계좌는 하나이고 전체 잔액은 내 계좌의 합이다', () => {
  assert.equal(myAccounts.filter((a) => a.withdrawal).length, 1);
  assert.equal(withdrawalAccount.name, 'WON 생활통장');
  assert.equal(totalBalance, 8250000);
});
