// 담당: 공용 — 390px 고정 휴대폰 프레임
import React from 'react';
import TopBar from './TopBar.jsx';
import BottomTab from './BottomTab.jsx';
import Bubble from '../components/Bubble.jsx';
import { useNavigation } from '../navigation/index.jsx';
export default function PhoneFrame({ children }) {
  // 원테이크 화면은 한 단계에 집중하도록 아래 탭을 숨긴다(작업 32).
  const { screen } = useNavigation();
  return (
    <div className="phone">
      <div className="status-bar">
        <span>9:41</span>
        <span className="status-right">▂▄▆ &nbsp; ◔ &nbsp; ▰</span>
      </div>
      <TopBar />
      <main className="phone-main">{children}</main>
      <Bubble />
      {screen !== 'plan' && <BottomTab />}
    </div>
  );
}
