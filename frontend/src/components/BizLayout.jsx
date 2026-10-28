// 담당: 공용 — R5 업무 화면 틀. 플랜 진행 띠, 가상 시연·브랜드 안내, 처리 결과 문구를 같은 모양으로 보여 준다.
import React, { useEffect } from 'react';
import BackButton from '../layout/BackButton.jsx';
import { useNavigation } from '../navigation/index.jsx';

/** 플랜 블록에서 들어왔을 때만 보인다. 화면에서 처리하면 같은 상태로 완료 수가 바뀐다. */
export function PlanBanner({ state }) {
  const { params, navigate, notePlanProgress } = useNavigation();
  const progress = params.planId ? state?.plans?.[params.planId] : null;
  // 업무 화면에서 마지막 단계를 끝내고 바로 다른 메뉴로 가도 플랜을 마친 것으로 알 수 있게 알려 준다.
  useEffect(() => {
    if (progress) notePlanProgress(params.planId, progress.complete);
  }, [progress?.complete, params.planId, notePlanProgress]);
  if (!progress) return null;
  // 필수 블록을 다 해도 필수 목표가 확보되지 않으면 완료로 보이지 않는다(R5 목표 중심).
  const done = Boolean(progress.complete);
  return (
    <div className={`plan-banner${done ? ' done' : ''}`} role="status">
      <span>
        원테이크 플랜 {done ? '완료' : `필수 ${progress.done}/${progress.total}`}
        {progress.events?.length > 0 && ` · ${progress.events.join(' · ')}`}
      </span>
      <button type="button" data-track-id="plan-back" onClick={() => navigate('plan', { planId: params.planId })}>
        플랜으로 돌아가기
      </button>
    </div>
  );
}

/** 플랜에서 들어와 처리를 마치면 화면 아래에 다음 단계로 가는 큰 버튼을 둔다(디지털 취약 계층 기준). */
function NextStep() {
  const { params, navigate } = useNavigation();
  if (!params.planId) return null;
  return (
    <button className="primary biz-next" type="button" data-track-id="plan-next" onClick={() => navigate('plan', { planId: params.planId })}>
      다음 단계로
    </button>
  );
}

export default function BizLayout({ category, title, lead, state, error, message, children, brands = false }) {
  return (
    <section className="page biz-page">
      <BackButton />
      <PlanBanner state={state} />
      <p className="eyebrow">{category}</p>
      <h1>{title}</h1>
      {lead && <p className="muted">{lead}</p>}
      {message && <p className="biz-message" role="status">{message}</p>}
      {message && <NextStep />}
      {error && <p className="error" role="alert">{error}</p>}
      {!state && !error ? <p className="muted">불러오고 있어요.</p> : state && children}
      <p className="biz-notice">
        가상 시연이며 실제 카드·결제·환전·보험에는 영향이 없습니다.
        {brands && ' 브랜드명은 시연용 예시이며 실제 제휴·연동은 없습니다.'}
      </p>
    </section>
  );
}

/** 대상 카드(플랜에서 넘어온 targetRef)를 맨 앞에 두고 강조한다. */
export function orderCards(cards, targetRef) {
  return [...cards].sort((a, b) => (b.ref === targetRef) - (a.ref === targetRef));
}

export function StatusBadge({ tone = 'normal', children }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}
