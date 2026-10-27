// 담당: R5 업무 화면 — 구독·자동결제 관리 (서비스를 골라 새 카드로 변경 또는 해지, 가상 상태만 바꿈)
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import Popup from '../components/Popup.jsx';
import BizLayout, { orderCards, StatusBadge } from '../components/BizLayout.jsx';
import { useDemoState } from '../demo/useDemoState.js';
import { won } from '../demo/fx.js';

const STATUS = { ACTIVE: ['이용 중', 'normal'], SWITCHED: ['새 카드로 변경됨', 'ok'], CANCELLED: ['해지됨', 'stop'] };

export default function SubscriptionScreen() {
  const { params } = useNavigation();
  const { state, error, message, busy, act } = useDemoState();
  const [picked, setPicked] = useState({});
  const [confirm, setConfirm] = useState(null);

  const cards = orderCards((state?.cards ?? []).filter((card) => card.subscriptions.length > 0), params.targetRef);
  const toggle = (ref, id) =>
    setPicked((current) => {
      const ids = new Set(current[ref] ?? []);
      ids.has(id) ? ids.delete(id) : ids.add(id);
      return { ...current, [ref]: [...ids] };
    });

  return (
    <BizLayout category="카드" title="구독·자동결제 관리" lead="카드에 걸린 정기결제를 골라 새 카드로 옮기거나 해지해요."
      state={state} error={error} message={message} brands>
      {cards.length === 0 && <p className="muted">등록된 자동결제가 없어요.</p>}
      {cards.map((card) => {
        const active = card.subscriptions.filter((item) => item.status === 'ACTIVE');
        const selected = (picked[card.ref] ?? []).filter((id) => active.some((item) => item.id === id));
        const monthly = active.reduce((sum, item) => sum + item.amount, 0);
        return (
          <section key={card.ref} className={`biz-group${card.ref === params.targetRef ? ' focus' : ''}`}>
            <div className="biz-group-head">
              <strong>{card.display}</strong>
              {card.status === 'STOPPED' && <StatusBadge tone="stop">분실신고됨</StatusBadge>}
              {card.reissued && <StatusBadge tone="ok">새 카드 신청됨</StatusBadge>}
            </div>
            <p className="muted">이용 중 {active.length}건 · 매월 {won(monthly)}</p>
            <ul className="sub-list">
              {card.subscriptions.map((item) => {
                const [label, tone] = STATUS[item.status];
                return (
                  <li key={item.id}>
                    <label className={item.status !== 'ACTIVE' ? 'disabled' : ''}>
                      <input type="checkbox" disabled={item.status !== 'ACTIVE'} checked={selected.includes(item.id)}
                        onChange={() => toggle(card.ref, item.id)} />
                      <span className="sub-brand">{item.merchant}</span>
                      <span className="sub-meta">{item.category} · 매월 {item.billingDay}일 · {won(item.amount)}</span>
                    </label>
                    <StatusBadge tone={tone}>{label}</StatusBadge>
                  </li>
                );
              })}
            </ul>
            {!card.reissued && active.length > 0 && (
              <p className="muted">새 카드로 옮기려면 먼저 재발급을 신청해 주세요. 해지는 지금도 할 수 있어요.</p>
            )}
            <div className="biz-actions">
              <button className="primary" type="button" data-track-id="subscription-switch" disabled={busy || selected.length === 0}
                onClick={() => setConfirm({ card, ids: selected, choice: 'SWITCH' })}>
                선택한 구독 새 카드로 변경
              </button>
              <button className="secondary" type="button" data-track-id="subscription-cancel" disabled={busy || selected.length === 0}
                onClick={() => setConfirm({ card, ids: selected, choice: 'CANCEL' })}>
                선택한 구독 해지
              </button>
            </div>
          </section>
        );
      })}
      {confirm && (
        <Popup
          title={confirm.choice === 'SWITCH' ? '새 카드로 변경' : '구독 해지'}
          onClose={() => setConfirm(null)}
          buttons={[
            { label: '취소', onClick: () => setConfirm(null) },
            {
              label: busy ? '처리 중' : confirm.choice === 'SWITCH' ? '변경하기' : '해지하기',
              primary: true,
              onClick: async () => {
                const choices = Object.fromEntries(confirm.ids.map((id) => [id, confirm.choice]));
                const names = confirm.card.subscriptions.filter((s) => confirm.ids.includes(s.id)).map((s) => s.merchant).join(', ');
                const ok = await act({ action: 'SUBSCRIPTION', ref: confirm.card.ref, choices },
                  `${names} ${confirm.choice === 'SWITCH' ? '결제 카드를 새 카드로 바꿨어요' : '구독을 해지했어요'}(가상).`);
                setConfirm(null);
                if (ok) setPicked((current) => ({ ...current, [confirm.card.ref]: [] }));
              },
            },
          ]}
        >
          <p>{confirm.card.display}</p>
          <ul>
            {confirm.card.subscriptions.filter((s) => confirm.ids.includes(s.id)).map((s) => (
              <li key={s.id}>{s.merchant} · {won(s.amount)}</li>
            ))}
          </ul>
          {confirm.choice === 'CANCEL' && <p className="muted">해지한 서비스는 다시 가입해야 쓸 수 있어요.</p>}
        </Popup>
      )}
    </BizLayout>
  );
}
