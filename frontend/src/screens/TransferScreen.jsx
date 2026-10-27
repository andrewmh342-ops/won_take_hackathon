// 담당: 역할 1 — 실제 거래를 실행하지 않는 이체 화면
import React, { useState } from 'react';
import Popup from '../components/Popup.jsx';
import BackButton from '../layout/BackButton.jsx';
import { useAccounts } from '../data/useAccounts.js';
import AccountStatus from '../components/AccountStatus.jsx';
export default function TransferScreen() {
  const [open, setOpen] = useState(false);
  const accountState = useAccounts();
  const withdrawalAccount = accountState.data?.accounts.find(account => account.withdrawal);
  return (
    <div className="page">
      <BackButton />
      <p className="eyebrow">TRANSFER</p>
      <h1>누구에게 보낼까요?</h1>
      <p className="muted">필요한 정보를 차근차근 입력해 주세요.</p>
      <div className="step-line">
        <span className="selected">1</span>
        <i />
        <span>2</span>
        <i />
        <span>3</span>
      </div>
      <AccountStatus {...accountState} />
      {withdrawalAccount && <section className="account-picker">
        <small>출금 계좌</small>
        <div>
          <span className="bank-icon">W</span>
          <span>
            <strong>{withdrawalAccount.name}</strong>
            <small>{withdrawalAccount.number} · 가상 계좌</small>
          </span>
        </div>
        <p>
          출금 가능 <strong>{withdrawalAccount.balance.toLocaleString('ko-KR')}원</strong>
        </p>
      </section>}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setOpen(true);
        }}
      >
        <label className="field">
          받는 사람
          <input
            autoComplete="off"
            placeholder="가상 수취인 입력"
            data-track-id="recipient-input"
          />
        </label>
        <label className="field">
          보낼 금액
          <div className="amount-input">
            <input
              inputMode="numeric"
              pattern="[0-9]*"
              placeholder="0"
              aria-label="보낼 금액"
              data-track-id="amount-input"
            />
            <span>원</span>
          </div>
        </label>
        <p className="info-note">
          이 화면은 시연용이에요.
          <br />
          실제 계좌 조회나 이체는 이루어지지 않아요.
        </p>
        <button className="primary full" data-track-id="transfer-next" type="submit">
          다음
        </button>
      </form>
      {open && (
        <Popup
          title="시연용 화면입니다"
          onClose={() => setOpen(false)}
          buttons={[{ label: '확인', primary: true, onClick: () => setOpen(false) }]}
        >
          실제 이체는 실행되지 않습니다.
        </Popup>
      )}
    </div>
  );
}
