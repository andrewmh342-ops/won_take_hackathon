package com.wontake.chat;

import com.wontake.ai.AiClient;
import com.wontake.api.ApiException;
import com.wontake.catalog.MenuCatalog;
import com.wontake.session.DemoStore;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChatService {
    public record Reply(String type, String normalized, String reply, String menuId, List<String> menuIds,
                        List<String> steps, int notFoundStreak, String mode) {}
    private final DemoStore store;
    private final MenuCatalog catalog;
    private final AiClient ai;
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai) { this.store=store; this.catalog=catalog; this.ai=ai; }
    public Reply chat(String userId, String sessionId, String question, String inputType) {
        if (question == null || question.isBlank() || question.length() > 2000) throw ApiException.invalid("질문을 1~2000자로 입력해 주세요.");
        if (!Set.of("TEXT", "VOICE", "SUGGESTION").contains(Objects.toString(inputType, ""))) throw ApiException.invalid("입력 방식을 확인해 주세요.");
        var s = store.session(userId, sessionId);
        synchronized(s) {
            var turns = s.messages().stream().map(m -> new AiClient.Turn(m.role(), m.message())).toList();
            var allEvents = Boolean.TRUE.equals(store.consent(userId)) ? s.events() : List.<DemoStore.Event>of();
            var events = allEvents.subList(Math.max(0, allEvents.size()-20), allEvents.size());
            var result = ai.answer(new AiClient.Context(question.trim(), inputType, turns, events, catalog.all()));
            validate(result);
            var ids = List.copyOf(result.menuIds());
            var reply = new Reply(ids.isEmpty() ? "NOT_FOUND" : "ANSWER", "VOICE".equals(inputType) ? result.normalized() : "",
                result.reply(), ids.isEmpty() ? "" : ids.getFirst(), ids, List.copyOf(result.steps()), 0, ai.mode());
            s.conversation.add(new DemoStore.Message("USER", question.trim(), inputType, null));
            s.conversation.add(new DemoStore.Message("ASSISTANT", reply.reply(), "", reply));
            while(s.conversation.size()>20) s.conversation.removeFirst();
            return reply;
        }
    }
    private void validate(AiClient.Answer a) {
        if (a == null || a.reply() == null || a.reply().isBlank() || a.reply().length()>500
            || a.normalized()==null || a.normalized().length()>2000 || a.menuIds()==null || a.menuIds().size()>2
            || a.menuIds().stream().anyMatch(id -> id==null || !catalog.contains(id))
            || a.menuIds().stream().distinct().count()!=a.menuIds().size()
            || a.steps()==null || a.steps().size()>3 || a.steps().stream().anyMatch(s -> s==null || s.isBlank() || s.length()>200))
            throw new ApiException(502, "AI_INVALID_RESPONSE", "AI 안내 형식을 확인하지 못했어요. 다시 시도해 주세요.");
    }
}
