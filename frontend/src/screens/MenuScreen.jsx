// 담당: 역할 1 — 전체 메뉴, 검색, 분류 바로가기, 동의 설정
import React, { useEffect, useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import MenuList from '../components/MenuList.jsx';
import SearchBar from '../components/SearchBar.jsx';
import BackButton from '../layout/BackButton.jsx';
import { categories } from '../data/menu.js';

const CATEGORY_ICONS = {
  '이체/출금': '↗',
  '계좌조회/관리': '▤',
  '인증/보안': '◇',
  '증명서/서류': '▧',
  환전: '↔',
};

export default function MenuScreen() {
  const { navigate, params } = useNavigation();
  const [query, setQuery] = useState(params.query ?? '');
  const [targetCategory, setTargetCategory] = useState(null);

  // 분류 바로가기: 검색어를 지우고, 목록이 다시 그려진 뒤 그 분류 제목으로 스크롤한다
  const jumpTo = (category) => {
    setQuery('');
    setTargetCategory(category);
  };
  useEffect(() => {
    if (!targetCategory) return;
    document
      .querySelector(`[data-category="${targetCategory}"]`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    setTargetCategory(null);
  }, [targetCategory, query]);

  return (
    <div className="page">
      <BackButton />
      <p className="eyebrow">ALL MENU</p>
      <h1>무엇을 도와드릴까요?</h1>
      <SearchBar value={query} onChange={setQuery} trackPrefix="menu" />
      {query.trim() && (
        <button className="secondary full" onClick={() => navigate('chat', { question: query.trim(), autoSend: true })}>
          이 검색어로 AI에게 물어보기
        </button>
      )}
      <div className="menu-shortcuts">
        {categories.map((category) => (
          <button
            key={category}
            onClick={() => jumpTo(category)}
            data-track-id={`menu-category-${category}`}
          >
            <span>{CATEGORY_ICONS[category] ?? '·'}</span>
            {category}
          </button>
        ))}
      </div>
      <MenuList query={query} />
      <button
        className="settings-link"
        onClick={() => navigate('consent')}
        data-track-id="consent-settings"
      >
        도움 제안 · 클릭 기록 동의 설정 <span>›</span>
      </button>
      <button className="settings-link" onClick={() => navigate('agent')}>
        상담사 화면 미리보기 <span>↗</span>
      </button>
    </div>
  );
}
