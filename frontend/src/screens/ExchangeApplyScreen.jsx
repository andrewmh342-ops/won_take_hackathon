// 담당: R5 업무 화면 — 환전 신청 (원 미만 절사, 1회 200만 원, 수령일 내일~30일 뒤. 가상 신청만)
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import Popup from '../components/Popup.jsx';
import BizLayout from '../components/BizLayout.jsx';
import { useDemoState } from '../demo/useDemoState.js';
import { overLimit, toKrw, won } from '../demo/fx.js';

const addDays = (iso, days) => {
  const date = new Date(`${iso}T00:00:00`);
  date.setDate(date.getDate() + days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};

export default function ExchangeApplyScreen() {
  const { params } = useNavigation();
  const { state, error, message, busy, act } = useDemoState();
  const [code, setCode] = useState(params.param || 'JPY');
  const [amount, setAmount] = useState(params.amount ? String(params.amount) : '');
  const [branch, setBranch] = useState('');
  const [account, setAccount] = useState('');
  const [date, setDate] = useState('');
  const [confirm, setConfirm] = useState(false);

  if (!state) return <BizLayout category="환전" title="환전 신청" state={state} error={error} message={message} />;
  const rate = state.fxRates.find((item) => item.code === code);
  const value = Number(amount);
  const krw = toKrw(value, rate);
  const min = addDays(state.today, 1);
  const max = addDays(state.today, 30);
  const form = { branch: branch || state.branches[0].id, account: account || state.accounts[0].id, date: date || min };
  const invalid = krw === null ? '외화 금액은 1 이상 정수로 입력해 주세요.' : overLimit(krw) ? '1회 환전은 200만 원까지예요(가상 한도).' : '';

  return (
    <BizLayout category="환전" title="환전 신청" lead="여행 전에 외화 현금을 신청하고 공항이나 영업점에서 받아요."
      state={state} error={error} message={message}>
      <form className="biz-form" onSubmit={(event) => { event.preventDefault(); if (!invalid) setConfirm(true); }}>
        <label>
          통화
          <select value={code} onChange={(event) => setCode(event.target.value)}>
            {state.fxRates.map((item) => <option key={item.code} value={item.code}>{item.code} · {item.name}</option>)}
          </select>
        </label>
        <p className="muted">시연용 가상 환율: {rate.unit.toLocaleString('ko-KR')} {code} = {won(rate.krw)}</p>
        <label>
          외화 금액 ({code})
          <input inputMode="numeric" placeholder="예: 30000" value={amount} onChange={(event) => setAmount(event.target.value.replace(/[^0-9]/g, ''))} />
        </label>
        <p className={`fx-result${invalid && amount ? ' warn' : ''}`} role="status">
          {amount ? invalid || `출금 예정 금액 ${won(krw)} (원 미만 버림)` : '금액을 입력하면 원화로 계산해 드려요.'}
        </p>
        <label>
          받을 곳
          <select value={form.branch} onChange={(event) => setBranch(event.target.value)}>
            {state.branches.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </label>
        <label>
          받는 날 (내일~30일 뒤)
          <input type="date" min={min} max={max} value={form.date} onChange={(event) => setDate(event.target.value)} />
        </label>
        <label>
          출금 계좌
          <select value={form.account} onChange={(event) => setAccount(event.target.value)}>
            {state.accounts.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </label>
        <button className="primary full" type="submit" disabled={busy || !!invalid} data-track-id="exchange-apply">환전 신청</button>
      </form>

      {state.exchangeRequests.length > 0 && (
        <section className="biz-group">
          <div className="biz-group-head"><strong>신청 내역</strong></div>
          <ul className="sub-list">
            {state.exchangeRequests.map((item) => (
              <li key={item.id}>
                <span className="sub-brand">{item.amount.toLocaleString('ko-KR')} {item.currency} · {won(item.krw)}</span>
                <span className="sub-meta">{item.date} · {item.branch}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {confirm && (
        <Popup
          title="환전 신청 확인"
          onClose={() => setConfirm(false)}
          buttons={[
            { label: '취소', onClick: () => setConfirm(false) },
            {
              label: busy ? '처리 중' : '신청하기',
              primary: true,
              onClick: async () => {
                const ok = await act({ action: 'EXCHANGE_APPLY', currency: code, amount: value, ...form },
                  `${value.toLocaleString('ko-KR')} ${code} 환전을 신청했어요(가상).`);
                setConfirm(false);
                if (ok) setAmount('');
              },
            },
          ]}
        >
          <dl className="biz-dl">
            <div><dt>외화</dt><dd>{value.toLocaleString('ko-KR')} {code}</dd></div>
            <div><dt>출금 예정</dt><dd>{won(krw)}</dd></div>
            <div><dt>받을 곳</dt><dd>{state.branches.find((b) => b.id === form.branch)?.name}</dd></div>
            <div><dt>받는 날</dt><dd>{form.date}</dd></div>
          </dl>
        </Popup>
      )}
    </BizLayout>
  );
}
