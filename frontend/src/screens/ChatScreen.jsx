// 담당: 역할 2 — 대화 기능을 채울 자리
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
export default function ChatScreen() { const { params, navigate } = useNavigation(); return <div className="page"><p className="eyebrow">WON TAKE ASSISTANT</p><h1>AI 대화</h1><p className="muted">어떤 도움이 필요하세요?</p>{params.question && <div className="question-preview">{params.question}</div>}<div className="placeholder"><span className="ai-mark">W</span><h2>역할 2가 만들 자리</h2><p>대화 · 음성 입력 · 상담 연결</p></div><button className="secondary full" onClick={() => navigate('menu')}>전체 메뉴로 돌아가기</button></div>; }
