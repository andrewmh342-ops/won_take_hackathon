package com.wontake.chat;

import com.wontake.ai.AiClient;
import com.wontake.ai.FakeAiClient;
import com.wontake.api.ApiException;
import com.wontake.catalog.MenuCatalog;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MenuConfirmationTest {
    private final DemoStore store = new DemoStore();
    private final MenuCatalog catalog = new MenuCatalog();
    private AiClient.Answer answer(List<String> ids, boolean uncertain) {
        return new AiClient.Answer("메뉴를 안내해 드릴게요.", "", ids, List.of("이 단계는 확인 전에는 보여 주지 않는다"),
            false, AiClient.Answer.IN_SCOPE, uncertain);
    }
    @Test void 후보가_하나여도_확신이_없으면_정식_메뉴명으로_확인한다() {
        var service = new ChatService(store, catalog, c -> answer(List.of("transfer-history"), true));
        var reply = service.chat("a", "s", "보낸 거 확인할래", "TEXT");
        assertEquals("CONFIRM_MENU", reply.type());
        assertEquals("혹시 보고 싶은 게 이체내역조회인가요?", reply.reply());
        assertEquals(List.of("transfer-history"), reply.menuIds());
        assertTrue(reply.steps().isEmpty());
        assertFalse(reply.handoffSuggested());
        assertEquals(0, reply.notFoundStreak());
    }
    @Test void 두_후보는_정식_이름을_보여_주고_선택을_받는다() {
        var service = new ChatService(store, catalog, c -> answer(List.of("transfer-history", "transaction-history"), true));
        var reply = service.chat("a", "s", "최근 내역 좀", "TEXT");
        assertEquals("CONFIRM_MENU", reply.type());
        assertTrue(reply.reply().contains("이체내역조회"));
        assertTrue(reply.reply().contains("거래내역조회"));
        assertEquals(2, reply.menuIds().size());
    }
    @Test void AI가_확실히_고른_메뉴는_바로_이동할_수_있는_응답이다() {
        var service = new ChatService(store, catalog, c -> answer(List.of("transaction-history"), false));
        assertEquals("ANSWER", service.chat("a", "s", "최근 거래내역 보고싶어", "TEXT").type());
    }
    @Test void 확인_대화도_이전_대화로_전달되어_긍정_답변을_해석한다() {
        var service = new ChatService(store, catalog, c -> {
            if (c.history().isEmpty()) return answer(List.of("transfer-history"), true);
            assertEquals("네", c.question());
            assertEquals("혹시 보고 싶은 게 이체내역조회인가요?", c.history().getLast().message());
            return answer(List.of("transfer-history"), false);
        });
        service.chat("a", "s", "보낸 기록", "TEXT");
        var reply = service.chat("a", "s", "네", "TEXT");
        assertEquals("ANSWER", reply.type());
        assertEquals("transfer-history", reply.menuId());
    }
    @Test void 확인_후보가_없거나_목록_밖이면_응답을_거부한다() {
        for (var ids : List.of(List.<String>of(), List.of("unknown"))) {
            var service = new ChatService(store, catalog, c -> answer(ids, true));
            assertEquals("AI_INVALID_RESPONSE", assertThrows(ApiException.class,
                () -> service.chat("a", "s", "내역", "TEXT")).code);
        }
    }
    /** FAKE의 연결 검증이다. 실제 Claude의 의미 판단 성능을 검증하는 테스트는 아니다. */
    @Test void 모의_AI도_최근_이체와_거래_표현을_구분하고_애매하면_확인한다() {
        var service = new ChatService(store, catalog, new FakeAiClient());
        for (String question : List.of("최근이체내역 보고싶어", "최근 이채내역 보고싶어")) {
            var reply = service.chat("a", "s", question, "TEXT");
            assertEquals("ANSWER", reply.type());
            assertEquals(List.of("transfer-history"), reply.menuIds());
        }
        assertEquals(List.of("transaction-history"), service.chat("a", "s", "최근 거래내역 보고싶어", "TEXT").menuIds());
        assertEquals("CONFIRM_MENU", service.chat("b", "other", "최근 내역", "TEXT").type());
    }
}
