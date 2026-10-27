// 담당: R5 업무 화면 — 트래블카드 확인·신청 (가상 상태만 바꿈)
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import Popup from '../components/Popup.jsx';
import BizLayout, { StatusBadge } from '../components/BizLayout.jsx';
import { useDemoState } from '../demo/useDemoState.js';

export default function TravelCardScreen() {
  const { params, navigate } = useNavigation();
  const { state, error, message, busy, act } = useDemoState();
  const [account, setAccount] = useState('');
  const [receive, setReceive] = useState('MAIL');
  const [confirm, setConfirm] = useState(false);
  const travelCards = (state?.cards ?? []).filter((card) => card.travel);
  const application = state?.travelCardApplication;
  const accountId = account || state?.accounts?.[0]?.id || '';

  return (
    <BizLayout category="카드" title="트래블카드" lead="해외에서 결제·출금할 때 쓰는 카드예요. 있으면 해외결제 설정만, 없으면 여기서 신청해요."
      state={state} error={error} message={message}>
      {travelCards.length > 0 ? (
        <ul className="biz-list">
          {travelCards.map((card) => (
            <li key={card.ref} className="biz-card focus">
              <div className="card-face travel" aria-hidden="true"><span>트래블</span><strong>•••• {card.last4}</strong></div>
              <div className="biz-card-body">
                <strong>{card.name}</strong>
                <p className="muted">끝자리 {card.last4}(가상) · 보유 중</p>
                <p>{card.overseasPayment ? <StatusBadge tone="ok">해외결제 켜짐</StatusBadge> : <StatusBadge>해외결제 꺼짐</StatusBadge>}</p>
              </div>
              {!card.overseasPayment && (
                <button className="primary" type="button" data-track-id="travel-go-overseas"
                  onClick={() => navigate('overseas', { menuId: 'overseas-payment', targetRef: card.ref, planId: params.planId })}>
                  해외결제 켜러 가기
                </button>
              )}
            </li>
          ))}
        </ul>
      ) : application ? (
        <div className="biz-done">
          <StatusBadge tone="ok">신청 완료</StatusBadge>
          <dl className="biz-dl">
            <div><dt>상태</dt><dd>{application.status}</dd></div>
            <div><dt>연결 계좌</dt><dd>{application.account}</dd></div>
            <div><dt>수령 방법</dt><dd>{application.receive}</dd></div>
            <div><dt>신청일</dt><dd>{application.appliedOn}</dd></div>
          </dl>
          <p className="muted">카드를 받는 데 시간이 걸리니 출국 전에 수령 일정을 확인해 주세요.</p>
        </div>
      ) : (
        <form className="biz-form" onSubmit={(event) => { event.preventDefault(); setConfirm(true); }}>
          <p>보유한 트래블카드가 없어요. 새로 신청할 수 있어요.</p>
          <label>
            연결할 출금 계좌
            <select value={accountId} onChange={(event) => setAccount(event.target.value)}>
              {state?.accounts.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </label>
          <fieldset>
            <legend>카드 받는 방법</legend>
            <label><input type="radio" name="receive" checked={receive === 'MAIL'} onChange={() => setReceive('MAIL')} /> 우편으로 받기 ({state?.address})</label>
            <label><input type="radio" name="receive" checked={receive === 'BRANCH'} onChange={() => setReceive('BRANCH')} /> 영업점에서 받기</label>
          </fieldset>
          <button className="primary full" type="submit" disabled={busy} data-track-id="travel-card-apply">트래블카드 신청</button>
        </form>
      )}
      {confirm && (
        <Popup
          title="트래블카드 신청"
          onClose={() => setConfirm(false)}
          buttons={[
            { label: '취소', onClick: () => setConfirm(false) },
            {
              label: busy ? '처리 중' : '신청하기',
              primary: true,
              onClick: async () => {
                await act({ action: 'TRAVEL_CARD_APPLY', account: accountId, receive }, '트래블카드를 신청했어요. 발급 대기 중이에요(가상).');
                setConfirm(false);
              },
            },
          ]}
        >
          <dl className="biz-dl">
            <div><dt>연결 계좌</dt><dd>{state.accounts.find((a) => a.id === accountId)?.name}</dd></div>
            <div><dt>수령 방법</dt><dd>{receive === 'MAIL' ? '우편 수령' : '영업점 수령'}</dd></div>
          </dl>
        </Popup>
      )}
    </BizLayout>
  );
}
