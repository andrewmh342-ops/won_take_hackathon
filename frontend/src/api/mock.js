// 담당: 공용 — 프론트 시연 전용 고정 응답 (실제 AI 분석 아님)
import { myAccounts, withdrawalAccount } from '../data/accounts.js';
import { buildMockPlan, executeMockPlan, confirmMockPlan, answerMockPlan } from './mockPlan.js';
let agreed = null;
const plans = new Map();
let never = localStorage.getItem('wontake-demo-never') === 'true';
const entries = new Map();
const tickets = [];
const conversations = [];
const events = [];
let notFoundStreak = 0;
let planCount = 0;
let mockBalance = withdrawalAccount.balance;
const confirmedPlans = new Set();
const suggestions = [
  { intentId: 'transfer-limit', question: '내 이체한도가 궁금하신가요?' },
  { intentId: 'overseas-remittance', question: '해외송금을 찾고 계신가요?' },
  { intentId: 'domestic-transfer', question: '국내송금을 찾고 계신가요?' },
];
// 이체 요청이면 문장을 읽지 않고 늘 같은 예시 목록을 준다(서버 연결 모드에서만 실제 AI가 나눈다).
const samplePlan = {
  type: 'PLAN', message: '', balance: withdrawalAccount.balance, totalAmount: 950000, expectedBalance: withdrawalAccount.balance - 950000,
  canConfirm: true, blockers: [], mode: 'FRONTEND_MOCK',
  tasks: [
    { order: 1, type: 'TRANSFER', target: '집주인', account: '가상은행 *0102', amount: 650000, memo: '월세', risk: 'MEDIUM', riskReasons: ['FREQUENT_RECIPIENT'], status: 'READY' },
    { order: 2, type: 'TRANSFER', target: '엄마', account: '가상은행 *0103', amount: 300000, memo: '', risk: 'MEDIUM', riskReasons: ['FREQUENT_RECIPIENT'], status: 'READY' },
  ],
};
export const mock = {
  async getAccounts() {
    const accounts = myAccounts.map(a => ({ ...a, balance: a.withdrawal ? mockBalance : a.balance }));
    return { accounts, totalBalance: accounts.reduce((sum, a) => sum + a.balance, 0) };
  },
  async getHistory() {
    return conversations.map((entry) => entry.role === 'USER' ? entry : {
      role: 'ASSISTANT', message: entry.reply, inputType: '', answer: structuredClone(entry),
    });
  },
  async getConsent() {
    return { agreed };
  },
  async putConsent(value) {
    agreed = value;
    if (!value) {
      entries.clear();
      events.length = 0;
    }
  },
  async postEvent({ type, screenId, elementId }) {
    if (!agreed) return { bubble: null };
    events.push({
      type,
      screenId,
      ...(elementId ? { elementId } : {}),
      timestamp: new Date().toISOString(),
    });
    if (never || type !== 'SCREEN_ENTER') return { bubble: null };
    const now = Date.now();
    const times = (entries.get(screenId) || []).filter((time) => now - time <= 60000);
    times.push(now);
    entries.set(screenId, times);
    return { bubble: times.length === 3 ? { screenId, suggestions } : null };
  },
  async closeBubble(screenId, action) {
    entries.delete(screenId);
    if (action === 'NEVER') {
      never = true;
      localStorage.setItem('wontake-demo-never', 'true');
    }
  },
  async postChat({ message, inputType, intentId }) {
    const text = typeof message === 'string' ? message.trim() : '';
    let result;

    if (!text) {
      result = {
        type: 'ASK_TEXT_INPUT',
        normalized: '',
        reply: '글자로 입력해 주세요.',
        intentId: '',
        menuId: '',
        steps: [],
        notFoundStreak,
      };
    } else if (inputType !== 'SUGGESTION' && buildMockPlan(text)) {
      const plan = buildMockPlan(text);
      plans.set(plan.planId, plan);
      notFoundStreak = 0;
      result = {
        type: 'PLAN',
        normalized: '',
        reply: '(가짜) 해야 할 일을 순서대로 정리했어요.',
        intentId: '',
        menuId: '',
        steps: [],
        notFoundStreak,
        handoffSuggested: false,
        plan,
      };
    } else if (inputType === 'SUGGESTION') {
      const answer =
        intentId === 'transfer-limit'
          ? {
              reply: '(가짜) 이체한도는 계좌이체 화면에서 확인할 수 있어요',
              menuId: 'instant-transfer',
            }
          : intentId === 'all-accounts'
            ? {
                reply: '(가짜) 전체계좌조회 메뉴에서 계좌 잔액을 확인할 수 있어요',
                menuId: 'all-accounts',
              }
            : null;
      result = answer
        ? {
            type: 'ANSWER',
            normalized: '',
            ...answer,
            intentId,
            steps: [],
            notFoundStreak: 0,
          }
        : {
            type: 'NOT_FOUND',
            normalized: '',
            reply: '목록에서 질문을 찾지 못했어요. 다른 질문을 입력해 주세요.',
            intentId: '',
            menuId: '',
            steps: [],
            notFoundStreak: ++notFoundStreak,
          };
      if (result.type === 'ANSWER') notFoundStreak = 0;
    } else if (/한테|에게/.test(text) && /\d\s*(만\s*)?원|보내|이체|송금|넣어/.test(text)) {
      // 백엔드 FakeAiClient와 같은 판정: 받는 사람과 돈을 보내는 말이 함께 있어야 이체로 본다.
      result = {
        type: 'PLAN',
        normalized: '',
        reply: '보내실 내용을 정리했어요. 아래 목록을 확인해 주세요. 실제 이체는 하지 않아요.',
        menuId: '',
        menuIds: [],
        steps: [],
        notFoundStreak,
        handoffSuggested: false,
        transferPlan: { ...structuredClone(samplePlan), planId: `mock-plan-${++planCount}` },
      };
    } else if (!/앱|은행|계좌|통장|이체|송금|출금|입금|한도|잔액|내역|인증|비밀번호|로그인|증명|서류|환전|환율|외화|카드|대출|상담|돈|오류|안 ?돼/.test(text)) {
      // 백엔드와 같은 규칙: 금융앱 이용 불편과 무관한 질문은 고정 안내로 거절한다.
      result = {
        type: 'OUT_OF_SCOPE',
        normalized: '',
        reply: 'WON테이크는 이 앱을 쓰다 막힌 점만 도와드려요. 이체, 계좌 조회, 인증, 증명서, 환전 같은 메뉴를 찾거나 쓰다가 어려운 점을 말씀해 주세요.',
        intentId: '',
        menuId: '',
        steps: [],
        notFoundStreak,
        handoffSuggested: false,
      };
    } else if (/상담|상담원/.test(text)) {
      result = {
        type: 'SUGGEST_HANDOFF',
        normalized: inputType === 'VOICE' ? '(가짜) 상담사 연결 요청' : '',
        reply: '상담사에게 지금까지의 내용을 전달할까요?',
        intentId: '',
        menuId: '',
        steps: [],
        notFoundStreak,
      };
    } else if (/모르겠|못 찾|못찾|찾지 못/.test(text)) {
      notFoundStreak += 1;
      const suggestHandoff = notFoundStreak >= 2;
      result = {
        type: suggestHandoff ? 'SUGGEST_HANDOFF' : 'NOT_FOUND',
        normalized: inputType === 'VOICE' ? '(가짜) 뜻을 찾지 못한 질문' : '',
        reply: suggestHandoff
          ? '두 번 연속으로 답을 찾지 못했어요. 상담사에게 이어서 전달할까요?'
          : '말씀하신 내용을 찾지 못했어요. 다른 표현으로 다시 물어봐 주세요.',
        intentId: '',
        menuId: '',
        steps: [],
        notFoundStreak,
      };
    } else {
      notFoundStreak = 0;
      result = {
        type: 'ANSWER',
        normalized: inputType === 'VOICE' ? '이체 한도가 어떻게 되나요' : '',
        reply: '(가짜) 이체한도는 계좌이체 화면에서 확인할 수 있어요',
        intentId: 'transfer-limit',
        menuId: 'instant-transfer',
        steps: ['계좌이체 화면을 열어요.', '이체한도 조회/변경 메뉴를 확인해요.'],
        notFoundStreak,
      };
    }

    conversations.push({ role: 'USER', message: text, inputType, intentId: intentId || '' });
    conversations.push({ role: 'ASSISTANT', ...result });
    return result;
  },
  async executePlan(planId, index) {
    const plan = plans.get(planId);
    if (!plan) throw new Error('플랜을 찾지 못했어요. 다시 물어봐 주세요.');
    const next = executeMockPlan(plan, index);
    plans.set(planId, next);
    return next;
  },
  async getPlan(planId) {
    const plan = plans.get(planId);
    if (!plan) throw new Error('플랜을 찾지 못했어요. 다시 물어봐 주세요.');
    return plan;
  },
  async reviewPlan(planId) {
    return this.getPlan(planId); // 모의: 새 할 일 없음
  },
  async confirmCards(planId, refs) {
    const plan = plans.get(planId);
    if (!plan) throw new Error('플랜을 찾지 못했어요. 다시 물어봐 주세요.');
    const next = confirmMockPlan(plan, refs);
    plans.set(planId, next);
    return next;
  },
  async answerPlan(planId, index, value) {
    const plan = plans.get(planId);
    if (!plan) throw new Error('플랜을 찾지 못했어요. 다시 물어봐 주세요.');
    const next = answerMockPlan(plan, index, value);
    plans.set(planId, next);
    return next;
  },
  async postVoice() {
    return { recognizedText: '(가짜) 이체 한도가 우째 되능교' };
  },
  async getHandoffSummary() {
    return {
      purpose: '(가짜) 고객이 도움을 요청한 업무',
      stuckPoint: '(가짜) 질문에 해당하는 메뉴를 확인 중',
      triedActions: '(가짜) 앱 메뉴 안내와 질문 응답',
      unresolved: '(가짜) 상담사 확인이 필요한 내용',
      clickTimeline: agreed === true ? structuredClone(events) : [],
      clickConsent: agreed === true,
    };
  },
  async postHandoff(summary) {
    const receipt = { ticketId: `T-${tickets.length + 1}`, createdAt: new Date().toISOString() };
    tickets.push({
      ...receipt,
      ...summary,
      clickTimeline: summary.clickConsent ? structuredClone(summary.clickTimeline) : [],
      conversations: structuredClone(conversations),
    });
    return receipt;
  },
  // 비밀번호는 서버(app.plan.password)만 안다. 화면 확인용이라 아무 값이나 받고, 한 번만 승인하는 것만 흉내 낸다.
  async confirmPlan(planId) {
    if (confirmedPlans.has(planId)) throw new Error('이미 승인한 목록이에요.');
    confirmedPlans.add(planId);
    const balanceBefore = mockBalance;
    mockBalance -= samplePlan.totalAmount;
    return { planId, status: 'CONFIRMED', totalAmount: samplePlan.totalAmount, balanceBefore, balanceAfter: mockBalance };
  },
  async getTickets() {
    return structuredClone(tickets);
  },
};
