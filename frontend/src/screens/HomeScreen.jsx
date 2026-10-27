// 담당: 역할 1 — 홈 화면
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import SearchBar from '../components/SearchBar.jsx';
import { useAuth, logout } from '../session/auth.js';
import { useAccounts } from '../data/useAccounts.js';
import AccountStatus from '../components/AccountStatus.jsx';

// [이름, 화면, 아이콘, 메뉴 id]
const shortcuts = [
  ['계좌조회', 'account', '▤', 'all-accounts'],
  ['계좌이체', 'transfer', '↗', 'instant-transfer'],
  ['전체메뉴', 'menu', '☷'],
  ['AI 길잡이', 'chat', 'W'],
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
    <div className="home">
      <section className="welcome">
        <p className="eyebrow">나의 일상에 가까운 금융</p>
        <h1>
          {user ? `${user.name}님, 반가워요` : '오늘도 반가워요'}
          <span className="blue">.</span>
        </h1>
      </section>
      <SearchBar
        value={query}
        onChange={setQuery}
        onSubmit={(value) => navigate('menu', { query: value.trim() })}
        trackPrefix="home"
      />
      <section className="hero">
        <span className="pill">WON TAKE</span>
        <h2>
          어디로 가야 할지
          <br />
          막막할 때, 함께.
        </h2>
        <p>
          찾기 전에 먼저,
          <br />
          해결될 때까지 끊김 없이
        </p>
        <button data-track-id="hero-chat" onClick={() => navigate('chat')}>
          AI 길잡이 만나기 <span>↗</span>
        </button>
        <div className="hero-art" aria-hidden="true">
          <div className="orbit" />
          <div className="art-card">
            W<span>당신 곁의 길잡이</span>
          </div>
          <div className="art-dot">↗</div>
        </div>
      </section>
      {user ? (
        <section className="login-card logged-in">
          <div>
            <strong>{user.name}님의 전체 잔액</strong>
            <AccountStatus {...accountState} />
            {accountState.data && <p className="balance">{accountState.data.totalBalance.toLocaleString('ko-KR')}원</p>}
          </div>
          <div className="login-actions">
            <button
              className="secondary"
              data-track-id="home-accounts"
              onClick={() => navigate('account', { menuId: 'all-accounts' })}
            >
              계좌 보기
            </button>
            <button className="text-link" data-track-id="home-logout" onClick={logout}>
              로그아웃
            </button>
          </div>
        </section>
      ) : (
        <section className="login-card">
          <div>
            <strong>내 계좌, 한눈에</strong>
            <p>로그인하고 자산을 확인하세요</p>
          </div>
          <button
            className="secondary"
            data-track-id="home-login"
            onClick={() => navigate('login')}
          >
            로그인
          </button>
        </section>
      )}
      <section className="section">
        <div className="section-title">
          <h2>자주 찾는 서비스</h2>
          <span>QUICK MENU</span>
        </div>
        <div className="shortcut-grid">
          {shortcuts.map(([label, screen, icon, menuId]) => (
            <button
              key={label}
              data-track-id={`quick-${screen}-${label}`}
              onClick={() => navigate(screen, menuId ? { menuId } : {})}
            >
              <span className="shortcut-icon">{icon}</span>
              <span>{label}</span>
            </button>
          ))}
        </div>
      </section>
      <p className="demo-note">가상 데이터로 구성된 해커톤 시연 화면입니다.</p>
    </div>
  );
}
