// 담당: 역할 2 — PC 상담사 화면을 채울 자리
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
export default function AgentScreen() { const { navigate } = useNavigation(); return <main className="agent"><div className="section-title"><span className="wordmark">WON<span>테이크</span></span><button className="secondary" onClick={() => navigate('home')}>고객 화면으로</button></div><p className="eyebrow">COUNSELOR WORKSPACE</p><h1>상담사 화면</h1><div className="placeholder"><h2>역할 2가 만들 자리</h2><p>접수 목록 · 요약 4항목 · 클릭 타임라인 · 대화 원문</p></div></main>; }
