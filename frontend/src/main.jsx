// 담당: 역할 1 — 앱 조립
import React, { useEffect, useLayoutEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { NavigationProvider, useNavigation } from './navigation/index.jsx';
import PhoneFrame from './layout/PhoneFrame.jsx';
import HomeScreen from './screens/HomeScreen.jsx';
import MenuScreen from './screens/MenuScreen.jsx';
import TransferScreen from './screens/TransferScreen.jsx';
import AccountScreen from './screens/AccountScreen.jsx';
import ConsentScreen from './screens/ConsentScreen.jsx';
import ChatScreen from './screens/ChatScreen.jsx';
import AgentScreen from './screens/AgentScreen.jsx';
import ServiceScreen from './screens/ServiceScreen.jsx';
import LoginScreen from './screens/LoginScreen.jsx';
import CardListScreen from './screens/CardListScreen.jsx';
import CardReissueScreen from './screens/CardReissueScreen.jsx';
import SubscriptionScreen from './screens/SubscriptionScreen.jsx';
import SimplePayScreen from './screens/SimplePayScreen.jsx';
import OverseasScreen from './screens/OverseasScreen.jsx';
import TravelCardScreen from './screens/TravelCardScreen.jsx';
import FxRateScreen from './screens/FxRateScreen.jsx';
import ExchangeApplyScreen from './screens/ExchangeApplyScreen.jsx';
import TravelInsuranceScreen from './screens/TravelInsuranceScreen.jsx';
import PlanScreen from './screens/PlanScreen.jsx';
import SplashScreen from './screens/SplashScreen.jsx';
import { registerMenuNavigation, highlightMenu } from './components/MenuList.jsx';
import { useMock, getHealth } from './api/client.js';
import './styles/tokens.css';
import './styles/app.css';
const screens = {
  home: HomeScreen,
  menu: MenuScreen,
  transfer: TransferScreen,
  account: AccountScreen,
  consent: ConsentScreen,
  chat: ChatScreen,
  agent: AgentScreen,
  login: LoginScreen,
  // 인증/보안·증명서/서류·환전 메뉴는 공통 시연 화면을 쓴다 (screenId는 분류마다 다르게 둔다)
  security: ServiceScreen,
  certificate: ServiceScreen,
  exchange: ServiceScreen,
  card: ServiceScreen,
  // R5 업무 화면 (가상 상태 공유)
  'card-list': CardListScreen,
  'card-reissue': CardReissueScreen,
  subscription: SubscriptionScreen,
  'simple-pay': SimplePayScreen,
  overseas: OverseasScreen,
  'travel-card': TravelCardScreen,
  'fx-rate': FxRateScreen,
  'exchange-apply': ExchangeApplyScreen,
  'travel-insurance': TravelInsuranceScreen,
  // 작업 32: 원테이크 화면(가로 단계 탭)
  plan: PlanScreen,
};
function App() {
  const { screen, key, navigate } = useNavigation();
  const [aiMode, setAiMode] = useState(useMock ? 'FRONTEND_MOCK' : 'CONNECTING');
  useEffect(() => { getHealth().then((status) => setAiMode(status.aiMode)).catch(() => setAiMode('OFFLINE')); }, []);
  useEffect(() => registerMenuNavigation(() => navigate('menu')), [navigate]);
  useEffect(() => {
    if (import.meta.env.DEV) {
      window.highlightMenu = highlightMenu;
      return () => {
        delete window.highlightMenu;
      };
    }
  }, []);
  useLayoutEffect(() => {
    document.querySelector('.phone-main')?.scrollTo(0, 0);
  }, [key]);
  const Screen = screens[screen] || HomeScreen;
  return (
    <>
      <div className="preview-label">
        <span className="preview-dot" />
        WON TAKE{' '}
        <span className="preview-mode">{{ FRONTEND_MOCK: '프런트 모의 모드', FAKE: '모의 AI · 서버 연결됨', CLAUDE: 'Claude 연결 모드', CONNECTING: '서버 확인 중', OFFLINE: '서버 연결 확인 필요' }[aiMode]}</span>
      </div>
      {screen === 'splash' ? (
        <div className="phone splash-phone">
          <SplashScreen key={key} />
        </div>
      ) : screen === 'agent' ? (
        <AgentScreen />
      ) : (
        <PhoneFrame>
          <Screen key={key} />
        </PhoneFrame>
      )}
      <footer className="preview-footer">
        WON테이크 <span>찾기 전에 먼저, 해결될 때까지 끊김 없이</span>
      </footer>
      {import.meta.env.DEV && (
        <div className="preview-tools">
          <button onClick={() => highlightMenu('all-accounts')}>개발 확인: 계좌 메뉴 강조</button>
        </div>
      )}
    </>
  );
}
createRoot(document.getElementById('root')).render(
  <NavigationProvider>
    <App />
  </NavigationProvider>,
);
