package com.wontake.api;

import com.wontake.ai.AiClient;
import com.wontake.behavior.BehaviorService;
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
    public record ChatInput(String userId, String sessionId, String message, String inputType, String intentId,String contextId) {}
    public record AnalyzeInput(String userId,String sessionId,String contextId) {}
    private final DemoStore store;
    private final MenuCatalog catalog;
    private final ChatService chat;
    private final AiClient ai;
    private final BehaviorService behavior;
    public DemoController(DemoStore store, MenuCatalog catalog, ChatService chat, AiClient ai,BehaviorService behavior) { this.store=store; this.catalog=catalog; this.chat=chat; this.ai=ai; this.behavior=behavior; }
    @GetMapping("/health") public Object health() { return Map.of("status", "UP", "aiMode", ai.mode()); }
    @GetMapping("/menus") public Object menus() { return catalog.all(); }
    @GetMapping("/consent") public Object consent(@RequestParam String userId) { return Collections.singletonMap("agreed", store.consent(userId)); }
    @PutMapping("/consent") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void consent(@RequestBody ConsentInput body) {
        if (body.agreed()==null) throw ApiException.invalid("동의 여부를 확인해 주세요.");
        store.consent(body.userId(), body.agreed());
        if(!body.agreed()) behavior.reset(body.userId());
    }
    @PostMapping("/events") public Object event(@RequestBody EventInput body) {
        String id=behavior.record(body.userId(),body.sessionId(),body.type(),body.screenId(),body.elementId(),body.query());
        var result=new LinkedHashMap<String,Object>(); result.put("bubble",null); result.put("analysisId",id); return result;
    }
    @PostMapping("/assist/analyze") public Object analyze(@RequestBody AnalyzeInput body) {
        return Collections.singletonMap("bubble",behavior.analyze(body.userId(),body.sessionId(),body.contextId()));
    }
    @PostMapping("/bubble/close") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void close(@RequestBody CloseInput body) { store.close(body.userId(), body.sessionId(), body.screenId(), body.action()); behavior.reset(body.userId()); }
    @PostMapping("/chat") public Object chat(@RequestBody ChatInput body) { return chat.chat(body.userId(), body.sessionId(), body.message(), body.inputType(),body.contextId()); }
    @GetMapping("/chat/history") public Object history(@RequestParam String userId, @RequestParam String sessionId) { return store.session(userId, sessionId).messages(); }
}
