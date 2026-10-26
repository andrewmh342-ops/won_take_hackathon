package com.wontake.ai;

import com.wontake.catalog.MenuCatalog.Menu;
import com.wontake.session.DemoStore.Event;
import java.util.List;

@FunctionalInterface
public interface AiClient {
    record Turn(String role, String message) {}
    record Context(String question, String inputType, List<Turn> history, List<Event> recentEvents, List<Menu> menus) {}
    record Answer(String reply, String normalized, List<String> menuIds, List<String> steps) {}
    Answer answer(Context context);
    default String mode() { return "FAKE"; }
}
