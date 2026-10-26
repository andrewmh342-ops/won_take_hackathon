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
     */
    record Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff, String scope) {
        public static final String IN_SCOPE = "IN_SCOPE", OUT_OF_SCOPE = "OUT_OF_SCOPE";
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps, boolean handoff) { this(reply,normalized,menuIds,steps,handoff,IN_SCOPE); }
        public Answer(String reply, String normalized, List<String> menuIds, List<String> steps) { this(reply,normalized,menuIds,steps,false); }
        public boolean outOfScope() { return OUT_OF_SCOPE.equals(scope); }
    }
    Answer answer(Context context);
    default ProactiveSuggestion suggest(BehaviorContext context) { return new ProactiveSuggestion(false,"",List.of()); }
    default HandoffSummary summarize(HandoffContext context) { return new HandoffSummary("","","",""); }
    default String mode() { return "FAKE"; }
}
