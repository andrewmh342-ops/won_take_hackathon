// 담당: R5 업무 화면 — 간편결제 관리 (등록 카드 해제, 가상 상태만 바꿈)
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import Popup from '../components/Popup.jsx';
import BizLayout, { orderCards, StatusBadge } from '../components/BizLayout.jsx';
import { useDemoState } from '../demo/useDemoState.js';

export default function SimplePayScreen() {
  const { params } = useNavigation();
  const { state, error, message, busy, act } = useDemoState();
  const [confirm, setConfirm] = useState(null);
  const registered = orderCards((state?.cards ?? []).filter((card) => card.simplePayName), params.targetRef);

  return (
    <BizLayout category="카드" title="간편결제 관리" lead="휴대폰 간편결제에 등록된 카드를 확인하고 해제해요." state={state} error={error} message={message} brands>
      {registered.length === 0 && <p className="muted">간편결제에 등록된 카드가 없어요.</p>}
      <ul className="biz-list">
        {registered.map((card) => (
          <li key={card.ref} className={`biz-card${card.ref === params.targetRef ? ' focus' : ''}`}>
            <div className="wallet-mark" aria-hidden="true">{card.simplePayName.slice(0, 2)}</div>
            <div className="biz-card-body">
              <strong>{card.simplePayName}</strong>
              <p className="muted">{card.display}</p>
              <p>{card.simplePay ? <StatusBadge tone="info">등록됨</StatusBadge> : <StatusBadge tone="ok">해제됨</StatusBadge>}
                {card.status === 'STOPPED' && <StatusBadge tone="stop">카드 정지됨</StatusBadge>}</p>
            </div>
            <button className="primary" type="button" disabled={busy || !card.simplePay} data-track-id="simple-pay-unlink" onClick={() => setConfirm(card)}>
              {card.simplePay ? '해제' : '해제 완료'}
            </button>
          </li>
        ))}
      </ul>
      {confirm && (
        <Popup
          title="간편결제 해제"
          onClose={() => setConfirm(null)}
          buttons={[
            { label: '취소', onClick: () => setConfirm(null) },
            {
              label: busy ? '처리 중' : '해제하기',
              primary: true,
              onClick: async () => {
                const ok = await act({ action: 'SIMPLE_PAY_UNLINK', ref: confirm.ref }, `${confirm.simplePayName}에서 ${confirm.name}을 해제했어요(가상).`);
                if (ok) setConfirm(null);
              },
            },
          ]}
        >
          <p><strong>{confirm.simplePayName}</strong> · {confirm.display}</p>
          <p>해제하면 휴대폰으로 이 카드 결제가 되지 않아요.</p>
        </Popup>
      )}
    </BizLayout>
  );
}
