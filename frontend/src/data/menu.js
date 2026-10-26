// 담당: 공용 — 역할 3의 메뉴 ID와 맞추는 목록
export const menu = [
  { id: 'instant-transfer', category: '이체/출금', name: '즉시/예약이체', screen: 'transfer' },
  { id: 'multi-transfer', category: '이체/출금', name: '다건이체', screen: 'transfer' },
  { id: 'transfer-history', category: '이체/출금', name: '이체내역조회', screen: 'account' },
  { id: 'overseas-remittance', category: '이체/출금', name: '해외송금', screen: 'transfer' },
  { id: 'atm-withdrawal', category: '이체/출금', name: 'ATM/영업점 출금', screen: 'transfer' },
  { id: 'all-accounts', category: '계좌조회/관리', name: '전체계좌조회', screen: 'account' },
  { id: 'closed-accounts', category: '계좌조회/관리', name: '해지계좌조회', screen: 'account' },
  { id: 'auth-center', category: '인증/보안', name: '인증센터', screen: 'menu' },
];
