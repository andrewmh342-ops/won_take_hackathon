// 담당: 공용 — 원테이크 화면(SPEC R5 플랜 화면). 위에는 단계 탭, 아래는 옆으로 넘기는 단계 카드. 한 화면에 할 일 하나.
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { getPlan, getDemoState, executePlan, confirmCards, answerPlan, reviewPlan } from '../api/client.js';
import { useNavigation } from '../navigation/index.jsx';
import { findMenu } from '../data/menu.js';
import AiThinking from '../components/AiThinking.jsx';
import {
  planSteps, currentStepIndex, planChanges, goalWarnings, finishState, optionalBlocks, dateChips, maxDate,
  actionLabel, stepTitle, stepLabel, isLoss, required,
} from '../demo/planSteps.js';

const ICON = {
  confirm: '🔍', 'card-lost-report': '🔒', 'simple-pay-unlink': '📱', 'card-reissue': '💳', 'auto-payment-switch': '🔁',
  'overseas-payment-check': '🌐', 'alt-payment-check': '💳', 'fx-rate-check': '💱', 'fx-exchange-apply': '💴',
  'travel-card-check': '✈️', 'travel-card-apply': '✈️', 'travel-insurance-guide': '🛡️', 'counselor-handoff': '🎧', 'ask-info': '📅',
};
const COUNTRIES = ['일본', '미국', '유럽', '중국'];

function CardFace({ card, small = false }) {
  if (!card) return null;
  return (
    <span className={`card-face${card.travel ? ' travel' : card.kind === '신용' ? ' credit' : ''}${small ? ' small' : ''}`} aria-hidden="true">
      <span>{card.travel ? '트래블' : card.kind}</span>
      <b>•••• {card.last4}</b>
    </span>
  );
}

function Icon({ name, tone = '' }) {
  return <span className={`take-icon ${tone}`} aria-hidden="true">{ICON[name] ?? '•'}</span>;
}

/** 분실 카드 확인: 보유 카드 타일을 눌러 고른다(AI 추정 카드는 미리 선택). */
function ConfirmPage({ plan, cards, busy, onConfirm }) {
  const [picked, setPicked] = useState(() => new Set(plan.suggestedCards ?? []));
  const toggle = (ref) => setPicked((current) => {
    const next = new Set(current);
    if (next.has(ref)) next.delete(ref); else next.add(ref);
    return next;
  });
  if (!plan.needsCardConfirm) {
    return (
      <Done title="잃어버린 카드를 확인했어요"
        detail={(plan.confirmedCards ?? []).map((ref) => cards?.find((c) => c.ref === ref)?.display ?? ref).join(', ')} />
    );
  }
  return (
    <>
      <div className="take-body">
        <Icon name="confirm" />
        <h2>어떤 카드를 잃어버리셨어요?</h2>
        <p className="take-sub">여러 장이면 모두 골라 주세요.</p>
        <div className="card-tiles">
          {(cards ?? []).map((card) => (
            <button key={card.ref} type="button" className={`card-tile${picked.has(card.ref) ? ' selected' : ''}`}
              aria-pressed={picked.has(card.ref)} data-track-id={`take-card-${card.ref}`} onClick={() => toggle(card.ref)}>
              <CardFace card={card} small />
              <span className="card-tile-name">{card.name}<small>끝자리 {card.last4}</small></span>
              <span className="card-tile-check" aria-hidden="true">{picked.has(card.ref) ? '✓' : ''}</span>
            </button>
          ))}
        </div>
      </div>
      <div className="take-actions">
        <button className="primary take-main" type="button" data-track-id="take-confirm" disabled={busy || picked.size === 0}
          onClick={() => onConfirm([...picked])}>
          {picked.size > 1 ? `이 ${picked.size}장으로 확인` : '이 카드로 확인'}
        </button>
      </div>
    </>
  );
}

function Done({ title, detail, children }) {
  return (
    <>
      <div className="take-body">
        <span className="take-icon done" aria-hidden="true">✓</span>
        <h2>{title}</h2>
        {detail && <p className="take-sub">{detail}</p>}
      </div>
      {children && <div className="take-actions">{children}</div>}
    </>
  );
}

/** 정보 확인(출국일·나라): 칩으로 고르고, 없으면 직접 입력한다. */
function AskPage({ block, today, busy, onAnswer }) {
  const [value, setValue] = useState('');
  const [custom, setCustom] = useState(false);
  const date = block.param !== 'COUNTRY';
  const chips = date ? (today ? dateChips(today) : []) : COUNTRIES.map((c) => ({ label: c, value: c }));
  return (
    <>
      <div className="take-body">
        <Icon name="ask-info" />
        <h2>{stepTitle(block)}</h2>
        <p className="take-sub">{date ? '날짜에 따라 준비할 일이 달라져요.' : '나라에 따라 필요한 돈이 달라요.'}</p>
        <div className="chip-row" role="group" aria-label={stepLabel(block)}>
          {chips.map((chip) => (
            <button key={chip.label} type="button" className={`chip${!custom && value === chip.value ? ' selected' : ''}`}
              aria-pressed={!custom && value === chip.value} onClick={() => { setCustom(false); setValue(chip.value); }}>
              {chip.label}
            </button>
          ))}
          <button type="button" className={`chip${custom ? ' selected' : ''}`} aria-pressed={custom}
            onClick={() => { setCustom(true); setValue(''); }}>
            {date ? '다른 날짜' : '직접 입력'}
          </button>
        </div>
        {custom && (date
          ? <input className="take-input" type="date" aria-label="출국일" value={value} min={today} max={today ? maxDate(today) : undefined}
              onChange={(event) => setValue(event.target.value)} />
          : <input className="take-input" type="text" aria-label="여행 나라" value={value} maxLength={20} placeholder="예: 베트남"
              onChange={(event) => setValue(event.target.value)} />)}
      </div>
      <div className="take-actions">
        <button className="primary take-main" type="button" data-track-id={`take-ask-${block.param}`} disabled={busy || !value.trim()}
          onClick={() => onAnswer(block, value.trim())}>
          알려 주기
        </button>
      </div>
    </>
  );
}

function BlockPage({ plan, block, cards, busy, onRun, onNext, onGoConfirm }) {
  const card = cards?.find((c) => c.ref === block.targetRef);
  if (block.status !== 'PENDING') {
    return (
      <Done title={`${stepLabel(block)} 끝났어요`} detail={block.detail}>
        <button className="primary take-main" type="button" onClick={onNext}>다음으로</button>
        {block.type !== 'HANDOFF' && <button className="ghost" type="button" onClick={() => onRun(block)}>화면 다시 보기</button>}
      </Done>
    );
  }
  const locked = plan.needsCardConfirm && isLoss(block);
  return (
    <>
      <div className="take-body">
        <Icon name={block.blockId} />
        <h2>{stepTitle(block)}</h2>
        {block.reason && <p className="take-sub clamp">{block.reason}</p>}
        {card && (
          <div className="take-target">
            <CardFace card={card} small />
            <span>{card.name}<small>끝자리 {card.last4} · {block.detail || (card.usable ? '사용 중' : '사용 정지됨')}</small></span>
          </div>
        )}
        {!card && block.detail && <p className="take-chip-line">{block.detail}</p>}
      </div>
      <div className="take-actions">
        {locked ? (
          <>
            <p className="take-hint">먼저 잃어버린 카드를 확인해 주세요.</p>
            <button className="primary take-main" type="button" onClick={onGoConfirm}>카드 확인하러 가기</button>
          </>
        ) : (
          <button className="primary take-main" type="button" data-track-id={`take-run-${block.blockId}`} disabled={busy} onClick={() => onRun(block)}>
            {actionLabel(block)}
          </button>
        )}
      </div>
    </>
  );
}

function FinishPage({ plan, busy, onRun, onReview, onHandoff, onGoCurrent, onEnd }) {
  const state = finishState(plan);
  if (state === 'IN_PROGRESS') {
    const left = required(plan).filter((b) => b.status === 'PENDING').length + (plan.needsCardConfirm ? 1 : 0);
    return (
      <>
        <div className="take-body">
          <span className="take-icon" aria-hidden="true">📝</span>
          <h2>아직 {left}단계 남았어요</h2>
          <p className="take-sub">남은 단계를 마치면 여기서 정리해 드려요.</p>
        </div>
        <div className="take-actions"><button className="primary take-main" type="button" onClick={onGoCurrent}>남은 단계로 가기</button></div>
      </>
    );
  }
  const extras = optionalBlocks(plan).filter((b) => b.status === 'PENDING');
  if (state === 'REMAINING') {
    const problems = [...goalWarnings(plan), ...plan.unhandled.map((u) => `상담 필요: ${u}`)];
    return (
      <>
        <div className="take-body">
          <span className="take-icon warn" aria-hidden="true">!</span>
          <h2>할 일은 끝났지만<br />남은 문제가 있어요</h2>
          <ul className="take-problems">{(problems.length ? problems : plan.remaining).slice(0, 3).map((p) => <li key={p}>{p}</li>)}</ul>
        </div>
        <div className="take-actions">
          <button className="primary take-main" type="button" data-track-id="take-handoff" onClick={onHandoff}>상담사에게 이어서 묻기</button>
          <button className="ghost" type="button" disabled={busy} onClick={onReview}>다른 방법 더 찾아보기</button>
        </div>
      </>
    );
  }
  return (
    <>
      <div className="take-body">
        <span className="take-icon done big" aria-hidden="true">✓</span>
        <h2><span className="blue">WON TAKE</span>로 대응을 마쳤어요!</h2>
        <p className="take-sub">혹시 더 필요한 게 있으신가요?</p>
        {extras.length > 0 && (
          <div className="take-extras">
            <p>더 해 두면 좋은 일</p>
            {extras.map((b) => (
              <button key={b.index} type="button" className="extra-row" onClick={() => onRun(b)}>
                <span aria-hidden="true">{ICON[b.blockId] ?? '•'}</span>{stepLabel(b)}<span className="chev" aria-hidden="true">›</span>
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="take-actions two">
        <button className="secondary" type="button" data-track-id="take-end" onClick={onEnd}>아니요, 끝낼게요</button>
        <button className="primary" type="button" data-track-id="take-review" disabled={busy} onClick={onReview}>네, 더 찾아볼게요</button>
      </div>
    </>
  );
}

export default function PlanScreen() {
  const { params, navigate, openHandoff } = useNavigation();
  const [plan, setPlan] = useState(null);
  const [demo, setDemo] = useState(null);
  const [index, setIndex] = useState(0);
  const [busy, setBusy] = useState('');
  const [thinking, setThinking] = useState('review');
  const [toast, setToast] = useState('');
  const [error, setError] = useState('');
  const [newKeys, setNewKeys] = useState(() => new Set());
  const pager = useRef(null);

  const load = useCallback(async () => {
    setError('');
    try {
      const [next, state] = await Promise.all([getPlan(params.planId), getDemoState()]);
      setPlan(next);
      setDemo(state);
      setIndex(currentStepIndex(next));
    } catch (e) {
      setError(`플랜을 불러오지 못했어요. (${e.message})`);
    }
  }, [params.planId]);
  useEffect(() => { void load(); }, [load]);

  // 탭을 누르거나 단계를 끝내면 그 카드로 옆으로 넘긴다. 코드로 넘기는 동안에는 밀기 감지를 멈춘다.
  const auto = useRef(false);
  useEffect(() => {
    const el = pager.current;
    if (!el) return undefined;
    auto.current = true;
    const frame = requestAnimationFrame(() => el.scrollTo({ left: index * el.clientWidth, behavior: 'smooth' }));
    const done = setTimeout(() => {
      el.scrollLeft = index * el.clientWidth; // 내용이 바뀌어 부드러운 이동이 끊겨도 제자리에 맞춘다
      auto.current = false;
    }, 500);
    return () => { cancelAnimationFrame(frame); clearTimeout(done); };
  }, [index, plan]);
  useEffect(() => {
    if (!toast) return undefined;
    const timer = setTimeout(() => setToast(''), 6000);
    return () => clearTimeout(timer);
  }, [toast]);

  // 손가락으로 밀어 넘기면 탭도 따라간다.
  const onScroll = () => {
    const el = pager.current;
    if (!el || auto.current) return;
    clearTimeout(el.settle);
    el.settle = setTimeout(() => {
      const at = Math.round(el.scrollLeft / el.clientWidth);
      setIndex((current) => (current === at ? current : at));
    }, 120);
  };

  /** 카드 확인·정보 입력·추가 찾기: AI가 다시 판단하는 동안 그 카드에 알리고, 바뀐 할 일을 한 줄로 알려 준다. */
  async function replan(request, emptyMessage = '', kind = 'review') {
    setThinking(kind);
    setBusy('review');
    setError('');
    try {
      const next = await request();
      const change = planChanges(plan, next);
      setPlan(next);
      setNewKeys(change.newKeys);
      setToast(next.notice || change.message || emptyMessage);
      setIndex(currentStepIndex(next));
      getDemoState().then(setDemo).catch(() => {});
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy('');
    }
  }

  async function run(block) {
    setBusy('action');
    setError('');
    try {
      if (block.status === 'PENDING' && block.type !== 'ACTION') setPlan(await executePlan(plan.planId, block.index));
      if (block.type === 'HANDOFF') { openHandoff(); return; }
      const target = findMenu(block.menuId);
      if (target) navigate(target.screenId, { menuId: target.id, planId: plan.planId, index: block.index, targetRef: block.targetRef, param: block.param });
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy('');
    }
  }

  if (!plan) {
    return (
      <section className="take">
        <TakeHeader onBack={() => navigate('chat')} />
        {error ? <p className="take-loading">{error}</p> : <div className="take-body"><AiThinking kind="load" /></div>}
        {error && <button className="secondary" type="button" onClick={load}>다시 불러오기</button>}
      </section>
    );
  }

  const steps = planSteps(plan);
  const doneCount = steps.filter((s) => s.kind !== 'FINISH' && s.done).length;
  const total = steps.length - 1;
  const warnings = goalWarnings(plan);
  const current = currentStepIndex(plan);
  // 끝낸 단계에서 [다음으로]: 지금 할 단계가 따로 있으면 그리로, 아니면 바로 다음 카드로.
  const next = () => setIndex(current !== index ? current : Math.min(index + 1, steps.length - 1));

  return (
    <section className="take" aria-label="원테이크">
      <TakeHeader onBack={() => navigate('chat')} title={plan.events.join(' · ') || '원테이크'} done={doneCount} total={total} />
      <div className="take-progress" aria-hidden="true"><span style={{ width: `${total ? (doneCount / total) * 100 : 0}%` }} /></div>
      <nav className="take-tabs" aria-label="단계">
        {steps.map((step, i) => (
          <button key={step.key} type="button" className={`take-tab${i === index ? ' active' : ''}${step.done ? ' done' : ''}`}
            aria-current={i === index ? 'step' : undefined} onClick={() => setIndex(i)}>
            <span className="take-tab-no" aria-hidden="true">{step.done ? '✓' : step.kind === 'FINISH' ? '★' : i + 1}</span>
            {step.label}
            {newKeys.has(step.key) && <small className="take-new">새로</small>}
          </button>
        ))}
      </nav>
      {(toast || error) && <p className={`take-toast${error ? ' error' : ''}`} role={error ? 'alert' : 'status'}>{error || toast}</p>}
      {!toast && !error && warnings.length > 0 && steps[index]?.kind !== 'FINISH' && <p className="take-warn" role="status">⚠ {warnings[0]}</p>}

      <div className="take-pager" ref={pager} onScroll={onScroll}>
        {steps.map((step, i) => (
          <article key={step.key} className="take-page" aria-hidden={i !== index} aria-label={`${i + 1}단계 ${step.label}`}>
            {busy === 'review' && i === index ? (
              <div className="take-body">
                <AiThinking kind={thinking} />
              </div>
            ) : step.kind === 'CONFIRM' ? (
              <ConfirmPage plan={plan} cards={demo?.cards} busy={Boolean(busy)} onConfirm={(refs) => replan(() => confirmCards(plan.planId, refs), '', 'confirm')} />
            ) : step.kind === 'FINISH' ? (
              <FinishPage plan={plan} busy={Boolean(busy)} onRun={run} onHandoff={openHandoff} onGoCurrent={() => setIndex(current)}
                onEnd={() => navigate('home')} onReview={() => replan(() => reviewPlan(plan.planId), '더 하실 일은 없어요')} />
            ) : step.block.blockId === 'ask-info' && step.block.status === 'PENDING' ? (
              <AskPage block={step.block} today={demo?.today} busy={Boolean(busy)}
                onAnswer={(block, value) => replan(() => answerPlan(plan.planId, block.index, value), '', 'answer')} />
            ) : (
              <BlockPage plan={plan} block={step.block} cards={demo?.cards} busy={Boolean(busy)} onRun={run} onNext={next} onGoConfirm={() => setIndex(0)} />
            )}
          </article>
        ))}
      </div>
    </section>
  );
}

// 상단: 뒤로·제목·진행 개수만 둔다. 순서 이유 시트는 고객 흐름을 끊어 뺐다(2026-10-27 사용자 승인).
function TakeHeader({ onBack, title = '원테이크', done, total }) {
  return (
    <header className="take-header">
      <button className="take-back" type="button" data-track-id="take-back" onClick={onBack} aria-label="대화로 돌아가기">‹</button>
      <strong>{title}</strong>
      {total > 0 && (
        <span className={`take-count${done === total ? ' all' : ''}`} aria-label={`${total}단계 중 ${done}단계 끝냄`}>
          <b>{done}</b>/{total} 완료
        </span>
      )}
    </header>
  );
}

