package com.wontake.assist;

import com.wontake.ai.AiClient.AiBlock;
import com.wontake.ai.AiClient.AiPlan;
import com.wontake.api.ApiException;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** SPEC R5 규칙 표: 한 행이 테스트 하나. 고객 A는 card-1(신용, 간편결제·자동결제 3건·해외결제 켜짐), card-2(트래블, 해외결제 꺼짐). */
class PlanServiceTest {
    private final DemoStore store = new DemoStore();
    private final ProfileStore profiles = new ProfileStore(store);
    private final PlanService service = new PlanService(store, new BlockCatalog(), profiles);

    private static AiBlock b(String id, String ref) { return new AiBlock(id, ref, "", "이유"); }
    private static AiBlock fx(String id, String currency) { return new AiBlock(id, "", currency, "이유"); }
    private static AiPlan plan(AiBlock... blocks) { return new AiPlan(List.of("사건"), List.of(blocks), List.of(), "순서 근거"); }
    private static AiPlan plan(List<String> unhandled, AiBlock... blocks) { return new AiPlan(List.of("사건"), List.of(blocks), unhandled, "순서 근거"); }
    private Plan build(AiPlan p) { return service.build("a", "sa", p); }
    private static List<String> ids(Plan p) { return p.blocks.stream().map(x -> x.blockId + (x.targetRef.isEmpty() ? "" : "@" + x.targetRef)).toList(); }

    @Test void 사전에_없는_블록은_버린다() {
        assertEquals(List.of("card-lost-report@card-1"), ids(build(plan(b("card-teleport", ""), b("card-lost-report", "card-1")))));
    }

    @Test void 갖지_않은_카드는_버린다() {
        assertEquals(List.of("card-lost-report@card-1"), ids(build(plan(b("card-lost-report", "card-9"), b("card-lost-report", "card-1")))));
    }

    @Test void 같은_블록은_하나만_남긴다() {
        assertEquals(1, build(plan(b("card-lost-report", "card-1"), b("card-lost-report", "card-1"))).blocks.size());
    }

    @Test void 재발급이_분실신고보다_앞이면_뒤로_옮긴다() {
        var p = build(plan(b("card-reissue", "card-1"), b("card-lost-report", "card-1")));
        assertEquals(List.of("card-lost-report@card-1", "card-reissue@card-1"), ids(p));
        assertTrue(p.blocks.get(1).adjusted);
    }

    @Test void 재발급만_오면_분실신고를_앞에_넣는다() {
        var p = build(plan(b("card-reissue", "card-2")));
        assertEquals(List.of("card-lost-report@card-2", "card-reissue@card-2"), ids(p));
        assertTrue(p.blocks.getFirst().adjusted);
    }

    @Test void 자동결제_변경만_오면_분실신고와_재발급을_넣는다() {
        assertEquals(List.of("card-lost-report@card-1", "card-reissue@card-1", "auto-payment-switch@card-1"),
            ids(build(plan(b("auto-payment-switch", "card-1")))));
    }

    @Test void 트래블카드가_있으면_신청_블록을_버린다() {
        assertEquals(List.of("travel-card-check"), ids(build(plan(b("travel-card-apply", ""), b("travel-card-check", "")))));
    }

    @Test void 해외결제가_켜진_카드는_설정_블록을_버린다() {
        assertEquals(List.of("overseas-payment-check@card-2"), ids(build(plan(b("overseas-payment-check", "card-1"), b("overseas-payment-check", "card-2")))));
    }

    @Test void 지원하는_통화는_환율을_보여_준다() {
        var p = build(plan(fx("fx-rate-check", "jpy")));
        assertEquals("JPY", p.blocks.getFirst().param);
        assertTrue(p.blocks.getFirst().detail.contains("905"));
        assertTrue(p.blocks.getFirst().detail.contains("가상"));
    }

    @Test void 지원하지_않는_통화는_버리고_상담으로_넘긴다() {
        var p = build(plan(fx("fx-rate-check", "MNT"), fx("fx-exchange-apply", "MNT"), b("travel-insurance-guide", "")));
        assertEquals(List.of("travel-insurance-guide", "counselor-handoff"), ids(p));
        assertTrue(p.unhandled.getFirst().contains("MNT"));
    }

    @Test void 처리_못한_일이_있으면_상담_블록을_끝에_넣는다() {
        var p = build(plan(List.of("신분증 분실 신고"), b("card-lost-report", "card-1")));
        assertEquals("counselor-handoff", p.blocks.getLast().blockId);
    }

    @Test void 남은_블록이_없으면_플랜이_없다() {
        assertNull(build(plan(b("card-teleport", ""))));
        assertNull(build(new AiPlan(List.of(), List.of(), List.of(), "")));
    }

    @Test void 블록은_12개까지만_둔다() {
        var many = new ArrayList<AiBlock>();
        for (var c : List.of("JPY", "USD", "EUR", "CNY")) { many.add(fx("fx-rate-check", c)); many.add(fx("fx-exchange-apply", c)); }
        many.addAll(List.of(b("card-lost-report", "card-1"), b("card-lost-report", "card-2"), b("card-reissue", "card-1"), b("card-reissue", "card-2"), b("travel-insurance-guide", "")));
        assertEquals(13, many.size());
        assertEquals(12, build(new AiPlan(List.of(), many, List.of(), "")).blocks.size());
    }

    @Test void 이유와_처리_못한_일은_길이를_자른다() {
        var p = build(new AiPlan(List.of(), List.of(new AiBlock("card-lost-report", "card-1", "", "가".repeat(121)), new AiBlock("card-lost-report", "card-2", "", "나".repeat(120))),
            List.of("다".repeat(101), "라".repeat(100)), ""));
        assertEquals(120, p.blocks.get(0).reason.length());
        assertEquals(120, p.blocks.get(1).reason.length());
        assertEquals(100, p.unhandled.get(0).length());
        assertEquals(100, p.unhandled.get(1).length());
    }

    @Test void 선행_미완료면_409() {
        var p = build(plan(b("card-lost-report", "card-1"), b("card-reissue", "card-1")));
        var e = assertThrows(ApiException.class, () -> service.execute("a", "sa", p.planId, 1, Map.of()));
        assertEquals(409, e.status);
        assertEquals("PLAN_PREREQUISITE", e.code);
        assertTrue(e.getMessage().contains("카드 분실신고"));
        service.execute("a", "sa", p.planId, 0, Map.of());
        assertEquals("DONE", service.execute("a", "sa", p.planId, 1, Map.of()).blocks.get(1).status);
    }

    @Test void 끝난_블록은_다시_실행해도_같은_결과다() {
        var p = build(plan(b("card-lost-report", "card-1")));
        var first = service.execute("a", "sa", p.planId, 0, Map.of()).blocks.getFirst().detail;
        assertEquals(first, service.execute("a", "sa", p.planId, 0, Map.of()).blocks.getFirst().detail);
        assertEquals("STOPPED", profiles.state("a", "sa").card("card-1").status);
    }

    @Test void 자동결제는_고른_건만_해지한다() {
        var p = build(plan(b("card-lost-report", "card-1"), b("card-reissue", "card-1"), b("auto-payment-switch", "card-1")));
        service.execute("a", "sa", p.planId, 0, Map.of());
        service.execute("a", "sa", p.planId, 1, Map.of());
        service.execute("a", "sa", p.planId, 2, Map.of("ap-2", "CANCEL"));
        var aps = profiles.state("a", "sa").card("card-1").autoPayments;
        assertEquals(List.of("SWITCHED", "CANCELLED", "SWITCHED"), aps.stream().map(x -> x.status).toList());
    }

    @Test void 다른_세션의_플랜은_404() {
        var p = build(plan(b("card-lost-report", "card-1")));
        assertEquals("PLAN_NOT_FOUND", assertThrows(ApiException.class, () -> service.execute("b", "sb", p.planId, 0, Map.of())).code);
    }

    @Test void AI에는_끝자리_금액_가맹점명을_보내지_않는다() throws Exception {
        var shape = new JsonMapper().writeValueAsString(profiles.shape("a", "sa"));
        for (var secret : List.of("4821", "7310", "55000", "가상텔레콤", "가상플릭스")) assertFalse(shape.contains(secret), secret);
        assertTrue(shape.contains("card-1"));
        assertTrue(shape.contains("통신"));
    }

    @Test void 화면에는_복원된_카드_이름이_있다() {
        var block = build(plan(b("card-lost-report", "card-1"))).blocks.getFirst();
        assertTrue(block.target.contains("4821"));
        assertTrue(block.target.contains("가상"));
    }

    @Test void 시연_고객을_바꾸면_상태와_플랜이_초기화된다() {
        var p = build(plan(b("card-lost-report", "card-1")));
        profiles.select("a", "sa", "B");
        assertFalse(profiles.state("a", "sa").hasTravelCard);
        assertEquals("PLAN_NOT_FOUND", assertThrows(ApiException.class, () -> service.execute("a", "sa", p.planId, 0, Map.of())).code);
        assertEquals("INVALID_REQUEST", assertThrows(ApiException.class, () -> profiles.select("a", "sa", "Z")).code);
    }

    @Test void 상담_요약_AI에는_카드_끝자리를_넘기지_않는다() {
        var p = build(plan(b("card-lost-report", "card-1")));
        service.execute("a", "sa", p.planId, 0, Map.of());
        assertTrue(service.progress("a", "sa", false).getFirst().contains("card-1"));
        assertFalse(String.join(" ", service.progress("a", "sa", false)).contains("4821"));
        assertTrue(service.progress("a", "sa", true).getFirst().contains("4821")); // 상담사 화면용
    }

    @Test void AI_요약의_카드_ref는_상담사_화면에서_카드_이름으로_복원한다() {
        assertEquals("우리 신용카드 끝자리 4821(가상) 분실신고 완료", service.restore("a", "sa", "card-1 분실신고 완료"));
    }

    @Test void 상담_요약에_플랜_진행을_넘긴다() {
        var p = build(plan(List.of("신분증 분실 신고"), b("card-lost-report", "card-1"), b("card-reissue", "card-1")));
        service.execute("a", "sa", p.planId, 0, Map.of());
        var progress = service.progress("a", "sa");
        assertTrue(progress.stream().anyMatch(x -> x.startsWith("[완료] 카드 분실신고")));
        assertTrue(progress.stream().anyMatch(x -> x.startsWith("[남음] 카드 재발급")));
        assertTrue(progress.stream().anyMatch(x -> x.startsWith("[상담 필요] 신분증")));
    }
}
