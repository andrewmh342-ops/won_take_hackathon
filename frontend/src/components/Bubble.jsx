// 담당: 공용 — 최대 3개 질문과 닫기 제어
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import { closeBubble } from '../api/client.js';
export default function Bubble() {
  const { bubble, setBubble, navigate } = useNavigation();
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  if (!bubble) return null;
  async function close(action) { setBusy(true); setError(''); try { await closeBubble(bubble.screenId, action); setBubble(null); } catch (e) { setError(e.message); } finally { setBusy(false); } }
  return <aside className="bubble" aria-label="AI 도움 제안"><div className="bubble-title"><span className="ai-mark">W</span><div><strong>혹시 이걸 찾고 계세요?</strong><small>WON테이크가 길을 안내해 드려요</small></div></div><div className="suggestions">{bubble.suggestions.slice(0, 3).map(s => <button key={s.intentId} data-track-id={`suggestion-${s.intentId}`} onClick={() => navigate('chat', s)}>{s.question}<span>↗</span></button>)}</div>{error && <p role="alert">{error}</p>}<div className="bubble-actions"><button disabled={busy} onClick={() => close('NEVER')}>다시 띄우지 않기</button><button disabled={busy} onClick={() => close('CLOSE')}>닫기</button></div></aside>;
}
