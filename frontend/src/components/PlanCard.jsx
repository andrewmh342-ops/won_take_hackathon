// 담당: 공용 — R5 원테이크 플랜 카드. AI가 고른 업무 블록을 순서대로 보여 주고 버튼으로 하나씩 끝낸다.
import React from 'react';

const BUTTON = { ACTION: '화면에서 처리', CHECK: '확인하러 가기', LINK: '화면 열기', HANDOFF: '상담 연결' };

export default function PlanCard({ plan, busy, onRun }) {
  const total = plan.blocks.length;
  const finished = plan.blocks.filter((block) => block.status !== 'PENDING').length;
  return (
    <section className="plan-card" aria-label="원테이크 플랜">
      <p className="plan-eyebrow">원테이크 플랜</p>
      {plan.events.length > 0 && <h3>{plan.events.join(' · ')}</h3>}
      {plan.orderReason && <p className="plan-reason">순서 이유: {plan.orderReason}</p>}
      <p className="plan-progress" role="status">
        {finished === total ? '원테이크 완료! 모든 일을 끝냈어요.' : `${total}개 중 ${finished}개 완료`}
      </p>
      <ol className="plan-steps">
        {plan.blocks.map((block) => {
          const pending = block.status === 'PENDING';
          return (
            <li key={block.index} className={`plan-step${pending ? '' : ' done'}`}>
              <span className="plan-number" aria-hidden="true">{pending ? block.index + 1 : '✓'}</span>
              <div className="plan-body">
                <strong>{block.name}</strong>
                {block.adjusted && <small className="plan-adjusted">순서 조정됨</small>}
                {block.target && <p className="plan-target">{block.target}</p>}
                {block.reason && <p className="plan-why">{block.reason}</p>}
                {block.detail && <p className="plan-detail">{block.detail}</p>}
              </div>
              {/* 끝난 블록도 화면을 다시 볼 수 있다(상담 블록은 한 번만). */}
              <button
                className={pending ? 'primary plan-run' : 'secondary plan-run'}
                type="button"
                data-track-id={`plan-${block.blockId}`}
                disabled={busy || (!pending && block.type === 'HANDOFF')}
                onClick={() => onRun(plan, block)}
              >
                {pending ? BUTTON[block.type] : `완료 · ${block.type === 'HANDOFF' ? '전달함' : '화면 보기'}`}
              </button>
            </li>
          );
        })}
      </ol>
      {plan.unhandled.length > 0 && (
        <p className="plan-unhandled">
          앱에서 처리할 수 없어 상담사에게 넘길 일: {plan.unhandled.join(', ')}
        </p>
      )}
      <details className="plan-ai-input">
        <summary>AI에 보낸 정보 보기 (이름·카드번호·금액 제외)</summary>
        <pre>{JSON.stringify(plan.aiInput, null, 2)}</pre>
      </details>
    </section>
  );
}
