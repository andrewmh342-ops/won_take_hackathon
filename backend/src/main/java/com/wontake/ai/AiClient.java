package com.wontake.ai;

import com.wontake.catalog.MenuCatalog.Menu;
import com.wontake.session.DemoStore.Event;
import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface AiClient {
    record Turn(String role, String message) {}
    record Visit(String menuId, String category, String enteredAt, String exitedAt, long dwellMillis) {}
    record HelpContext(String id, String question, List<Visit> visits, List<Event> recentEvents) {}
    /** R5 가명화: AI가 보는 카드 모양. 이름·끝자리·금액·가맹점명은 없다. */
    record CardShape(String ref, String kind, boolean travel, boolean simplePay, boolean overseasPayment, String status, List<String> autoPayments,
                     boolean usable, String reissueStatus) {}
    /** 여행 정보(정보 확인 블록의 답). 실제 날짜 대신 출국까지 남은 날 수만 보낸다. */
    record TripShape(String country, Integer daysUntilDeparture) {}
    record CustomerShape(List<CardShape> cards, boolean hasTravelCard, List<String> currencies, boolean hasUsableTravelCard, TripShape trip) {
        public CustomerShape(List<CardShape> cards, boolean hasTravelCard, List<String> currencies) { this(cards,hasTravelCard,currencies,hasTravelCard,new TripShape(null,null)); }
    }
    /** R5 블록 사전 중 AI에 보여 주는 부분. */
    record BlockInfo(String id, String name, String type, String target, String param, List<String> requires, String description,
                     String effect, List<String> achieves, String leadTime) {}
    record Context(String question, String inputType, List<Turn> history, List<Event> recentEvents, List<Menu> menus, HelpContext helpContext,
                   CustomerShape customer, List<BlockInfo> blocks, PlanState planState) {
        public Context(String question, String inputType, List<Turn> history, List<Event> recentEvents, List<Menu> menus, HelpContext helpContext,
                       CustomerShape customer, List<BlockInfo> blocks) {
            this(question,inputType,history,recentEvents,menus,helpContext,customer,blocks,null);
        }
        public Context(String question, String inputType, List<Turn> history, List<Event> recentEvents, List<Menu> menus, HelpContext helpContext) {
            this(question,inputType,history,recentEvents,menus,helpContext,null,null);
        }
        public Context(String question, String inputType, List<Turn> history, List<Event> recentEvents, List<Menu> menus) {
            this(question,inputType,history,recentEvents,menus,null);
        }
    }
    record BehaviorContext(List<Visit> visits, List<Event> recentEvents, List<Turn> history, List<Menu> menus) {}
    record ProactiveSuggestion(boolean shouldOffer, String question, List<String> menuIds) {}
    record HandoffContext(List<Turn> conversation, List<Event> clickTimeline, boolean clickConsent, HelpContext helpContext, List<Menu> menus,
                          List<String> planProgress) {
        public HandoffContext(List<Turn> conversation, List<Event> clickTimeline, boolean clickConsent, HelpContext helpContext, List<Menu> menus) {
            this(conversation,clickTimeline,clickConsent,helpContext,menus,List.of());
        }
    }
    record HandoffSummary(String purpose, String stuckPoint, String triedActions, String unresolved) {}
    /** R5 재판단 입력: 고객이 확인한 분실 카드, 이미 끝낸 업무. 처음 판단이면 null. */
    record PlanState(List<String> confirmedLostCards, List<String> doneBlocks) {}
    /** R5: AI가 고른 블록. targetRef는 카드 ref(없으면 ""), param은 통화 코드·정보 종류(없으면 ""), priority는 REQUIRED|OPTIONAL. */
    record AiBlock(String blockId, String targetRef, String param, String reason, String priority) {
        public AiBlock(String blockId, String targetRef, String param, String reason) { this(blockId,targetRef,param,reason,"REQUIRED"); }
    }
    /** R5 목표 중심: 목표(CARD_PROTECTION·PAYMENT_ABROAD·CASH_ABROAD·OTHER), 장애물과 해결 방법, 넣지 않은 업무. */
    record AiGoal(String type, String text, boolean required) {}
    record AiObstacle(String text, String resolution, List<Integer> blockIndexes) {}
    record AiConsidered(String blockId, String reason) {}
    record AiPlan(List<String> events, List<AiBlock> blocks, List<String> unhandled, String orderReason,
                  List<AiGoal> goals, List<AiObstacle> obstacles, List<AiConsidered> considered) {
        public static final AiPlan EMPTY = new AiPlan(List.of(), List.of(), List.of(), "");
        public AiPlan(List<String> events, List<AiBlock> blocks, List<String> unhandled, String orderReason) {
            this(events,blocks,unhandled,orderReason,List.of(),List.of(),List.of());
        }
    }
    /**
     * handoff는 상담 연결 버튼을 강조할지에 대한 AI 판단이다. 버튼 자체는 항상 쓸 수 있다.
     * scope는 금융앱 이용 불편 질문인지(IN_SCOPE) 아닌지(OUT_OF_SCOPE)다. OUT_OF_SCOPE면 서버가 AI 문장을 버린다.
     * TRANSFER_PLAN은 받는 사람을 말한 이체 요청이다. 서버가 plan으로 작업 목록을 만들고, 작업이 없으면 이 답의 메뉴 안내를 대신 보여 준다.
     */
    record Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff, String scope, AiPlan plan, boolean needsConfirmation) {
        public static final String IN_SCOPE = "IN_SCOPE", OUT_OF_SCOPE = "OUT_OF_SCOPE", TRANSFER_PLAN = "TRANSFER_PLAN";
        public static final List<String> CHAT_SCOPES = List.of(IN_SCOPE, OUT_OF_SCOPE, TRANSFER_PLAN);
        /** 명시적 메뉴 선택을 만드는 기존 모의 구현·테스트의 생성자를 유지한다. 실제 Claude 응답은 확인 여부를 따로 읽는다. */
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff, String scope) { this(reply,normalized,menuIds,steps,handoff,scope,false); }
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff, String scope, boolean needsConfirmation) { this(reply,normalized,menuIds,steps,handoff,scope,AiPlan.EMPTY,needsConfirmation); }
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff, String scope, AiPlan plan) { this(reply,normalized,menuIds,steps,handoff,scope,plan,false); }
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff) { this(reply,normalized,menuIds,steps,handoff,IN_SCOPE); }
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps) { this(reply,normalized,menuIds,steps,false); }
        public boolean outOfScope() { return OUT_OF_SCOPE.equals(scope); }
        public boolean transferPlan() { return TRANSFER_PLAN.equals(scope); }
        public boolean hasPlan() { return plan != null && plan.blocks() != null && !plan.blocks().isEmpty(); }
    }
    /** 한 번에 맡기기: AI에는 받는 곳 이름과 별칭만 보낸다. 계좌번호와 이체 이력은 보내지 않는다. */
    record Payee(String name, String kind, List<String> aliases) {}
    /** amountValues는 서버 복원용이며 Claude 요청 본문에는 넣지 않는다. */
    record PlanContext(String request, List<Payee> payees, Map<String, Long> amountValues) {
        public PlanContext(String request, List<Payee> payees) { this(TransferAmounts.mask(request), payees); }
        private PlanContext(TransferAmounts.Masked masked, List<Payee> payees) { this(masked.text(), payees, masked.values()); }
    }
    /** AI는 받는 곳과 금액만 고른다. amount가 null이면 사용자가 금액을 말하지 않은 것이다. 이체 종류와 위험도는 서버가 정한다. */
    record TaskDraft(String target, Long amount, String memo) {}
    record PlanDraft(String scope, List<TaskDraft> tasks) {
        public static final List<String> PLAN_SCOPES = List.of(Answer.IN_SCOPE, Answer.OUT_OF_SCOPE);
        public boolean outOfScope() { return Answer.OUT_OF_SCOPE.equals(scope); }
    }
    /** 빈 금액 채우기: AI에는 금액이 빈 받는 곳 이름과 고객의 다음 말만 보낸다. 목록 전체와 잔액은 보내지 않는다. */
    record FillContext(List<String> targets, String message, Map<String, Long> amountValues) {
        public FillContext(List<String> targets, String message) { this(targets, TransferAmounts.mask(message)); }
        private FillContext(List<String> targets, TransferAmounts.Masked masked) { this(targets, masked.text(), masked.values()); }
    }
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
