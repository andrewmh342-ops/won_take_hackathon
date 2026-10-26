// 담당: 역할 2 — 대화 기능을 채울 자리
// 받는 params: { suggestions } (도움 말풍선을 눌러 들어옴, 추천 질문 최대 3개)
//             { intentId, question } (질문이 정해진 채로 들어옴)
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import BackButton from '../layout/BackButton.jsx';
export default function ChatScreen() {
  const { params } = useNavigation();
  const [chosen, setChosen] = useState(
    params.question ? { intentId: params.intentId, question: params.question } : null,
  );
  return (
    <div className="page">
      <BackButton />
      <p className="eyebrow">WON TAKE ASSISTANT</p>
      <h1>AI 대화</h1>
      <p className="muted">어떤 도움이 필요하세요?</p>
      {!chosen && params.suggestions?.length > 0 && (
        <div className="suggestion-list">
          <p>혹시 이걸 찾고 계셨나요?</p>
          {params.suggestions.map((suggestion) => (
            <button
              key={suggestion.intentId}
              data-track-id={`suggestion-${suggestion.intentId}`}
              onClick={() => setChosen(suggestion)}
            >
              {suggestion.question}
            </button>
          ))}
        </div>
      )}
      {chosen && <div className="question-preview">{chosen.question}</div>}
      <div className="placeholder">
        <span className="ai-mark">W</span>
        <h2>역할 2가 만들 자리</h2>
        <p>대화 · 음성 입력 · 상담 연결</p>
      </div>
    </div>
  );
}
