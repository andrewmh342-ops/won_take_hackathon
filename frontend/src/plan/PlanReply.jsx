import React, { useRef, useState } from 'react';
import PlanCard from './PlanCard.jsx';
import { amountMessage } from './amountInput.js';

// 긴 카드의 끝으로 자동 스크롤해도, 다음에 답할 금액 질문이 입력창 가까이에 남도록 한다.
export default function PlanReply({ text, plan, onFill, active = true, disabled = false }) {
  const [amounts, setAmounts] = useState({});
  const sending = useRef(false);
  const askAfterCard = plan?.blockers.includes('NEEDS_AMOUNT');
  const targets = [...new Set(plan?.tasks.filter((task) => task.status === 'NEEDS_AMOUNT').map((task) => task.target))];
  const message = amountMessage(targets, amounts);
  async function submitAmounts(event) {
    event.preventDefault();
    if (!message || disabled || sending.current) return;
    sending.current = true;
    try {
      // 승인 API가 아니라 기존 대화로 보낸다. 빈 금액만 채운 새 목록을 서버가 만든다.
      await onFill(message);
    } finally {
      sending.current = false;
    }
  }
  return (
    <>
      {!askAfterCard && <p>{text}</p>}
      {plan && <PlanCard plan={plan} />}
      {askAfterCard && <p>{text}</p>}
      {askAfterCard && active && onFill && (
        <form className="plan-confirm" onSubmit={submitAmounts}>
          {targets.map((target) => (
            <label className="field" key={target}>
              {target} 이체 금액(원)
              <input
                type="text"
                inputMode="numeric"
                autoComplete="off"
                maxLength={19}
                placeholder="예: 650000"
                value={amounts[target] ?? ''}
                disabled={disabled}
                onChange={(event) => setAmounts((current) => ({ ...current, [target]: event.target.value }))}
                data-track-id="plan-amount-input"
              />
            </label>
          ))}
          <button className="primary full" type="submit" disabled={disabled || !message} data-track-id="plan-amount-submit">
            {disabled ? '확인하는 중' : '금액 입력'}
          </button>
        </form>
      )}
    </>
  );
}
