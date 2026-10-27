// 담당: R5 업무 화면 — 카드 재발급 (분실신고한 카드만, 가상 상태만 바꿈)
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import Popup from '../components/Popup.jsx';
import BizLayout, { orderCards, StatusBadge } from '../components/BizLayout.jsx';
import { useDemoState } from '../demo/useDemoState.js';

export default function CardReissueScreen() {
  const { params, navigate } = useNavigation();
  const { state, error, message, busy, act } = useDemoState();
  const [confirm, setConfirm] = useState(null);

  return (
    <BizLayout category="카드" title="카드 재발급" lead="분실신고한 카드와 같은 상품으로 새 카드를 받아요." state={state} error={error} message={message}>
      <ul className="biz-list">
        {orderCards(state?.cards ?? [], params.targetRef).map((card) => {
          const stopped = card.status === 'STOPPED';
          return (
            <li key={card.ref} className={`biz-card${card.ref === params.targetRef ? ' focus' : ''}`}>
              <div className="biz-card-body">
                <strong>{card.display}</strong>
                <p>
                  {card.reissued ? <StatusBadge tone="ok">재발급 신청 완료</StatusBadge>
                    : stopped ? <StatusBadge tone="stop">분실신고됨</StatusBadge> : <StatusBadge>사용 중</StatusBadge>}
                </p>
                {card.reissued && <p className="muted">예상 수령: 3~5영업일(가상) · {state.address}</p>}
                {!stopped && <p className="muted">사용 중인 카드는 먼저 분실신고를 해야 재발급할 수 있어요.</p>}
              </div>
              {card.reissued ? (
                card.subscriptions.length > 0 && (
                  <button className="secondary" type="button" data-track-id="reissue-go-subscription"
                    onClick={() => navigate('subscription', { menuId: 'auto-payment', targetRef: card.ref, planId: params.planId })}>
                    자동결제 옮기기
                  </button>
                )
              ) : (
                <button className="primary" type="button" disabled={busy || !stopped} data-track-id="card-reissue" onClick={() => setConfirm(card)}>
                  재발급 신청
                </button>
              )}
            </li>
          );
        })}
      </ul>
      {confirm && (
        <Popup
          title="카드 재발급 신청"
          onClose={() => setConfirm(null)}
          buttons={[
            { label: '취소', onClick: () => setConfirm(null) },
            {
              label: busy ? '처리 중' : '신청하기',
              primary: true,
              onClick: async () => {
                const ok = await act({ action: 'CARD_REISSUE', ref: confirm.ref }, `${confirm.name} 재발급을 신청했어요. 3~5영업일 안에 받아요(가상).`);
                if (ok) setConfirm(null);
              },
            },
          ]}
        >
          <p><strong>{confirm.display}</strong></p>
          <dl className="biz-dl">
            <div><dt>받을 주소</dt><dd>{state.address}</dd></div>
            <div><dt>예상 수령</dt><dd>3~5영업일(가상)</dd></div>
          </dl>
        </Popup>
      )}
    </BizLayout>
  );
}
