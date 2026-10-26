// 담당: 공용 — 바로 전 화면으로 돌아가는 버튼
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
export default function BackButton() {
  const { goBack } = useNavigation();
  return (
    <button className="back" onClick={goBack} data-track-id="back">
      ‹ 뒤로
    </button>
  );
}
