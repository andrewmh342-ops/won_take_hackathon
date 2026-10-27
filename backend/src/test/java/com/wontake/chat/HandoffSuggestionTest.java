package com.wontake.chat;

import com.wontake.ai.AiClient;
import com.wontake.ai.FakeAiClient;
import com.wontake.catalog.MenuCatalog;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 상담 버튼 강조: 금융앱 이용 불편 질문인데 AI가 명확한 답을 찾기 어려울 때만. */
class HandoffSuggestionTest {
    private final MenuCatalog catalog = new MenuCatalog();
    private final DemoStore store = new DemoStore();
    private final ChatService service = new ChatService(store, catalog, new FakeAiClient());

    @Test void 찾지_못함이_연속_2번이면_상담을_강조한다() {
        var first = service.chat("a", "sa", "앱에서 대출 신청은 어디서 해요", "TEXT");
        assertEquals("NOT_FOUND", first.type());
        assertEquals(1, first.notFoundStreak());
        assertFalse(first.handoffSuggested());
        var second = service.chat("a", "sa", "적금 해지는 어디서 해요", "TEXT");
        assertEquals(2, second.notFoundStreak());
        assertTrue(second.handoffSuggested());
    }

    @Test void 중간에_성공하면_연속_횟수를_되돌린다() {
        service.chat("a", "sa", "앱에서 대출 신청은 어디서 해요", "TEXT");
        var answer = service.chat("a", "sa", "환전 신청", "TEXT");
        assertEquals("ANSWER", answer.type());
        assertEquals(0, answer.notFoundStreak());
        var again = service.chat("a", "sa", "적금 해지는 어디서 해요", "TEXT");
        assertEquals(1, again.notFoundStreak());
        assertFalse(again.handoffSuggested());
    }

    @Test void AI가_상담을_권하면_강조한다() {
        var ai = new ChatService(store, catalog, c -> new AiClient.Answer("상담사에게 이어서 물어보실 수 있어요.", "", List.of("auth-center"), List.of(), true));
        var reply = ai.chat("a", "sa", "인증이 계속 안 돼요", "TEXT");
        assertEquals("ANSWER", reply.type());
        assertTrue(reply.handoffSuggested());
    }

    @Test void 모의_AI는_상담_요청에_상담을_권한다() {
        assertTrue(service.chat("a", "sa", "상담원 연결해 줘", "TEXT").handoffSuggested());
    }

    @Test void 범위_밖_질문은_찾지_못함_연속에_세지_않고_강조하지_않는다() {
        service.chat("a", "sa", "앱에서 대출 신청은 어디서 해요", "TEXT");
        var off = service.chat("a", "sa", "오늘 날씨 어때", "TEXT");
        assertEquals("OUT_OF_SCOPE", off.type());
        assertEquals(1, off.notFoundStreak());
        assertFalse(off.handoffSuggested());
    }
}
