// 담당: 공용 — 대화 안의 원테이크 요약 카드(SPEC R5 플랜 화면). 사건 제목·할 일 수·버튼 하나만 둔다.
import React from 'react';
import { planSteps, finishState } from '../demo/planSteps.js';

export default function PlanCard({ plan, onOpen }) {
  const steps = planSteps(plan).filter((step) => step.kind !== 'FINISH');
  const done = steps.filter((step) => step.done).length;
  const state = finishState(plan);
  const shown = steps.slice(0, 3);
  const button = state === 'COMPLETE' ? '결과 보기' : done > 0 ? '이어서 하기' : '지금 해결하기';
  return (
    <section className="plan-summary" aria-label="원테이크 플랜">
      <p className="plan-summary-title">{plan.events.join(' · ') || '해결할 일'}</p>
      <p className="plan-summary-count">
        {state === 'COMPLETE' ? '모두 끝냈어요 ✓' : done > 0 ? `${steps.length}단계 중 ${done}단계 끝냈어요` : `${steps.length}단계면 정리돼요`}
      </p>
      <ol className="plan-summary-steps">
        {shown.map((step, i) => (
          <li key={step.key} className={step.done ? 'done' : ''}>
            <span aria-hidden="true">{step.done ? '✓' : i + 1}</span>{step.label}
          </li>
        ))}
        {steps.length > shown.length && <li className="more">외 {steps.length - shown.length}개</li>}
      </ol>
      <button className="primary plan-summary-go" type="button" data-track-id="plan-open" onClick={() => onOpen(plan)}>
        {button}
      </button>
    </section>
  );
}
