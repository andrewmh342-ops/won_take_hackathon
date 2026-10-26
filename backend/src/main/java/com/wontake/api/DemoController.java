package com.wontake.api;

import com.wontake.ai.AiClient;
import com.wontake.catalog.MenuCatalog;
import com.wontake.chat.ChatService;
import com.wontake.session.DemoStore;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class DemoController {
    public record ConsentInput(String userId, Boolean agreed) {}
    public record EventInput(String userId, String sessionId, String type, String screenId, String elementId, String query) {}
    public record CloseInput(String userId, String sessionId, String screenId, String action) {}
    public record ChatInput(String userId, String sessionId, String message, String inputType, String intentId) {}
    private final DemoStore store;
    private final MenuCatalog catalog;
    private final ChatService chat;
    private final AiClient ai;
    public DemoController(DemoStore store, MenuCatalog catalog, ChatService chat, AiClient ai) { this.store=store; this.catalog=catalog; this.chat=chat; this.ai=ai; }
    @GetMapping("/health") public Object health() { return Map.of("status", "UP", "aiMode", ai.mode()); }
    @GetMapping("/menus") public Object menus() { return catalog.all(); }
    @GetMapping("/consent") public Object consent(@RequestParam String userId) { return Collections.singletonMap("agreed", store.consent(userId)); }
    @PutMapping("/consent") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void consent(@RequestBody ConsentInput body) {
        if (body.agreed()==null) throw ApiException.invalid("동의 여부를 확인해 주세요.");
        store.consent(body.userId(), body.agreed());
    }
    @PostMapping("/events") public Object event(@RequestBody EventInput body) {
        boolean suggest = store.event(body.userId(), body.sessionId(), body.type(), body.screenId(), body.elementId(), body.query());
        // 기존 재진입 말풍선은 탐색 진입점이다. 실제 맥락 추론은 질문을 보낸 뒤 ChatService에서 수행한다.
        if (!suggest) return Collections.singletonMap("bubble", null);
        var suggestions = catalog.all().stream().filter(m -> m.screenId().equals(body.screenId())).limit(3)
            .map(m -> Map.of("intentId", m.id(), "question", m.name()+" 메뉴를 찾고 싶어요")).toList();
        return Map.of("bubble", Map.of("screenId", body.screenId(), "suggestions", suggestions));
    }
    @PostMapping("/bubble/close") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void close(@RequestBody CloseInput body) { store.close(body.userId(), body.sessionId(), body.screenId(), body.action()); }
    @PostMapping("/chat") public Object chat(@RequestBody ChatInput body) { return chat.chat(body.userId(), body.sessionId(), body.message(), body.inputType()); }
    @GetMapping("/chat/history") public Object history(@RequestParam String userId, @RequestParam String sessionId) { return store.session(userId, sessionId).messages(); }
}
