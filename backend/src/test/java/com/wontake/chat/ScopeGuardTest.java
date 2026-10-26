package com.wontake.chat;

import com.wontake.ai.AiClient;
import com.wontake.api.ApiException;
import com.wontake.catalog.MenuCatalog;
import com.wontake.session.DemoStore;
import com.wontake.session.UsageGuard;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 금융앱 이용 불편 외의 질문은 거절하고, AI가 쓴 문장 대신 서버의 고정 안내만 보여 준다. */
class ScopeGuardTest {
    private final MenuCatalog catalog = new MenuCatalog();
    private final DemoStore store = new DemoStore();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<AiClient.Context> seen = new ArrayList<>();

    private AiClient ai(String scope, String reply) {
        return c -> { calls.incrementAndGet(); seen.add(c);
            return new AiClient.Answer(reply, "", List.of(), List.of(), false, scope); };
    }

    @Test void 범위_밖이면_AI_문장을_버리고_고정_안내만_보낸다() {
        var service = new ChatService(store, catalog, ai("OUT_OF_SCOPE", "파이썬 코드는 이렇게 짜면 돼요: print(1)"));
        var reply = service.chat("a", "sa", "파이썬으로 크롤러 만들어줘", "TEXT");
        assertEquals("OUT_OF_SCOPE", reply.type());
        assertEquals(ChatService.OUT_OF_SCOPE_REPLY, reply.reply());
        assertTrue(reply.menuIds().isEmpty());
        assertTrue(reply.steps().isEmpty());
        assertFalse(reply.reply().contains("print"));
    }

    @Test void 범위_밖_대화는_다음_AI_입력의_이전_대화에서_뺀다() {
        var guard = new UsageGuard();
        new ChatService(store, catalog, ai("OUT_OF_SCOPE", "x"), null, guard).chat("a", "sa", "시 한 편 써 줘", "TEXT");
        new ChatService(store, catalog, c -> { seen.add(c); return new AiClient.Answer("이체한도 메뉴에서 확인하세요.", "", List.of("transfer-limit-manage"), List.of()); }, null, guard)
            .chat("a", "sa", "이체 한도 어디서 봐요", "TEXT");
        assertTrue(seen.getLast().history().isEmpty());
        assertEquals(4, store.session("a", "sa").messages().size()); // 상담사 원문에는 남는다
    }

    @Test void 범위_밖이_연속_3번이면_AI를_부르지_않고_거절한다() {
        var service = new ChatService(store, catalog, ai("OUT_OF_SCOPE", "x"), null, new UsageGuard());
        for (int i = 0; i < 3; i++) service.chat("a", "sa", "농담 해 줘 " + i, "TEXT");
        assertEquals(3, calls.get());
        var reply = service.chat("a", "sa", "이체 한도 알려줘", "TEXT");
        assertEquals("OUT_OF_SCOPE", reply.type());
        assertEquals(ChatService.BLOCKED_REPLY, reply.reply());
        assertEquals(3, calls.get());
    }

    @Test void AI가_거절하면_범위_밖으로_처리한다() {
        var service = new ChatService(store, catalog, c -> { throw new ApiException(502, "AI_REFUSED", "거절"); });
        assertEquals("OUT_OF_SCOPE", service.chat("a", "sa", "위험한 요청", "TEXT").type());
    }

    @Test void 질문은_500자까지_받고_501자는_거절한다() {
        var service = new ChatService(store, catalog, ai("IN_SCOPE", "안내"));
        assertEquals("NOT_FOUND", service.chat("a", "sa", "이".repeat(500), "TEXT").type());
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class, () -> service.chat("a", "sb", "이".repeat(501), "TEXT")).code);
    }

    @Test void 분당_한도를_넘으면_AI를_부르지_않는다() {
        var service = new ChatService(store, catalog, ai("IN_SCOPE", "안내"), null, new UsageGuard());
        for (int i = 0; i < 6; i++) service.chat("a", "sa", "대출 " + i, "TEXT");
        assertEquals("CHAT_RATE_LIMIT", assertThrows(ApiException.class, () -> service.chat("a", "sa", "대출", "TEXT")).code);
        assertEquals(6, calls.get());
    }

    @Test void 모의_AI도_금융앱과_무관한_질문을_범위_밖으로_본다() {
        var service = new ChatService(store, catalog, new com.wontake.ai.FakeAiClient());
        assertEquals("OUT_OF_SCOPE", service.chat("a", "sa", "오늘 날씨 어때", "TEXT").type());
        assertEquals("OUT_OF_SCOPE", service.chat("a", "sa", "점심 메뉴 추천해 줘", "TEXT").type());
        assertEquals("NOT_FOUND", service.chat("a", "sb", "앱에서 대출 신청은 어디서 해요", "TEXT").type());
        assertEquals("ANSWER", service.chat("a", "sc", "이체 한도 알려줘", "TEXT").type());
    }
}
