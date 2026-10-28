// 담당: 공용 — R1 도움 제안 말풍선을 띄워도 되는 화면인지 정한다.
// 원테이크 플랜을 진행하는 동안에는 고객이 화면을 오가는 것을 "헤맴"으로 보지 않는다.
// 플랜을 마치면(또는 [아니요, 끝낼게요]로 끝내면) 원테이크 화면 밖에서는 다시 원래 규칙대로 띄운다.

// 말풍선을 띄우지 않는 화면. 원테이크 화면(plan)은 "WON TAKE로 대응을 마쳤어요!"에서도 띄우지 않는다.
const NO_BUBBLE_SCREENS = ['chat', 'consent', 'agent', 'login', 'splash', 'plan'];

/** plans: 이 세션에서 연 플랜 id → 마쳤는지. 하나라도 안 끝났으면 진행 중이다. */
export const hasUnfinishedPlan = (plans) => [...plans.values()].some((done) => !done);

/** [아니요, 끝낼게요]: 고객이 플랜을 끝냈으므로 끝난 플랜으로 친다. */
export function endPlan(plans, planId) {
  if (planId) plans.set(planId, true);
}

/** unfinishedPlan: 이 세션에서 열고 아직 마치지 않은 플랜이 있는지 */
export function canShowHelpBubble(screen, params = {}, unfinishedPlan = false) {
  if (NO_BUBBLE_SCREENS.includes(screen)) return false;
  return !unfinishedPlan;
}
