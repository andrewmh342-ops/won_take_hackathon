// 담당: 공용 — 데모 고객의 내 계좌. 주인은 backend/src/main/resources/data/finance.json의 myAccounts이고,
// accounts.test.js가 두 값을 대조한다. 잔액을 바꿀 때는 finance.json을 먼저 고치고 여기를 맞춘다.
// withdrawal이 true인 계좌에서 돈이 나간다(작업 카드의 "보낸 뒤 잔액"도 이 계좌 기준).
export const myAccounts = [
  { name: 'WON 생활통장', number: 'DEMO-ACCOUNT-01', balance: 2450000, withdrawal: true },
  { name: 'WON 차곡차곡 적금', number: 'DEMO-ACCOUNT-02', balance: 5800000, withdrawal: false },
];
export const withdrawalAccount = myAccounts.find((account) => account.withdrawal);
export const totalBalance = myAccounts.reduce((sum, account) => sum + account.balance, 0);
