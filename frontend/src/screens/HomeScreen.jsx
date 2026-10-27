// 담당: 역할 1 — 홈 화면 (우리WON뱅킹 메인 구조를 참고한 정적 화면, 계좌·금액은 모두 가상)
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import SearchBar from '../components/SearchBar.jsx';
import RobotIcon from '../components/RobotIcon.jsx';
import { useAuth, logout } from '../session/auth.js';
import { useAccounts } from '../data/useAccounts.js';
import AccountStatus from '../components/AccountStatus.jsx';

// [이름, 화면, 아이콘, 메뉴 id]. AI 길잡이는 스플래시를 거쳐 대화로 간다.
const shortcuts = [
  ['계좌조회', 'account', '▤', 'all-accounts'],
  ['계좌이체', 'transfer', '↗', 'instant-transfer'],
  ['전체메뉴', 'menu', '☷'],
  ['AI 길잡이', 'splash', null],
  ['해외송금', 'transfer', '◎', 'overseas-remittance'],
  ['환전', 'exchange', '↔', 'exchange-apply'],
  ['증명서', 'certificate', '▧', 'balance-certificate'],
  ['인증센터', 'security', '◇', 'auth-center'],
];

export default function HomeScreen() {
  const { navigate } = useNavigation();
  const user = useAuth();
  const accountState = useAccounts(Boolean(user));
  const [query, setQuery] = useState('');
  return (
    <div className="home won-home">
      <section className="welcome">
        <h1>
          {user ? `${user.name}님, 반가워요` : '오늘도 반가워요'}
          <span className="blue">.</span>
        </h1>
      </section>

      {user ? (
        <section className="account-card" aria-label="대표 계좌">
          <div className="account-card-top">
            <div>
              <strong>{user.name}님의 전체 잔액</strong>
              <small>가상 계좌 · 서버 가상 데이터</small>
            </div>
            <button className="text-link on-blue" data-track-id="home-logout" onClick={logout}>
              로그아웃
            </button>
          </div>
          <div className="account-status"><AccountStatus {...accountState} /></div>
          {accountState.data && (
            <p className="account-balance">{accountState.data.totalBalance.toLocaleString('ko-KR')}<span>원</span></p>
          )}
          <div className="account-actions">
            <button
              data-track-id="home-accounts"
              onClick={() => navigate('account', { menuId: 'all-accounts' })}
            >
              조회
            </button>
            <button
              data-track-id="home-transfer"
              onClick={() => navigate('transfer', { menuId: 'instant-transfer' })}
            >
              이체
            </button>
          </div>
        </section>
      ) : (
        <section className="account-card logged-out" aria-label="로그인">
          <strong>내 계좌, 한눈에</strong>
          <p>로그인하고 잔액과 거래를 확인하세요</p>
          <div className="account-actions">
            <button data-track-id="home-login" onClick={() => navigate('login')}>
              로그인
            </button>
          </div>
        </section>
      )}

      <section className="section quick-section">
        <div className="shortcut-grid">
          {shortcuts.map(([label, screen, icon, menuId]) => (
            <button
              key={label}
              data-track-id={`quick-${screen}-${label}`}
              onClick={() => navigate(screen, menuId ? { menuId } : {})}
            >
              <span className={`shortcut-icon${icon ? '' : ' robot'}`}>
                {icon ?? <RobotIcon size={24} />}
              </span>
              <span>{label}</span>
            </button>
          ))}
        </div>
      </section>

      <SearchBar
        value={query}
        onChange={setQuery}
        onSubmit={(value) => navigate('menu', { query: value.trim() })}
        trackPrefix="home"
      />

      <button className="take-banner" data-track-id="hero-chat" onClick={() => navigate('splash')}>
        <span className="take-banner-icon">
          <RobotIcon size={30} />
        </span>
        <span className="take-banner-text">
          <small>WON TAKE · AI 길잡이</small>
          <strong>어디로 가야 할지 막막할 때, 함께</strong>
          <span>찾기 전에 먼저, 해결될 때까지 끊김 없이</span>
        </span>
        <span className="take-banner-arrow" aria-hidden="true">›</span>
      </button>

      <section className="section">
        <div className="section-title">
          <h2>이번 달 혜택</h2>
          <span>BENEFIT</span>
        </div>
        <div className="benefit-list">
          <button data-track-id="home-benefit-fx" onClick={() => navigate('fx-rate', { menuId: 'exchange-rate' })}>
            <span className="benefit-dot">$</span>
            <span>
              <strong>환율 확인하고 환전하기</strong>
              <small>여행 전 가상 환율 보기</small>
            </span>
          </button>
          <button
            data-track-id="home-benefit-overseas"
            onClick={() => navigate('transfer', { menuId: 'overseas-remittance' })}
          >
            <span className="benefit-dot">◎</span>
            <span>
              <strong>해외송금 한 번에</strong>
              <small>가족에게 보내는 해외송금</small>
            </span>
          </button>
        </div>
      </section>
      <p className="demo-note">가상 데이터로 구성된 해커톤 시연 화면입니다.</p>
    </div>
  );
}
