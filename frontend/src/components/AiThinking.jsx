// 담당: 공용 — AI가 판단하는 동안 보여 주는 대화형 로딩(로봇 + 입력 중 말풍선). 원테이크·AI 대화에서 같이 쓴다.
import React, { useEffect, useState } from 'react';
import RobotIcon from './RobotIcon.jsx';
import { thinkingLine, THINKING_STEP_MS } from './aiThinking.js';

export default function AiThinking({ kind = 'review', compact = false }) {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    setTick(0);
    const timer = setInterval(() => setTick((value) => value + 1), THINKING_STEP_MS);
    return () => clearInterval(timer);
  }, [kind]);
  const line = thinkingLine(kind, tick);
  return (
    <div className={`ai-thinking${compact ? ' compact' : ''}`} role="status" aria-live="polite">
      <span className="ai-thinking-avatar" aria-hidden="true">
        <RobotIcon size={compact ? 20 : 28} />
      </span>
      <div className="ai-thinking-bubble">
        <small>WON TAKE</small>
        <p key={line}>{line}</p>
        <span className="ai-typing" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
      </div>
    </div>
  );
}
