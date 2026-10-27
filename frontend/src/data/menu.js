// 담당: 공용 — 메뉴 목록. 역할 3의 menu.json과 id·category·name·screenId를 똑같이 맞춘다.
// screenId는 이 메뉴를 누르면 열리는 화면이고, 클릭 기록(SCREEN_ENTER)에도 같은 값이 쓰인다.
// 분류마다 메뉴를 고르게 두어, 의도 분석이 한쪽 분류로 쏠리지 않게 한다 (분류당 4~5개).
export const menu = [
  { id: 'instant-transfer', category: '이체/출금', name: '즉시/예약이체', screenId: 'transfer' },
  { id: 'multi-transfer', category: '이체/출금', name: '다건이체', screenId: 'transfer' },
  { id: 'transfer-history', category: '이체/출금', name: '이체내역조회', screenId: 'account' },
  { id: 'overseas-remittance', category: '이체/출금', name: '해외송금', screenId: 'transfer' },
  { id: 'atm-withdrawal', category: '이체/출금', name: 'ATM/영업점 출금', screenId: 'transfer' },

  { id: 'all-accounts', category: '계좌조회/관리', name: '전체계좌조회', screenId: 'account' },
  {
    id: 'transaction-history',
    category: '계좌조회/관리',
    name: '거래내역조회',
    screenId: 'account',
  },
  { id: 'closed-accounts', category: '계좌조회/관리', name: '해지계좌조회', screenId: 'account' },
  { id: 'dormant-accounts', category: '계좌조회/관리', name: '휴면계좌조회', screenId: 'account' },

  { id: 'auth-center', category: '인증/보안', name: '인증센터', screenId: 'security' },
  {
    id: 'certificate-manage',
    category: '인증/보안',
    name: '인증서 발급/관리',
    screenId: 'security',
  },
  {
    id: 'transfer-limit-manage',
    category: '인증/보안',
    name: '이체한도 조회/변경',
    screenId: 'security',
  },
  { id: 'device-manage', category: '인증/보안', name: '기기 등록 관리', screenId: 'security' },

  {
    id: 'balance-certificate',
    category: '증명서/서류',
    name: '잔액증명서 발급',
    screenId: 'certificate',
  },
  {
    id: 'transaction-certificate',
    category: '증명서/서류',
    name: '거래내역 증명서 발급',
    screenId: 'certificate',
  },
  {
    id: 'financial-confirmation',
    category: '증명서/서류',
    name: '금융거래확인서 발급',
    screenId: 'certificate',
  },
  { id: 'bankbook-copy', category: '증명서/서류', name: '통장 사본 발급', screenId: 'certificate' },

  { id: 'exchange-apply', category: '환전', name: '환전 신청', screenId: 'exchange-apply' },
  { id: 'exchange-rate', category: '환전', name: '환율 조회', screenId: 'fx-rate' },
  { id: 'exchange-history', category: '환전', name: '환전 내역 조회', screenId: 'exchange' },
  { id: 'exchange-return', category: '환전', name: '외화 재환전', screenId: 'exchange' },
  { id: 'travel-insurance', category: '환전', name: '여행자 보험 가입 안내', screenId: 'travel-insurance' },

  // R5 업무 블록 조립용 카드 메뉴 (공통 시연 화면)
  { id: 'card-loss-report', category: '카드', name: '카드 분실신고', screenId: 'card-list' },
  { id: 'card-reissue', category: '카드', name: '카드 재발급', screenId: 'card-reissue' },
  { id: 'auto-payment', category: '카드', name: '자동결제 관리', screenId: 'subscription' },
  { id: 'simple-pay', category: '카드', name: '간편결제 관리', screenId: 'simple-pay' },
  { id: 'overseas-payment', category: '카드', name: '해외결제 설정', screenId: 'overseas' },
  { id: 'travel-card-apply', category: '카드', name: '트래블카드 신청', screenId: 'travel-card' },
];

export const categories = [...new Set(menu.map((item) => item.category))];

export const findMenu = (menuId) => menu.find((item) => item.id === menuId);
