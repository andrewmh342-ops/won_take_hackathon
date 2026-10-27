package com.wontake.assist;

import com.wontake.ai.AiClient;
import com.wontake.ai.AiClient.AiBlock;
import com.wontake.ai.AiClient.AiPlan;
import com.wontake.ai.FakeAiClient;
import com.wontake.catalog.MenuCatalog;
import com.wontake.chat.ChatService;
import com.wontake.session.DemoStore;
import com.wontake.session.UsageGuard;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 대화 한 번의 호출에서 플랜이 만들어지고, 범위 규제·가명화가 함께 지켜지는지. */
class PlanChatTest {
    private final DemoStore store = new DemoStore();
    private final MenuCatalog catalog = new MenuCatalog();
    private final ProfileStore profiles = new ProfileStore(store);
    private final PlanService plans = new PlanService(store, new BlockCatalog(), profiles);

    private ChatService chat(AiClient ai) { return new ChatService(store, catalog, ai, null, new UsageGuard(), plans); }
    private static List<String> ids(ChatService.Reply r) { return ((Plan) r.plan()).blocks.stream().map(b -> b.blockId).toList(); }

    @Test void 같은_여행_질문도_고객_A와_B는_다르게_조립된다() {
        var a = chat(new FakeAiClient()).chat("a", "sa", "다음 달 일본 여행 가는데 준비할 거 알려줘", "TEXT");
        profiles.select("b", "sb", "B");
        var b = chat(new FakeAiClient()).chat("b", "sb", "다음 달 일본 여행 가는데 준비할 거 알려줘", "TEXT");
        assertEquals("PLAN", a.type());
        assertTrue(ids(a).contains("overseas-payment-check"));
        assertFalse(ids(a).contains("travel-card-apply"));
        assertTrue(ids(b).contains("travel-card-apply"));
        assertFalse(ids(b).contains("overseas-payment-check"));
        assertNotEquals(ids(a), ids(b));
    }

    @Test void 모의_AI는_지갑_분실에_카드_블록을_조립한다() {
        var r = chat(new FakeAiClient()).chat("a", "sa", "지갑 잃어버렸어요, 카드 두 장이랑 신분증도", "TEXT");
        var p = (Plan) r.plan();
        assertEquals(2, p.blocks.stream().filter(b -> b.blockId.equals("card-lost-report")).count());
        assertTrue(p.unhandled.stream().anyMatch(x -> x.contains("신분증")));
        assertEquals("counselor-handoff", p.blocks.getLast().blockId);
        assertEquals(0, r.notFoundStreak());
    }

    @Test void AI에는_가명화된_상태와_블록_사전을_보낸다() {
        var seen = new ArrayList<AiClient.Context>();
        chat(c -> { seen.add(c); return new AiClient.Answer("안내", "", List.of("all-accounts"), List.of()); }).chat("a", "sa", "계좌 보여줘", "TEXT");
        var c = seen.getFirst();
        assertEquals(3, c.customer().cards().size()); // SPEC R5 화면: 고객 A 카드 3장
        assertEquals(11, c.blocks().size());
        assertTrue(c.customer().currencies().contains("JPY"));
    }

    @Test void 범위_밖이면_플랜도_버린다() {
        var p = new AiPlan(List.of("x"), List.of(new AiBlock("card-lost-report", "card-1", "", "x")), List.of(), "");
        var r = chat(c -> new AiClient.Answer("x", "", List.of(), List.of(), false, AiClient.Answer.OUT_OF_SCOPE, p)).chat("a", "sa", "농담해 줘", "TEXT");
        assertEquals("OUT_OF_SCOPE", r.type());
        assertNull(r.plan());
    }

    @Test void 검증_뒤_블록이_없으면_기존_답변으로_보낸다() {
        var p = new AiPlan(List.of("x"), List.of(new AiBlock("made-up", "", "", "x")), List.of(), "");
        var r = chat(c -> new AiClient.Answer("전체계좌조회에서 보세요.", "", List.of("all-accounts"), List.of(), false, AiClient.Answer.IN_SCOPE, p)).chat("a", "sa", "계좌", "TEXT");
        assertEquals("ANSWER", r.type());
        assertNull(r.plan());
    }

    @Test void 플랜은_대화_기록에_남아_다시_그릴_수_있다() {
        chat(new FakeAiClient()).chat("a", "sa", "카드 한 장 잃어버렸어", "TEXT");
        var last = store.session("a", "sa").messages().getLast();
        assertNotNull(last.answer().plan());
    }
}
