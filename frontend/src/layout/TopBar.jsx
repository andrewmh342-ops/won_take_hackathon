// 담당: 공용 — 상단 탐색
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
import RobotIcon from '../components/RobotIcon.jsx';
export default function TopBar() {
  const { navigate, handoffHint, openHandoff } = useNavigation();
  return (
    <header className="topbar">
      <button
        className="wordmark"
        aria-label="우리WON뱅킹 홈"
        data-track-id="logo-home"
        onClick={() => navigate('home')}
      >
        우리<b>WON</b>뱅킹
      </button>
      <div className="top-actions">
        {/* 상담 연결은 언제나 누를 수 있다. AI가 답을 찾기 어려울 때만 노란색으로 강조한다(SPEC R3). */}
        <button
          className={`handoff-button${handoffHint ? ' highlighted' : ''}`}
          data-track-id="top-handoff"
          aria-label={handoffHint ? '상담사 연결 (추천)' : '상담사 연결'}
          onClick={openHandoff}
        >
          상담
        </button>
        {/* AI 버튼: WON TAKE 스플래시를 약 1초 보여 준 뒤 AI 대화로 들어간다. */}
        <button
          className="ai-button"
          aria-label="AI 길잡이"
          title="AI 길잡이"
          data-track-id="top-ai"
          onClick={() => navigate('splash')}
        >
          <RobotIcon size={20} />
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
