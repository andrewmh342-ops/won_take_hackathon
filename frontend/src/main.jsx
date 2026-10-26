// 담당: 역할 1 — 앱 조립
import React, { useEffect, useLayoutEffect } from 'react';
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
import { registerMenuNavigation, highlightMenu } from './components/MenuList.jsx';
import { useMock } from './api/client.js';
import './styles/tokens.css';
import './styles/app.css';
const screens = { home: HomeScreen, menu: MenuScreen, transfer: TransferScreen, account: AccountScreen, consent: ConsentScreen, chat: ChatScreen, agent: AgentScreen };
function App() {
  const { screen, key, navigate } = useNavigation();
  useEffect(() => registerMenuNavigation(() => navigate('menu')), [navigate]);
  useEffect(() => { if (import.meta.env.DEV) { window.highlightMenu = highlightMenu; return () => { delete window.highlightMenu; }; } }, []);
  useLayoutEffect(() => { document.querySelector('.phone-main')?.scrollTo(0, 0); }, [key]);
  const Screen = screens[screen] || HomeScreen;
  return <><div className="preview-label"><span className="preview-dot"/>WON TAKE <span> / </span> CODEX UI <span className="preview-mode">{useMock ? '가짜 응답 모드' : 'API 연결 모드'}</span></div>{screen === 'agent' ? <AgentScreen/> : <PhoneFrame><Screen key={key}/></PhoneFrame>}<footer className="preview-footer">WON테이크 <span>찾기 전에 먼저, 해결될 때까지 끊김 없이</span></footer>{import.meta.env.DEV && <div className="preview-tools"><button onClick={() => highlightMenu('all-accounts')}>개발 확인: 계좌 메뉴 강조</button></div>}</>;
}
createRoot(document.getElementById('root')).render(<NavigationProvider><App/></NavigationProvider>);
