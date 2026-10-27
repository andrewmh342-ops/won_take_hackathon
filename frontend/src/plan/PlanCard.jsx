// 담당: 역할 4 — AI 대화 안에 보여 주는 이체 작업 카드 목록(실제 이체 없음)
import React from 'react';
import { won, riskBadge, taskNote, blockerNotes } from './planView.js';

export default function PlanCard({ plan }) {
  if (plan.tasks.length === 0) return null;
  const notes = blockerNotes(plan.blockers);
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
      <small className="plan-foot">승인 단계는 준비 중이에요.</small>
    </section>
  );
}
