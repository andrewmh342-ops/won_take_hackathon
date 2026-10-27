package com.wontake.ai;

import com.wontake.catalog.MenuCatalog.Menu;
import com.wontake.session.DemoStore.Event;
import java.util.List;

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
     * plan은 R5 업무 블록 조립 결과다. 블록이 없으면 일반 안내다.
     */
    record Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff, String scope, AiPlan plan) {
        public static final String IN_SCOPE = "IN_SCOPE", OUT_OF_SCOPE = "OUT_OF_SCOPE";
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff, String scope) { this(reply,normalized,menuIds,steps,handoff,scope,AiPlan.EMPTY); }
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff) { this(reply,normalized,menuIds,steps,handoff,IN_SCOPE); }
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps) { this(reply,normalized,menuIds,steps,false); }
        public boolean outOfScope() { return OUT_OF_SCOPE.equals(scope); }
        public boolean hasPlan() { return plan != null && plan.blocks() != null && !plan.blocks().isEmpty(); }
    }
    Answer answer(Context context);
    default ProactiveSuggestion suggest(BehaviorContext context) { return new ProactiveSuggestion(false,"",List.of()); }
    default HandoffSummary summarize(HandoffContext context) { return new HandoffSummary("","","",""); }
    default String mode() { return "FAKE"; }
}
