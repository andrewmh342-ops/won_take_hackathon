// 담당: 공용 — 상단 탐색
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
export default function TopBar() {
  const { navigate } = useNavigation();
  return (
    <header className="topbar">
      <button
        className="wordmark"
        aria-label="WON 홈"
        data-track-id="logo-home"
        onClick={() => navigate('home')}
      >
        WON<span>테이크</span>
      </button>
      <div className="top-actions">
        <button className="ai-button" data-track-id="top-ai" onClick={() => navigate('chat')}>
          AI
        </button>
        <button className="icon-button" aria-label="알림 준비 중" disabled>
          <span className="bell-icon" />
        </button>
        <button
          className="icon-button"
          aria-label="전체 메뉴"
          data-track-id="hamburger"
          onClick={() => navigate('menu')}
        >
          <span className="hamburger-icon" />
        </button>
      </div>
    </header>
  );
}
