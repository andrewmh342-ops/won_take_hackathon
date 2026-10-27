// 담당: 역할 1 — 인증/보안·증명서/서류·환전 메뉴의 공통 시연 화면 (실제 업무는 실행하지 않음)
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
import BackButton from '../layout/BackButton.jsx';
import { findMenu } from '../data/menu.js';

const DESCRIPTIONS = {
  security: '인증서와 보안 설정을 관리하는 화면이에요.',
  certificate: '필요한 증명서를 골라 발급하는 화면이에요.',
  exchange: '환전과 환율을 확인하는 화면이에요.',
  card: '카드 분실·재발급·결제 설정을 관리하는 화면이에요.',
};

export default function ServiceScreen() {
  const { screen, params, navigate } = useNavigation();
  const item = findMenu(params.menuId);
  return (
    <div className="page">
      <BackButton />
      <p className="eyebrow">{item?.category ?? '서비스'}</p>
      <h1>{item?.name ?? '서비스'}</h1>
      <p className="muted">{DESCRIPTIONS[screen]}</p>
      <div className="placeholder">
        <span className="ai-mark">W</span>
        <h2>시연용 화면</h2>
        <p>가상 데이터로 구성된 화면이에요. 실제 업무는 실행되지 않아요.</p>
      </div>
      <button
        className="secondary full"
        data-track-id="service-ask-ai"
        onClick={() => navigate('chat')}
      >
        이 메뉴가 어렵다면 AI 길잡이에게 물어보기
      </button>
    </div>
  );
}
