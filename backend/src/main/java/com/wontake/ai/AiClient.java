package com.wontake.ai;

import com.wontake.catalog.MenuCatalog.Menu;
import com.wontake.session.DemoStore.Event;
import java.util.List;

@FunctionalInterface
public interface AiClient {
    record Turn(String role, String message) {}
    record Visit(String menuId, String category, String enteredAt, String exitedAt, long dwellMillis) {}
    record HelpContext(String id, String question, List<Visit> visits, List<Event> recentEvents) {}
    record Context(String question, String inputType, List<Turn> history, List<Event> recentEvents, List<Menu> menus, HelpContext helpContext) {
        public Context(String question, String inputType, List<Turn> history, List<Event> recentEvents, List<Menu> menus) {
            this(question,inputType,history,recentEvents,menus,null);
        }
    }
    record BehaviorContext(List<Visit> visits, List<Event> recentEvents, List<Turn> history, List<Menu> menus) {}
    record ProactiveSuggestion(boolean shouldOffer, String question, List<String> menuIds) {}
    record HandoffContext(List<Turn> conversation, List<Event> clickTimeline, boolean clickConsent, HelpContext helpContext, List<Menu> menus) {}
    record HandoffSummary(String purpose, String stuckPoint, String triedActions, String unresolved) {}
    /**
     * handoff는 상담 연결 버튼을 강조할지에 대한 AI 판단이다. 버튼 자체는 항상 쓸 수 있다.
     * scope는 금융앱 이용 불편 질문인지(IN_SCOPE) 아닌지(OUT_OF_SCOPE)다. OUT_OF_SCOPE면 서버가 AI 문장을 버린다.
     * TRANSFER_PLAN은 받는 사람을 말한 이체 요청이다. 서버가 plan으로 작업 목록을 만들고, 작업이 없으면 이 답의 메뉴 안내를 대신 보여 준다.
     */
    record Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff, String scope) {
        public static final String IN_SCOPE = "IN_SCOPE", OUT_OF_SCOPE = "OUT_OF_SCOPE", TRANSFER_PLAN = "TRANSFER_PLAN";
        public static final List<String> CHAT_SCOPES = List.of(IN_SCOPE, OUT_OF_SCOPE, TRANSFER_PLAN);
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff) { this(reply,normalized,menuIds,steps,handoff,IN_SCOPE); }
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps) { this(reply,normalized,menuIds,steps,false); }
        public boolean outOfScope() { return OUT_OF_SCOPE.equals(scope); }
        public boolean transferPlan() { return TRANSFER_PLAN.equals(scope); }
    }
    /** 한 번에 맡기기: AI에는 받는 곳 이름과 별칭만 보낸다. 계좌번호와 이체 이력은 보내지 않는다. */
    record Payee(String name, String kind, List<String> aliases) {}
    record PlanContext(String request, List<Payee> payees) {}
    /** AI는 받는 곳과 금액만 고른다. amount가 null이면 사용자가 금액을 말하지 않은 것이다. 이체 종류와 위험도는 서버가 정한다. */
    record TaskDraft(String target, Long amount, String memo) {}
    record PlanDraft(String scope, List<TaskDraft> tasks) {
        public static final List<String> PLAN_SCOPES = List.of(Answer.IN_SCOPE, Answer.OUT_OF_SCOPE);
        public boolean outOfScope() { return Answer.OUT_OF_SCOPE.equals(scope); }
    }
    /** 빈 금액 채우기: AI에는 금액이 빈 받는 곳 이름과 고객의 다음 말만 보낸다. 목록 전체와 잔액은 보내지 않는다. */
    record FillContext(List<String> targets, String message) {}
    /** amount가 null이면 고객이 그 받는 곳의 금액을 이번에도 말하지 않은 것이다. */
    record AmountDraft(String target, Long amount) {}
    Answer answer(Context context);
    default ProactiveSuggestion suggest(BehaviorContext context) { return new ProactiveSuggestion(false,"",List.of()); }
    default HandoffSummary summarize(HandoffContext context) { return new HandoffSummary("","","",""); }
    /** 빈 목록을 돌려주면 구현이 빠진 것이 "작업 없음" 안내로 숨는다. 구현하지 않았으면 바로 드러나게 한다. */
    default PlanDraft plan(PlanContext context) { throw new UnsupportedOperationException("plan 미구현: " + getClass().getSimpleName()); }
    default List<AmountDraft> fill(FillContext context) { throw new UnsupportedOperationException("fill 미구현: " + getClass().getSimpleName()); }
    default String mode() { return "FAKE"; }
}
