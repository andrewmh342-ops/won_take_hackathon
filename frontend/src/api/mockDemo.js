// 담당: 공용 — R5 업무 화면의 프런트 전용 모의 상태 (VITE_USE_MOCK=true). 백엔드 /api/demo/state와 같은 모양.
import { toKrw } from '../demo/fx.js';

const card = (ref, name, kind, last4, extra) => ({
  ref, name, kind, last4, display: `${name} 끝자리 ${last4}(가상)`, travel: false, status: 'ACTIVE', reissued: false,
  simplePay: false, simplePayName: '', overseasPayment: true, subscriptions: [], ...extra,
});
const sub = (id, category, merchant, amount, billingDay) => ({ id, category, merchant, amount, billingDay, status: 'ACTIVE' });
const today = new Date().toISOString().slice(0, 10);

const state = {
  profileId: 'A',
  label: '시연 고객 A (프런트 모의)',
  cards: [
    card('card-1', '우리 신용카드', '신용', '4821', {
      simplePay: true, simplePayName: '삼성페이',
      subscriptions: [sub('ap-1', 'OTT', '넷플릭스', 17000, 5), sub('ap-2', 'OTT', '유튜브 프리미엄', 14900, 12), sub('ap-3', '통신', 'SK텔레콤', 55000, 25)],
    }),
    card('card-2', '우리 트래블 체크카드', '체크', '7310', { travel: true, overseasPayment: false }),
    card('card-3', '우리 체크카드', '체크', '3356', {
      simplePay: true, simplePayName: '카카오페이',
      subscriptions: [sub('ap-4', '음악', '멜론', 10900, 1), sub('ap-5', '쇼핑 멤버십', '쿠팡 와우', 7890, 18)],
    }),
  ],
  hasTravelCard: true,
  travelCardApplication: null,
  fxRates: [
    { code: 'JPY', name: '일본 엔', unit: 100, krw: 905 },
    { code: 'USD', name: '미국 달러', unit: 1, krw: 1380 },
    { code: 'EUR', name: '유로', unit: 1, krw: 1500 },
    { code: 'CNY', name: '중국 위안', unit: 1, krw: 190 },
  ],
  exchangeRequests: [],
  accounts: [{ id: 'acc-1', name: '우리 WON 통장 1002-***-0001(가상)' }, { id: 'acc-2', name: '우리 주거래 통장 1002-***-0002(가상)' }],
  branches: [{ id: 'icn-t1', name: '인천공항 제1터미널 환전소(가상)' }, { id: 'icn-t2', name: '인천공항 제2터미널 환전소(가상)' }, { id: 'seoul', name: '서울 본점 영업부(가상)' }],
  address: '서울특별시 ○○구 ○○로 12, 101동 1001호(가상)',
  exchangeLimit: 2000000,
  today,
  plans: {},
};

const copy = () => JSON.parse(JSON.stringify(state));
const find = (ref) => state.cards.find((item) => item.ref === ref);

export const mockDemo = {
  async getState() {
    return copy();
  },
  async act({ action, ref, choices, on, account, receive, currency, amount, branch, date }) {
    const target = find(ref);
    if (action === 'CARD_STOP') target.status = 'STOPPED';
    if (action === 'CARD_REISSUE') {
      if (target.status !== 'STOPPED') throw new Error('먼저 분실신고를 해 주세요.');
      target.reissued = true;
    }
    if (action === 'SUBSCRIPTION') {
      for (const [id, choice] of Object.entries(choices)) {
        const item = target.subscriptions.find((s) => s.id === id);
        if (choice === 'SWITCH' && !target.reissued) throw new Error('새 카드를 신청한 뒤 옮길 수 있어요.');
        if (item.status === 'ACTIVE') item.status = choice === 'SWITCH' ? 'SWITCHED' : 'CANCELLED';
      }
    }
    if (action === 'SIMPLE_PAY_UNLINK') target.simplePay = false;
    if (action === 'OVERSEAS') target.overseasPayment = on;
    if (action === 'TRAVEL_CARD_APPLY') {
      if (state.hasTravelCard) throw new Error('이미 트래블카드가 있어요.');
      state.travelCardApplication = { account, receive: receive === 'MAIL' ? '우편 수령' : '영업점 수령', appliedOn: today, status: '발급 대기(가상)' };
    }
    if (action === 'EXCHANGE_APPLY') {
      const rate = state.fxRates.find((r) => r.code === currency);
      state.exchangeRequests.push({ id: `ex-${state.exchangeRequests.length + 1}`, currency, amount, krw: toKrw(amount, rate), branch, date, account });
    }
    return copy();
  },
};
