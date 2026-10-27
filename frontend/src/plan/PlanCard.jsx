// 담당: 역할 4 — AI 대화 안에 보여 주는 이체 작업 카드 목록과 승인(가상 잔액만 줄고 실제 이체 없음)
import React, { useState } from 'react';
import { confirmPlan } from '../api/client.js';
import { won, riskBadge, taskNote, blockerNotes, hasWarning, confirmedNote, confirmError } from './planView.js';

export default function PlanCard({ plan }) {
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  if (plan.tasks.length === 0) return null;
  const notes = blockerNotes(plan.blockers);

  // 승인할 수 있는지는 서버가 정한다(canConfirm). 승인할 때도 서버가 저장된 목록을 다시 검사한다.
  const approve = async (event) => {
    event.preventDefault();
    if (busy || !password) return;
    setBusy(true);
    setError('');
    try {
      setResult(await confirmPlan(plan.planId, password));
    } catch (requestError) {
      setError(confirmError(requestError));
    } finally {
      setPassword('');
      setBusy(false);
    }
  };

  return (
    <section className="plan-card">
      <div className="plan-summary">
        <div>
          <small>보낼 돈</small>
          <strong>{won(plan.totalAmount)}</strong>
        </div>
        <div>
          <small>보낸 뒤 잔액</small>
          <strong className={plan.expectedBalance < 0 ? 'warn' : ''}>{won(plan.expectedBalance)}</strong>
        </div>
      </div>
      <ol className="plan-tasks">
        {plan.tasks.map((task) => {
          const badge = riskBadge(task.risk);
          return (
            <li key={task.order} className={task.status === 'READY' ? '' : 'pending'}>
              <div className="plan-task-head">
                <strong>{task.target}</strong>
                <span className={`plan-badge${badge.warn ? ' warn' : ''}`}>{badge.label}</span>
              </div>
              <p className="plan-amount">{task.amount > 0 ? won(task.amount) : '금액 미정'}</p>
              <small>{[task.account || '계좌 없음', task.memo].filter(Boolean).join(' · ')}</small>
              <p className={`plan-note${task.status === 'READY' && !badge.warn ? '' : ' warn'}`}>{taskNote(task)}</p>
            </li>
          );
        })}
      </ol>
      {notes.length > 0 && (
        <ul className="plan-blockers">
          {notes.map((note) => <li key={note}>{note}</li>)}
        </ul>
      )}
      {result ? (
        <p className="plan-done" role="status">{confirmedNote(result)}</p>
      ) : (
        plan.canConfirm && (
          <form className="plan-confirm" onSubmit={approve}>
            {hasWarning(plan.tasks) && <p className="plan-note warn">주의 표시가 있는 작업이 있어요. 한 번 더 확인해 주세요.</p>}
            <label className="field">
              간편 비밀번호
              <input
                type="password"
                inputMode="numeric"
                autoComplete="off"
                maxLength={12}
                value={password}
                placeholder="숫자 비밀번호"
                data-track-id="plan-password"
                onChange={(event) => setPassword(event.target.value.replace(/\D/g, ''))}
              />
            </label>
            {error && <p className="plan-note warn" role="alert">{error}</p>}
            <button className="primary full" type="submit" disabled={busy || !password} data-track-id="plan-confirm">
              {busy ? '확인하는 중' : `${won(plan.totalAmount)} 승인하기`}
            </button>
            {/* 시연에는 지문 인증이 없다. 모양만 두고 비밀번호로 안내한다. */}
            <button className="secondary full" type="button" onClick={() => setError('시연에서는 지문 대신 비밀번호로 확인해요.')}>
              지문으로 승인
            </button>
          </form>
        )
      )}
      <small className="plan-foot">실제 돈은 움직이지 않아요. 승인하면 가상 잔액만 줄어요.</small>
    </section>
  );
}
