// 담당: 공용 — 홈만 동작하는 하단 탭 (이모지는 data/emoji.js, 준비 중인 탭은 흐리게 보여 준다)
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
import { TAB_EMOJI } from '../data/emoji.js';
export default function BottomTab() {
  const { screen, navigate } = useNavigation();
  return (
    <nav className="bottom-tabs" aria-label="하단 메뉴">
      {Object.entries(TAB_EMOJI).map(([label, emoji]) => {
        const active = label === '홈' && screen === 'home';
        return (
          <button
            key={label}
            disabled={label !== '홈'}
            className={active ? 'active' : ''}
            aria-current={active ? 'page' : undefined}
            aria-label={label === '홈' ? '홈' : `${label} (준비 중)`}
            title={label === '홈' ? undefined : '준비 중이에요'}
            data-track-id={`tab-${label}`}
            onClick={() => navigate('home')}
          >
            <span className="tab-emoji emoji" aria-hidden="true">{emoji}</span>
            {label}
          </button>
        );
      })}
    </nav>
  );
}
