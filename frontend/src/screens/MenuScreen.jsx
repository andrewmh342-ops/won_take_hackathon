// 담당: 역할 1 — 전체 메뉴, 검색, 분류 바로가기, 동의 설정
import React, { useEffect, useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import MenuList from '../components/MenuList.jsx';
import SearchBar from '../components/SearchBar.jsx';
import BackButton from '../layout/BackButton.jsx';
import { categories } from '../data/menu.js';
import { setDemoProfile } from '../api/client.js';

const CATEGORY_ICONS = {
  '이체/출금': '↗',
  '계좌조회/관리': '▤',
  '인증/보안': '◇',
  '증명서/서류': '▧',
  환전: '↔',
  카드: '▭',
};

export default function MenuScreen() {
  const { navigate, params } = useNavigation();
  const [query, setQuery] = useState(params.query ?? '');
  const [targetCategory, setTargetCategory] = useState(null);
  const [demoProfile, setDemoProfileState] = useState('');

  // R5 시연용: 가상 고객 A·B를 바꿔 같은 질문에 다른 플랜이 조립되는 것을 보여 준다(개발 빌드에서만).
  const switchProfile = async (profileId) => {
    try {
      await setDemoProfile(profileId);
      setDemoProfileState(profileId);
    } catch (error) {
      setDemoProfileState(`오류: ${error.message}`);
    }
  };

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
      {import.meta.env.DEV && (
        <div className="demo-profile">
          <p>개발 확인: 시연 고객 바꾸기 {demoProfile && <strong>(현재 {demoProfile})</strong>}</p>
          <button className="secondary" onClick={() => switchProfile('A')}>고객 A (트래블카드 있음)</button>
          <button className="secondary" onClick={() => switchProfile('B')}>고객 B (트래블카드 없음)</button>
        </div>
      )}
    </div>
  );
}
