// 담당: 공용 — 390px 고정 휴대폰 프레임
import React from 'react';
import TopBar from './TopBar.jsx';
import BottomTab from './BottomTab.jsx';
import Bubble from '../components/Bubble.jsx';
export default function PhoneFrame({ children }) {
  return (
    <div className="phone">
      <div className="status-bar">
        <span>9:41</span>
        <span className="status-right">▂▄▆ &nbsp; ◔ &nbsp; ▰</span>
      </div>
      <TopBar />
      <main className="phone-main">{children}</main>
      <Bubble />
      <BottomTab />
    </div>
  );
}
