// 담당: 공용 — 프론트 시연 전용 고정 응답 (실제 AI 분석 아님)
let agreed = null;
let never = localStorage.getItem('wontake-demo-never') === 'true';
const entries = new Map();
const tickets = [];
const conversations = [];
const events = [];
let notFoundStreak = 0;
const suggestions = [
  { intentId: 'transfer-limit', question: '내 이체한도가 궁금하신가요?' },
  { intentId: 'overseas-remittance', question: '해외송금을 찾고 계신가요?' },
  { intentId: 'domestic-transfer', question: '국내송금을 찾고 계신가요?' },
];
export const mock = {
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
  async getTickets() {
    return structuredClone(tickets);
  },
};
