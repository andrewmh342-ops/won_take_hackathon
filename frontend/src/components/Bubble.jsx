// 담당: 공용 — 도움 제안 말풍선(R1). 화면 스크롤과 상관없이 휴대폰 틀 오른쪽 아래에 뜬다.
// 말풍선을 누르면 AI 대화로 이동하고, 추천 질문(최대 3개)을 params.suggestions로 넘긴다.
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import { closeBubble } from '../api/client.js';
const MAX_SUGGESTIONS = 3;
export default function Bubble() {
  const { bubble, setBubble, navigate } = useNavigation();
  const [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  if (!bubble) return null;
  async function close(action) {
    setBusy(true);
    setError('');
    try {
      await closeBubble(bubble.screenId, action);
      setBubble(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  const open = () =>
    navigate('chat', { suggestions: bubble.suggestions.slice(0, MAX_SUGGESTIONS) });
  return (
    <aside className="help-bubble" aria-label="AI 도움 제안">
      <button className="help-bubble-main" data-track-id="help-bubble-open" onClick={open}>
        <span className="ai-mark">W</span>
        <span>
          <strong>도움이 필요하세요?</strong>
          <small>눌러서 AI 길잡이와 이야기해요</small>
        </span>
      </button>
      {error && <p role="alert">{error}</p>}
      <div className="help-bubble-actions">
        <button disabled={busy} onClick={() => close('NEVER')}>
          다시 띄우지 않기
        </button>
        <button disabled={busy} onClick={() => close('CLOSE')}>
          닫기
        </button>
      </div>
    </aside>
  );
}
