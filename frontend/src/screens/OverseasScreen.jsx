// 담당: R5 업무 화면 — 해외결제 설정 (카드별 켜기·끄기, 가상 상태만 바꿈)
import React from 'react';
import { useNavigation } from '../navigation/index.jsx';
import BizLayout, { orderCards, StatusBadge } from '../components/BizLayout.jsx';
import { useDemoState } from '../demo/useDemoState.js';

export default function OverseasScreen() {
  const { params } = useNavigation();
  const { state, error, message, busy, act } = useDemoState();

  return (
    <BizLayout category="카드" title="해외결제 설정" lead="해외에서 카드 결제가 되도록 카드마다 켜거나 꺼요. 여행이 끝나면 다시 꺼 두면 안전해요."
      state={state} error={error} message={message}>
      <ul className="biz-list">
        {orderCards(state?.cards ?? [], params.targetRef).map((card) => (
          <li key={card.ref} className={`biz-card${card.ref === params.targetRef ? ' focus' : ''}`}>
            <div className="biz-card-body">
              <strong>{card.display}</strong>
              <p>
                {card.overseasPayment ? <StatusBadge tone="ok">해외결제 켜짐</StatusBadge> : <StatusBadge>해외결제 꺼짐</StatusBadge>}
                {card.travel && <StatusBadge tone="info">트래블카드</StatusBadge>}
                {card.status === 'STOPPED' && <StatusBadge tone="stop">정지됨</StatusBadge>}
              </p>
            </div>
            <button
              className={card.overseasPayment ? 'secondary' : 'primary'}
              type="button"
              role="switch"
              aria-checked={card.overseasPayment}
              disabled={busy || card.status === 'STOPPED'}
              data-track-id="overseas-toggle"
              onClick={() =>
                act({ action: 'OVERSEAS', ref: card.ref, on: !card.overseasPayment },
                  `${card.name} 해외결제를 ${card.overseasPayment ? '껐어요' : '켰어요'}(가상).`)
              }
            >
              {card.overseasPayment ? '끄기' : '켜기'}
            </button>
          </li>
        ))}
      </ul>
    </BizLayout>
  );
}
