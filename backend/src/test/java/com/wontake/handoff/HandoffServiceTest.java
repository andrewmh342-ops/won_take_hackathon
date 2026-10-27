package com.wontake.handoff;

import com.wontake.ai.AiClient;
import com.wontake.ai.FakeAiClient;
import com.wontake.api.ApiException;
import com.wontake.catalog.MenuCatalog;
import com.wontake.chat.ChatService;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HandoffServiceTest {
    private final MenuCatalog catalog = new MenuCatalog();
    private final DemoStore store = new DemoStore();

    private void talk() {
        new ChatService(store, catalog, new FakeAiClient()).chat("a", "sa", "이체 한도가 우째 되능교", "VOICE");
    }

    @Test void 빈_항목은_확인_안_됨으로_채운다() {
        talk();
        AiClient ai = new AiClient() {
            @Override public Answer answer(Context c) { throw new UnsupportedOperationException(); }
            @Override public HandoffSummary summarize(HandoffContext c) { return new HandoffSummary("이체한도 확인", "  ", "", null); }
            @Override public String mode() { return "CLAUDE"; }
        };
        var draft = new HandoffService(store, catalog, ai).draft("a", "sa");
        assertEquals("이체한도 확인", draft.get("purpose"));
        assertEquals("확인 안 됨", draft.get("stuckPoint"));
        assertEquals("확인 안 됨", draft.get("triedActions"));
        assertEquals("확인 안 됨", draft.get("unresolved"));
        assertEquals("AI", draft.get("source"));
        assertEquals("CLAUDE", draft.get("mode"));
    }

    @Test void 요약_실패해도_기록은_전달할_수_있다() {
        store.consent("a", true);
        store.event("a", "sa", "SCREEN_ENTER", "transfer", "instant-transfer", null);
        talk();
        AiClient ai = new AiClient() {
            @Override public Answer answer(Context c) { throw new UnsupportedOperationException(); }
            @Override public HandoffSummary summarize(HandoffContext c) { throw new ApiException(504, "AI_TIMEOUT", "시간 초과"); }
        };
        var draft = new HandoffService(store, catalog, ai).draft("a", "sa");
        for (var field : List.of("purpose", "stuckPoint", "triedActions", "unresolved")) assertEquals("확인 안 됨", draft.get(field));
        assertEquals("RECORDED", draft.get("source"));
        assertEquals("AI_TIMEOUT", draft.get("aiError"));
        assertEquals(1, ((List<?>) draft.get("clickTimeline")).size());
        assertEquals(2, ((List<?>) draft.get("conversations")).size());
    }

    @Test void 동의하지_않으면_클릭_기록을_빼고_요약한다() {
        store.event("a", "sa", "SCREEN_ENTER", "transfer", "instant-transfer", null);
        talk();
        var seen = new AtomicReference<AiClient.HandoffContext>();
        AiClient ai = new AiClient() {
            @Override public Answer answer(Context c) { throw new UnsupportedOperationException(); }
            @Override public HandoffSummary summarize(HandoffContext c) { seen.set(c); return new HandoffSummary("a", "b", "c", "d"); }
        };
        var draft = new HandoffService(store, catalog, ai).draft("a", "sa");
        assertTrue(seen.get().clickTimeline().isEmpty());
        assertFalse(seen.get().clickConsent());
        assertEquals(false, draft.get("clickConsent"));
        assertTrue(((List<?>) draft.get("clickTimeline")).isEmpty());
    }

    @Test void 대화_원문은_사투리_그대로_AI에_전달한다() {
        talk();
        var seen = new AtomicReference<AiClient.HandoffContext>();
        AiClient ai = new AiClient() {
            @Override public Answer answer(Context c) { throw new UnsupportedOperationException(); }
            @Override public HandoffSummary summarize(HandoffContext c) { seen.set(c); return new HandoffSummary("a", "b", "c", "d"); }
        };
        new HandoffService(store, catalog, ai).draft("a", "sa");
        assertEquals("이체 한도가 우째 되능교", seen.get().conversation().getFirst().message());
        assertEquals(28, seen.get().menus().size());
    }

    @Test void 모의_요약은_마지막_질문과_안내한_메뉴를_적는다() {
        talk();
        var draft = new HandoffService(store, catalog, new FakeAiClient()).draft("a", "sa");
        assertTrue(((String) draft.get("purpose")).contains("이체 한도"));
        assertTrue(((String) draft.get("triedActions")).contains("이체한도 조회/변경"));
        assertEquals("RECORDED", draft.get("source"));
        assertEquals("FAKE", draft.get("mode"));
    }

    // 작업 25: 상담 요약 입력도 가명화하고, 상담사에게 보이는 대화 원문은 그대로 둔다
    @Test void 요약_입력은_가명화하고_원문은_그대로다() {
        store.consent("a", true);
        store.event("a", "sa", "SEARCH", "menu", "search", "4821 분실");
        var profiles = new com.wontake.assist.ProfileStore(store);
        var guard = new com.wontake.session.UsageGuard();
        var plans = new com.wontake.assist.PlanService(store, new com.wontake.assist.BlockCatalog(), profiles, guard);
        new ChatService(store, catalog, new FakeAiClient(), null, guard, plans).chat("a", "sa", "이체할 때 넷플릭스 55,000원이 1002-123-456789로 나갔어요", "TEXT");
        var seen = new AtomicReference<AiClient.HandoffContext>();
        AiClient ai = new AiClient() {
            @Override public Answer answer(Context c) { throw new UnsupportedOperationException(); }
            @Override public HandoffSummary summarize(HandoffContext c) { seen.set(c); return new HandoffSummary("확인", "확인", "확인", "확인"); }
        };
        var draft = new HandoffService(store, catalog, ai, null, guard, plans).draft("a", "sa");
        var sent = seen.get().conversation().getFirst().message();
        assertEquals("이체할 때 ap-1 [금액]이 [번호]로 나갔어요", sent);
        assertTrue(seen.get().clickTimeline().stream().anyMatch(e -> "card-1 분실".equals(e.query())));
        assertTrue(draft.toString().contains("넷플릭스 55,000원이 1002-123-456789로"));
    }

    // 작업 38: 화면에서 답한 출국일이 요약에 들어가고, 남은 문제로 잘못 분류되지 않는다(모의 AI)
    @Test void 출국일은_상담_요약의_목적에_들어가고_남은_문제가_아니다() {
        var profiles = new com.wontake.assist.ProfileStore(store);
        var guard = new com.wontake.session.UsageGuard();
        var plans = new com.wontake.assist.PlanService(store, new com.wontake.assist.BlockCatalog(), profiles, guard);
        new ChatService(store, catalog, new FakeAiClient(), null, guard, plans).chat("a", "sa", "나 카드 잃어버렸어", "TEXT");
        profiles.state("a", "sa").tripDepartureDate = profiles.today();
        var draft = new HandoffService(store, catalog, new FakeAiClient(), null, guard, plans).draft("a", "sa");
        assertTrue(String.valueOf(draft.get("purpose")).contains("출국일: " + profiles.today() + "(오늘)"), String.valueOf(draft.get("purpose")));
        assertFalse(String.valueOf(draft.get("unresolved")).contains("[고객 정보]"));
    }
}
