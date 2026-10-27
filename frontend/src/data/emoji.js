// 담당: 공용 — 메뉴·분류·하단 탭 이모지. 홈 빠른 메뉴와 전체 메뉴가 같은 이모지를 쓰도록 한 곳에 모은다.
// 이모지 폰트 파일은 넣지 않고 기기 기본 이모지(유니코드 문자)로 보여 준다. 토스페이스 등 출품 제한이 있는 폰트는 쓰지 않는다.
export const CATEGORY_EMOJI = {
  '이체/출금': '💸',
  '계좌조회/관리': '🏦',
  '인증/보안': '🔐',
  '증명서/서류': '📄',
  환전: '💱',
  카드: '💳',
};

export const MENU_EMOJI = {
  'instant-transfer': '💸',
  'multi-transfer': '📤',
  'transfer-history': '🧾',
  'overseas-remittance': '🌏',
  'atm-withdrawal': '🏧',

  'all-accounts': '🏦',
  'transaction-history': '📒',
  'closed-accounts': '🗃️',
  'dormant-accounts': '💤',

  'auth-center': '🔐',
  'certificate-manage': '🪪',
  'transfer-limit-manage': '🎚️',
  'device-manage': '📱',

  'balance-certificate': '📄',
  'transaction-certificate': '📑',
  'financial-confirmation': '✅',
  'bankbook-copy': '📘',

  'exchange-apply': '💱',
  'exchange-rate': '📈',
  'exchange-history': '🗒️',
  'exchange-return': '🔄',
  'travel-insurance': '🧳',

  'card-loss-report': '🚨',
  'card-reissue': '💳',
  'auto-payment': '🔁',
  'simple-pay': '📲',
  'overseas-payment': '✈️',
  'travel-card-apply': '🛫',
};

// 메뉴가 아닌 홈 바로가기
export const ALL_MENU_EMOJI = '🗂️';
export const AI_GUIDE_EMOJI = '🤖';

export const TAB_EMOJI = {
  상품: '🛍️',
  자산소비: '📊',
  홈: '🏠',
  주식: '📈',
  혜택: '🎁',
};

export const menuEmoji = (menuId) => MENU_EMOJI[menuId] ?? '•';
