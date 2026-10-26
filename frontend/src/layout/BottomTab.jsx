// 담당: 공용 — 홈만 동작하는 하단 탭
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
export default function BottomTab() {
  const { screen, navigate } = useNavigation();
  return (
    <nav className="bottom-tabs" aria-label="하단 메뉴">
      {[
        ['상품', '◇'],
        ['자산소비', '▥'],
        ['홈', '⌂'],
        ['주식', '↗'],
        ['혜택', '♧'],
      ].map(([label, icon]) => (
        <button
          key={label}
          disabled={label !== '홈'}
          className={label === '홈' && screen === 'home' ? 'active' : ''}
          aria-current={label === '홈' && screen === 'home' ? 'page' : undefined}
          data-track-id={`tab-${label}`}
          onClick={() => navigate('home')}
        >
          <span>{icon}</span>
          {label}
        </button>
      ))}
    </nav>
  );
}
