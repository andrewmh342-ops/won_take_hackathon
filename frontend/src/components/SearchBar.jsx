// 담당: 공용 — 메뉴 검색창 (홈·전체 메뉴에서 같이 쓴다). 마이크 버튼은 AI 대화로 연결한다.
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
export default function SearchBar({ value, onChange, onSubmit, trackPrefix }) {
  const { navigate } = useNavigation();
  return (
    <form
      className="search"
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit?.(value);
      }}
    >
      <span aria-hidden="true">⌕</span>
      <input
        aria-label="메뉴 검색"
        placeholder="찾으시는 메뉴를 입력하세요"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        data-track-id={`${trackPrefix}-search`}
      />
      <button
        type="button"
        aria-label="음성으로 질문하기"
        data-track-id={`${trackPrefix}-mic`}
        onClick={() => navigate('chat')}
      >
        <span className="mic-icon" />
      </button>
    </form>
  );
}
