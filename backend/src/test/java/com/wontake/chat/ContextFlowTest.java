package com.wontake.chat;

import com.wontake.ai.AiClient;
import com.wontake.ai.FakeAiClient;
import com.wontake.catalog.MenuCatalog;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContextFlowTest {
    private final MenuCatalog catalog = new MenuCatalog();
    private final DemoStore store = new DemoStore();

    @Test void sameQuestionUsesOrderedConsentedContext() {
        var service = new ChatService(store, catalog, new FakeAiClient());
        store.consent("a", true); store.consent("b", true);
        store.event("a", "sa", "SCREEN_ENTER", "transfer", "instant-transfer", null);
        store.event("b", "sb", "SCREEN_ENTER", "exchange", "exchange-apply", null);
        assertEquals(List.of("transfer-history"), service.chat("a", "sa", "내역", "TEXT").menuIds());
        assertEquals(List.of("exchange-history"), service.chat("b", "sb", "내역", "VOICE").menuIds());
    }

    @Test void noConsentAndRevocationRemoveBehaviorFromAiInput() {
        AiClient inspect = context -> {
            assertTrue(context.recentEvents().isEmpty());
            return new AiClient.Answer("내역 메뉴를 선택해 주세요.", "", List.of("transfer-history", "exchange-history"), List.of());
        };
        store.event("a", "sa", "SCREEN_ENTER", "transfer", null, null);
        var service = new ChatService(store, catalog, inspect);
        service.chat("a", "sa", "내역", "TEXT");
        store.consent("a", true);
        store.event("a", "sa", "SCREEN_ENTER", "exchange", null, null);
        store.consent("a", false);
        service.chat("a", "sa", "내역", "TEXT");
        assertTrue(store.session("a", "sa").events().isEmpty());
    }

    @Test void historyIsIncludedOnlyForSameSessionAndQuestionAppearsOnce() {
        AiClient inspect = context -> {
            assertEquals(2, context.history().size());
            assertEquals("이체", context.history().getFirst().message());
            assertEquals("그 내역", context.question());
            return new AiClient.Answer("이체내역조회에서 확인하세요.", "", List.of("transfer-history"), List.of());
        };
        new ChatService(store, catalog, new FakeAiClient()).chat("a", "sa", "이체", "TEXT");
        new ChatService(store, catalog, inspect).chat("a", "sa", "그 내역", "TEXT");
        assertTrue(store.session("b", "sb").messages().isEmpty());
        assertThrows(RuntimeException.class, () -> store.session("b", "sa"));
    }

    @Test void fabricatedMenuAndMalformedAnswerAreRejectedWithoutInventedFallback() {
        var service = new ChatService(store, catalog, c -> new AiClient.Answer("500만원으로 올렸어요", "", List.of("made-up"), List.of()));
        assertThrows(RuntimeException.class, () -> service.chat("a", "sa", "한도", "TEXT"));
        assertTrue(store.session("a", "sa").messages().isEmpty());
        var duplicate = new ChatService(store, catalog, c -> new AiClient.Answer("안내", "", List.of("all-accounts", "all-accounts"), List.of()));
        assertThrows(RuntimeException.class, () -> duplicate.chat("a", "sa", "계좌", "TEXT"));
    }

    @Test void recentEventsAreOrderedAndBounded() {
        store.consent("a", true);
        for (int i=0; i<25; i++) store.event("a", "sa", "CLICK", "menu", "item-"+i, null);
        new ChatService(store, catalog, c -> {
            assertEquals(20, c.recentEvents().size());
            assertEquals("item-5", c.recentEvents().getFirst().elementId());
            assertEquals("item-24", c.recentEvents().getLast().elementId());
            return new AiClient.Answer("전체계좌조회로 이동하세요.", "", List.of("all-accounts"), List.of());
        }).chat("a", "sa", "계좌", "TEXT");
    }
}
