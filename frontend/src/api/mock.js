// 담당: 공용 — 프론트 시연 전용 고정 응답 (실제 AI 분석 아님)
let agreed = null;
let never = localStorage.getItem('wontake-demo-never') === 'true';
const entries = new Map();
const tickets = [];
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
    if (!value) entries.clear();
  },
  async postEvent({ type, screenId }) {
    if (!agreed || never || type !== 'SCREEN_ENTER') return { bubble: null };
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
  async postChat() {
    return {
      type: 'ANSWER',
      normalized: '',
      reply: '(가짜) 이체한도는 계좌이체 화면에서 확인할 수 있어요',
      intentId: 'transfer-limit',
      menuId: 'instant-transfer',
      steps: [],
      notFoundStreak: 0,
    };
  },
  async postVoice() {
    return { recognizedText: '(가짜) 이체 한도가 우째 되능교' };
  },
  async getHandoffSummary() {
    return {
      purpose: '(가짜)',
      stuckPoint: '(가짜)',
      triedActions: '(가짜)',
      unresolved: '(가짜)',
      clickTimeline: [],
      clickConsent: agreed === true,
    };
  },
  async postHandoff(summary) {
    const receipt = { ticketId: `T-${tickets.length + 1}`, createdAt: new Date().toISOString() };
    tickets.push({ ...receipt, summary, clickTimeline: [], conversations: [] });
    return receipt;
  },
  async getTickets() {
    return structuredClone(tickets);
  },
};
