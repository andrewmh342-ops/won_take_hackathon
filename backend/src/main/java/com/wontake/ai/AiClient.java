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
    record Answer(String reply, String normalized, List<String> menuIds, List<String> steps) {}
    Answer answer(Context context);
    default ProactiveSuggestion suggest(BehaviorContext context) { return new ProactiveSuggestion(false,"",List.of()); }
    default String mode() { return "FAKE"; }
}
