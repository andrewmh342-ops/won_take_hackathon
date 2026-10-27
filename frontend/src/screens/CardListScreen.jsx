// 담당: R5 업무 화면 — 내 카드·분실신고 (가상 상태만 바꿈)
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import Popup from '../components/Popup.jsx';
import BizLayout, { orderCards, StatusBadge } from '../components/BizLayout.jsx';
import { useDemoState } from '../demo/useDemoState.js';

export default function CardListScreen() {
  const { params, navigate } = useNavigation();
  const { state, error, message, busy, act } = useDemoState();
  const [confirm, setConfirm] = useState(null);
  const planParams = { planId: params.planId };

  return (
    <BizLayout category="카드" title="내 카드" lead="잃어버린 카드를 골라 바로 사용을 멈출 수 있어요." state={state} error={error} message={message} brands>
      <ul className="biz-list">
        {orderCards(state?.cards ?? [], params.targetRef).map((card) => {
          const stopped = card.status === 'STOPPED';
          return (
            <li key={card.ref} className={`biz-card${card.ref === params.targetRef ? ' focus' : ''}`}>
              <div className={`card-face${card.travel ? ' travel' : ''}${stopped ? ' stopped' : ''}`} aria-hidden="true">
                <span>{card.kind}</span>
                <strong>•••• {card.last4}</strong>
              </div>
              <div className="biz-card-body">
                <strong>{card.name}</strong>
                <p className="muted">끝자리 {card.last4}(가상) · {card.kind}카드{card.travel ? ' · 트래블' : ''}</p>
                <p>
                  {stopped ? <StatusBadge tone="stop">사용 정지됨</StatusBadge> : <StatusBadge>사용 중</StatusBadge>}
                  {card.reissued && <StatusBadge tone="ok">재발급 신청됨</StatusBadge>}
                  {card.simplePay && <StatusBadge tone="info">{card.simplePayName} 등록</StatusBadge>}
                  {card.subscriptions.length > 0 && <StatusBadge tone="info">자동결제 {card.subscriptions.length}건</StatusBadge>}
                </p>
              </div>
              {stopped ? (
                <button className="secondary" type="button" data-track-id="card-go-reissue"
                  onClick={() => navigate('card-reissue', { menuId: 'card-reissue', targetRef: card.ref, ...planParams })}>
                  {card.reissued ? '재발급 확인' : '재발급 신청하기'}
                </button>
              ) : (
                <button className="primary" type="button" disabled={busy} data-track-id="card-stop" onClick={() => setConfirm(card)}>
                  분실신고
                </button>
              )}
            </li>
          );
        })}
      </ul>
      {confirm && (
        <Popup
          title="카드 분실신고"
          onClose={() => setConfirm(null)}
          buttons={[
            { label: '취소', onClick: () => setConfirm(null) },
            {
              label: busy ? '처리 중' : '사용 정지',
              primary: true,
              onClick: async () => {
                const ok = await act({ action: 'CARD_STOP', ref: confirm.ref }, `${confirm.name}(끝자리 ${confirm.last4}) 사용을 정지했어요.`);
                if (ok) setConfirm(null);
              },
            },
          ]}
        >
          <p><strong>{confirm.display}</strong></p>
          <p>지금부터 이 카드로 결제·출금이 되지 않아요. 분실신고를 해야 재발급을 신청할 수 있어요.</p>
        </Popup>
      )}
    </BizLayout>
  );
}
