package com.wontake.assist;

import com.wontake.ai.AiClient.AiBlock;
import com.wontake.ai.AiClient.AiPlan;
import com.wontake.api.ApiException;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** SPEC R5 업무 화면 규칙 표: 한 행이 테스트 하나. 서버 시계는 2026-10-27(Asia/Seoul) 정오로 고정. */
class DemoActionServiceTest {
    private final DemoStore store = new DemoStore();
    private final ProfileStore profiles = new ProfileStore(store);
    private final PlanService plans = new PlanService(store, new BlockCatalog(), profiles);
    private final DemoActionService demo = new DemoActionService(profiles, plans,
        Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneId.of("Asia/Seoul")));

    private Map<String, Object> act(String action, Object... kv) {
        var body = new LinkedHashMap<String, Object>();
        body.put("action", action);
        for (int i = 0; i < kv.length; i += 2) body.put((String) kv[i], kv[i + 1]);
        return demo.act("a", "sa", body);
    }
    private ApiException fails(String action, Object... kv) { return assertThrows(ApiException.class, () -> act(action, kv)); }
    private Object[] exchange(Object amount, String date) {
        return new Object[]{"currency", "JPY", "amount", amount, "branch", "icn-t1", "date", date, "account", "acc-1"};
    }
    private ProfileStore.CardState card(String ref) { return profiles.state("a", "sa").card(ref); }

    @Test void 사용_중인_카드_재발급은_409() {
        var e = fails("CARD_REISSUE", "ref", "card-1");
        assertEquals(409, e.status);
        assertEquals("CARD_NOT_STOPPED", e.code);
        assertTrue(e.getMessage().contains("분실신고"));
    }

    // 작업 24: 구독은 고객이 고른 다른 보유 카드로 옮긴다(재발급을 기다리지 않음)
    @Test void 옮길_카드를_안_고르면_400() {
        var e = fails("SUBSCRIPTION", "ref", "card-1", "choices", Map.of("ap-1", "SWITCH"));
        assertEquals(400, e.status);
        assertEquals("옮길 카드를 골라 주세요.", e.getMessage());
        assertEquals("ACTIVE", card("card-1").autoPayments.get(0).status);
    }

    @Test void 같은_카드_정지된_카드_갖지_않은_카드로는_옮길_수_없다() {
        act("CARD_STOP", "ref", "card-2");
        for (var to : List.of("card-1", "card-2", "card-9")) {
            var e = fails("SUBSCRIPTION", "ref", "card-1", "choices", Map.of("ap-1", "SWITCH"), "toRef", to);
            assertEquals(400, e.status, to);
            assertEquals("지금 쓸 수 있는 다른 카드를 골라 주세요.", e.getMessage());
        }
        assertEquals("ACTIVE", card("card-1").autoPayments.get(0).status);
    }

    @Test void 사용_중인_다른_카드로_옮기면_옮긴_카드에_표시된다() {
        var view = act("SUBSCRIPTION", "ref", "card-1", "choices", Map.of("ap-1", "SWITCH", "ap-2", "CANCEL"), "toRef", "card-3");
        var from = card("card-1").autoPayments;
        assertEquals("SWITCHED", from.get(0).status);
        assertEquals("card-3", from.get(0).movedTo);
        assertEquals("CANCELLED", from.get(1).status);
        var moved = card("card-3").autoPayments.stream().filter(a -> a.id.equals("ap-1")).findFirst().orElseThrow();
        assertEquals("ACTIVE", moved.status);
        assertEquals("card-1", moved.movedFrom);
        assertTrue(view.toString().contains("movedFrom=card-1"));
    }

    @Test void 구독_해지는_언제든_된다() {
        act("SUBSCRIPTION", "ref", "card-1", "choices", Map.of("ap-2", "CANCEL"));
        assertEquals("CANCELLED", card("card-1").autoPayments.get(1).status);
    }

    @Test void 이미_정지된_카드와_이미_해지한_구독은_그대로다() {
        act("CARD_STOP", "ref", "card-1");
        var again = act("CARD_STOP", "ref", "card-1");
        assertEquals("STOPPED", card("card-1").status);
        assertNotNull(again.get("cards"));
        act("SUBSCRIPTION", "ref", "card-1", "choices", Map.of("ap-2", "CANCEL"));
        act("SUBSCRIPTION", "ref", "card-1", "choices", Map.of("ap-2", "CANCEL"));
        assertEquals("CANCELLED", card("card-1").autoPayments.get(1).status);
    }

    @Test void 엔화_15000은_135750원_15001은_135759원이다() {
        act("EXCHANGE_APPLY", exchange(15000, "2026-10-28"));
        act("EXCHANGE_APPLY", exchange(15001, "2026-10-28"));
        var reqs = profiles.state("a", "sa").exchangeRequests;
        assertEquals(135750L, reqs.get(0).krw);
        assertEquals(135759L, reqs.get(1).krw);
        assertEquals(135759L, DemoActionService.toKrw(15001, new ProfileStore.Rate(100, 905, "일본 엔")));
    }

    @Test void 원화_200만원까지_신청하고_넘으면_400() {
        // 1 USD = 1,380원: 1,449 USD = 1,999,620원 / 1,450 USD = 2,001,000원
        act("EXCHANGE_APPLY", "currency", "USD", "amount", 1449, "branch", "icn-t1", "date", "2026-10-28", "account", "acc-1");
        assertEquals("EXCHANGE_LIMIT", fails("EXCHANGE_APPLY", "currency", "USD", "amount", 1450, "branch", "icn-t1", "date", "2026-10-28", "account", "acc-1").code);
        assertEquals(2_000_000L, DemoActionService.toKrw(2_000_000, new ProfileStore.Rate(1, 1, "원")));
        assertTrue(DemoActionService.overLimit(2_000_001L));
        assertFalse(DemoActionService.overLimit(2_000_000L));
    }

    @Test void 외화_0_음수_소수는_400() {
        for (Object bad : List.of(0, -100, 1.5, "100")) assertEquals(400, fails("EXCHANGE_APPLY", exchange(bad, "2026-10-28")).status, String.valueOf(bad));
    }

    @Test void 수령일은_내일부터_30일_뒤까지다() {
        assertEquals(400, fails("EXCHANGE_APPLY", exchange(1000, "2026-10-27")).status);
        act("EXCHANGE_APPLY", exchange(1000, "2026-10-28"));
        act("EXCHANGE_APPLY", exchange(1000, "2026-11-26"));
        assertEquals(400, fails("EXCHANGE_APPLY", exchange(1000, "2026-11-27")).status);
        assertEquals(400, fails("EXCHANGE_APPLY", exchange(1000, "내일")).status);
    }

    @Test void 트래블카드가_있으면_신청은_409() {
        assertEquals("TRAVEL_CARD_EXISTS", fails("TRAVEL_CARD_APPLY", "account", "acc-1", "receive", "MAIL").code);
        profiles.select("a", "sa", "B");
        act("TRAVEL_CARD_APPLY", "account", "acc-1", "receive", "MAIL");
        assertNotNull(profiles.state("a", "sa").travelCardApplication);
        assertEquals(400, fails("TRAVEL_CARD_APPLY", "account", "acc-9", "receive", "MAIL").status); // 없는 계좌
    }

    @Test void 트래블카드가_정지되면_새로_신청할_수_있다() {
        act("CARD_STOP", "ref", "card-2");
        act("TRAVEL_CARD_APPLY", "account", "acc-1", "receive", "MAIL");
        assertNotNull(profiles.state("a", "sa").travelCardApplication);
        @SuppressWarnings("unchecked") var cards = (List<Map<String, Object>>) demo.state("a", "sa").get("cards");
        assertEquals(false, cards.get(1).get("usable"));
        assertEquals(false, demo.state("a", "sa").get("hasUsableTravelCard"));
    }

    @Test void 분실_카드_확인_전에는_플랜에서_온_화면_처리를_막는다() {
        var p = plans.build("a", "sa", new AiPlan(List.of("카드 분실"), List.of(new AiBlock("card-lost-report", "card-1", "", "차단")), List.of(), ""));
        assertEquals("PLAN_CARD_UNCONFIRMED", fails("CARD_STOP", "ref", "card-1", "planId", p.planId).code);
        plans.confirmCards("a", "sa", p.planId, List.of("card-1"));
        act("CARD_STOP", "ref", "card-1", "planId", p.planId);
        assertEquals("STOPPED", card("card-1").status);
        act("CARD_STOP", "ref", "card-3"); // 플랜 없이 메뉴로 직접 들어가면 고객이 고른 카드라 바로 처리
        assertEquals("STOPPED", card("card-3").status);
    }

    @Test void 플랜_블록에서_화면으로_가서_처리하면_완료된다() {
        var p = plans.build("a", "sa", new AiPlan(List.of("카드 분실"), List.of(new AiBlock("card-lost-report", "card-1", "", "차단"),
            new AiBlock("simple-pay-unlink", "card-1", "", "결제 차단")), List.of(), ""));
        act("CARD_STOP", "ref", "card-1");
        act("SIMPLE_PAY_UNLINK", "ref", "card-1");
        assertEquals(List.of("DONE", "DONE"), p.blocks.stream().map(b -> b.status).toList());
    }

    @Test void 메뉴로_직접_처리해도_열린_플랜에_반영된다() {
        var p = plans.build("a", "sa", new AiPlan(List.of("여행"), List.of(new AiBlock("overseas-payment-check", "card-2", "", "해외 결제"),
            new AiBlock("fx-rate-check", "", "JPY", "환율"), new AiBlock("fx-exchange-apply", "", "JPY", "현금")), List.of(), ""));
        act("OVERSEAS", "ref", "card-2", "on", true);
        act("EXCHANGE_APPLY", exchange(30000, "2026-10-30"));
        assertEquals("DONE", p.blocks.get(0).status);
        assertEquals("DONE", p.blocks.get(2).status);
        assertEquals("PENDING", p.blocks.get(1).status);
        @SuppressWarnings("unchecked") var progress = (Map<String, Map<String, Object>>) demo.state("a", "sa").get("plans");
        assertEquals(2, ((Number) progress.get(p.planId).get("done")).intValue());
    }

    @Test void 화면_상태의_이름과_끝자리는_AI_입력에_없다() throws Exception {
        var json = new JsonMapper();
        var view = json.writeValueAsString(demo.state("a", "sa"));
        assertTrue(view.contains("4821"));
        assertTrue(view.contains("넷플릭스"));
        var shape = json.writeValueAsString(profiles.shape("a", "sa"));
        for (var secret : List.of("4821", "넷플릭스", "17000", "삼성페이")) assertFalse(shape.contains(secret), secret);
    }

    @Test void 알_수_없는_동작과_카드는_400() {
        assertEquals(400, fails("FLY").status);
        assertEquals(400, fails("CARD_STOP", "ref", "card-9").status);
    }
}
