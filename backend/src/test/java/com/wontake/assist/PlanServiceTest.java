package com.wontake.assist;

import com.wontake.ai.AiClient;
import com.wontake.ai.AiClient.AiBlock;
import com.wontake.ai.AiClient.AiPlan;
import com.wontake.ai.AiClient.AiGoal;
import com.wontake.ai.AiClient.AiConsidered;
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
    /** 작업 23: 카드 분실 플랜은 고객이 분실 카드를 확인한 뒤에만 카드 블록을 실행한다. 테스트는 AI가 고른 카드를 그대로 확인한다. */
    private Plan run(Plan p, int index, Map<String, String> options) {
        if (p.needsCardConfirm) service.confirmCards("a", "sa", p.planId, p.suggestedCards);
        return service.execute("a", "sa", p.planId, index, options);
    }
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

    // 작업 24: 자동결제 변경은 재발급이 아니라 분실신고 뒤면 된다(다른 보유 카드로 옮김)
    @Test void 자동결제_변경만_오면_분실신고를_넣는다() {
        assertEquals(List.of("card-lost-report@card-1", "auto-payment-switch@card-1"),
            ids(build(plan(b("auto-payment-switch", "card-1")))));
    }

    @Test void 트래블카드가_있으면_신청_블록을_버린다() {
        assertEquals(List.of("travel-card-check"), ids(build(plan(b("travel-card-apply", ""), b("travel-card-check", "")))));
    }

    // 작업 26: 보유와 사용 가능을 구분한다.
    @Test void 트래블카드_분실시_신청_블록을_버리지_않는다() {
        profiles.state("a", "sa").card("card-2").status = "STOPPED";
        assertEquals(List.of("travel-card-apply"), ids(build(plan(b("travel-card-apply", "")))));
    }

    @Test void 정지되거나_재발급_대기인_카드는_사용_가능이_아니다() {
        var card = profiles.state("a", "sa").card("card-2");
        assertTrue(profiles.shape("a", "sa").cards().get(1).usable());
        card.status = "STOPPED"; card.reissued = true;
        var shape = profiles.shape("a", "sa").cards().get(1);
        assertFalse(shape.usable());
        assertEquals("REQUESTED", shape.reissueStatus());
        assertFalse(profiles.state("a", "sa").hasUsableTravelCard());
    }

    // 작업 28: plan 출력 확장(목표·필수/선택·넣지 않은 업무)과 보호 우선 순서
    private static AiBlock ask(String param) { return new AiBlock("ask-info", "", param, "판단에 필요해요"); }
    private static AiPlan goals(List<AiGoal> goals, AiBlock... blocks) {
        return new AiPlan(List.of("사건"), List.of(blocks), List.of(), "근거", goals, List.of(), List.of());
    }

    @Test void 질문_블록이_분실신고보다_앞이면_뒤로_옮긴다() {
        var p = build(plan(ask("DEPARTURE_DATE"), b("card-lost-report", "card-2")));
        assertEquals(List.of("card-lost-report@card-2", "ask-info"), ids(p));
        assertTrue(p.blocks.get(1).adjusted);
    }

    @Test void 이미_아는_정보를_묻는_질문은_버리고_이유를_남긴다() {
        profiles.state("a", "sa").tripDepartureDate = java.time.LocalDate.of(2026, 10, 28);
        var p = build(plan(ask("DEPARTURE_DATE"), b("card-lost-report", "card-2")));
        assertEquals(List.of("card-lost-report@card-2"), ids(p));
        assertTrue(p.skipped.stream().anyMatch(s -> "ask-info".equals(s.get("blockId")) && String.valueOf(s.get("reason")).contains("이미 확인")));
    }

    @Test void 묻는_정보_종류가_틀리면_버린다() {
        assertEquals(List.of("card-lost-report@card-2"), ids(build(plan(ask("AGE"), b("card-lost-report", "card-2")))));
    }

    @Test void 필수_선택_표시가_없으면_필수이고_진행률은_필수_기준이다() {
        var p = build(plan(b("card-lost-report", "card-2"), new AiBlock("travel-insurance-guide", "", "", "대비", "OPTIONAL")));
        assertEquals("REQUIRED", p.blocks.get(0).priority);
        assertEquals("OPTIONAL", p.blocks.get(1).priority);
        var summary = service.progressSummary(profiles.state("a", "sa")).get(p.planId);
        assertEquals(1, ((Number) summary.get("total")).intValue());
    }

    @Test void 필수_블록은_다_했지만_필수_목표가_미확보면_완료가_아니다() {
        var s = profiles.state("a", "sa");
        s.card("card-1").status = "STOPPED"; s.card("card-3").status = "STOPPED";
        var p = build(goals(List.of(new AiGoal("CARD_PROTECTION", "분실 카드 보호", true), new AiGoal("PAYMENT_ABROAD", "일본에서 결제", true)),
            b("card-lost-report", "card-2")));
        run(p, 0, Map.of());
        assertEquals("DONE", p.blocks.getFirst().status);
        assertFalse(p.complete);
        assertTrue(p.remaining.stream().anyMatch(r -> r.contains("해외 결제 수단")));
        assertEquals("SECURED", p.goalStatus.getFirst().status());
    }

    @Test void 필수_목표가_모두_확보되면_완료다() {
        profiles.state("a", "sa").card("card-1").overseasPayment = true; // 작업 34: 기본값이 꺼짐이라 켠 상태를 만든다
        var p = build(goals(List.of(new AiGoal("CARD_PROTECTION", "보호", true), new AiGoal("PAYMENT_ABROAD", "결제", true)), b("card-lost-report", "card-2")));
        run(p, 0, Map.of());
        assertTrue(p.complete); // card-1이 해외결제 켜진 사용 가능 카드
    }

    @Test void AI와_서버가_뺀_업무를_이유와_함께_보여준다() {
        var ai = new AiPlan(List.of("분실"), List.of(b("card-lost-report", "card-2"), b("simple-pay-unlink", "card-2")), List.of(), "",
            List.of(), List.of(), List.of(new AiConsidered("travel-card-apply", "재발급하므로 새로 신청하지 않아요")));
        var p = build(ai);
        assertEquals("travel-card-apply", p.considered.getFirst().get("blockId"));
        assertTrue(p.skipped.stream().anyMatch(s -> "simple-pay-unlink".equals(s.get("blockId")) && String.valueOf(s.get("reason")).contains("간편결제")));
    }

    @Test void 잃어버린_카드는_대체_결제_수단으로_넣지_않는다() {
        var p = build(plan(b("card-lost-report", "card-2"), b("alt-payment-check", "card-2"), b("alt-payment-check", "card-1")));
        assertEquals(List.of("card-lost-report@card-2", "alt-payment-check@card-1"), ids(p));
    }

    // 작업 23: 분실 카드 직접 확인
    private Plan lossPlan() {
        return build(plan(b("card-lost-report", "card-1"), b("simple-pay-unlink", "card-1"), b("auto-payment-switch", "card-1"), b("card-reissue", "card-1")));
    }

    @Test void 카드_분실_플랜은_분실_카드_확인이_먼저다() {
        var p = lossPlan();
        assertTrue(p.needsCardConfirm);
        assertEquals(List.of("card-1"), p.suggestedCards);
        assertNull(p.confirmedCards);
    }

    @Test void 확인_전_카드_블록은_409() {
        var p = lossPlan();
        var e = assertThrows(ApiException.class, () -> service.execute("a", "sa", p.planId, 0, Map.of()));
        assertEquals(409, e.status);
        assertEquals("PLAN_CARD_UNCONFIRMED", e.code);
        assertTrue(e.getMessage().contains("잃어버린 카드"));
    }

    @Test void 확인할_카드가_없거나_갖지_않은_카드면_400() {
        var p = lossPlan();
        assertEquals(400, assertThrows(ApiException.class, () -> service.confirmCards("a", "sa", p.planId, List.of())).status);
        assertEquals(400, assertThrows(ApiException.class, () -> service.confirmCards("a", "sa", p.planId, List.of("card-9"))).status);
        assertTrue(p.needsCardConfirm);
    }

    @Test void AI와_다른_카드를_확인하면_그_카드_기준으로_다시_구성한다() {
        var p = lossPlan();
        service.confirmCards("a", "sa", p.planId, List.of("card-3"));
        assertFalse(p.needsCardConfirm);
        assertEquals(List.of("card-3"), p.confirmedCards);
        assertTrue(p.blocks.stream().noneMatch(x -> x.targetRef.equals("card-1")));
        assertEquals(List.of("card-lost-report@card-3", "simple-pay-unlink@card-3", "auto-payment-switch@card-3", "card-reissue@card-3"),
            ids(p).stream().filter(x -> x.contains("@")).toList().stream().sorted(java.util.Comparator.comparingInt(x -> List.of("card-lost-report", "simple-pay-unlink", "auto-payment-switch", "card-reissue").indexOf(x.split("@")[0]))).toList());
        assertEquals("card-lost-report", p.blocks.getFirst().blockId);
    }

    @Test void 두_장을_확인하면_두_장_모두_분실신고가_앞에_온다() {
        var p = lossPlan();
        service.confirmCards("a", "sa", p.planId, List.of("card-1", "card-2"));
        assertEquals(2, p.blocks.stream().filter(x -> x.blockId.equals("card-lost-report")).count());
        assertEquals("card-lost-report", p.blocks.get(0).blockId);
        assertEquals("card-lost-report", p.blocks.get(1).blockId);
        assertTrue(p.blocks.stream().noneMatch(x -> x.blockId.equals("simple-pay-unlink") && x.targetRef.equals("card-2"))); // card-2는 간편결제 없음
    }

    @Test void 이미_확인한_플랜을_다시_확인하면_409() {
        var p = lossPlan();
        service.confirmCards("a", "sa", p.planId, List.of("card-1"));
        assertEquals("PLAN_ALREADY_CONFIRMED", assertThrows(ApiException.class, () -> service.confirmCards("a", "sa", p.planId, List.of("card-1"))).code);
    }

    @Test void 분실이_아닌_플랜은_확인_단계가_없다() {
        assertFalse(build(plan(b("overseas-payment-check", "card-2"), fx("fx-rate-check", "JPY"))).needsCardConfirm);
    }

    // 작업 29: 정보 입력
    private Plan askPlan() { return build(plan(b("card-lost-report", "card-2"), ask("DEPARTURE_DATE"), ask("COUNTRY"))); }
    private int indexOf(Plan p, String param) { return p.blocks.stream().filter(x -> x.param.equals(param)).findFirst().orElseThrow().index; }

    @Test void 출국일은_오늘부터_365일_뒤까지_저장한다() {
        var today = profiles.today();
        var p = askPlan();
        service.answer("a", "sa", p.planId, indexOf(p, "DEPARTURE_DATE"), today.toString());
        assertEquals(today, profiles.state("a", "sa").tripDepartureDate);
        assertEquals("DONE", p.blocks.get(indexOf(p, "DEPARTURE_DATE")).status);
        var q = askPlan();
        assertFalse(q.blocks.stream().anyMatch(x -> x.param.equals("DEPARTURE_DATE"))); // 이미 아는 정보는 다시 묻지 않음
        profiles.state("a", "sa").tripDepartureDate = null;
        var r = askPlan();
        service.answer("a", "sa", r.planId, indexOf(r, "DEPARTURE_DATE"), today.plusDays(365).toString());
        assertEquals(365, profiles.daysUntilDeparture(profiles.state("a", "sa")));
    }

    @Test void 과거_출국일은_400() {
        var p = askPlan();
        assertEquals(400, assertThrows(ApiException.class, () -> service.answer("a", "sa", p.planId, indexOf(p, "DEPARTURE_DATE"), profiles.today().minusDays(1).toString())).status);
        assertEquals(400, assertThrows(ApiException.class, () -> service.answer("a", "sa", p.planId, indexOf(p, "DEPARTURE_DATE"), profiles.today().plusDays(366).toString())).status);
        assertEquals(400, assertThrows(ApiException.class, () -> service.answer("a", "sa", p.planId, indexOf(p, "DEPARTURE_DATE"), "내일")).status);
        assertNull(profiles.state("a", "sa").tripDepartureDate);
    }

    @Test void 여행_나라는_1자에서_20자까지_저장한다() {
        var p = askPlan();
        assertEquals(400, assertThrows(ApiException.class, () -> service.answer("a", "sa", p.planId, indexOf(p, "COUNTRY"), " ")).status);
        assertEquals(400, assertThrows(ApiException.class, () -> service.answer("a", "sa", p.planId, indexOf(p, "COUNTRY"), "가".repeat(21))).status);
        service.answer("a", "sa", p.planId, indexOf(p, "COUNTRY"), "일본");
        assertEquals("일본", profiles.state("a", "sa").tripCountry);
    }

    @Test void 정보_확인_블록이_아니면_답할_수_없다() {
        var p = askPlan();
        assertEquals(400, assertThrows(ApiException.class, () -> service.answer("a", "sa", p.planId, 0, "2026-12-01")).status);
    }

    @Test void 해외결제가_켜진_카드는_설정_블록을_버린다() {
        profiles.state("a", "sa").card("card-1").overseasPayment = true; // 작업 34: 기본값이 꺼짐이라 켠 상태를 만든다
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
        // 작업 36: 같은 통화의 환율·환전은 하나로 합쳐지므로 합쳐지지 않는 블록 13개로 센다
        for (var c : List.of("JPY", "USD", "EUR", "CNY")) many.add(fx("fx-rate-check", c));
        for (var r : List.of("card-1", "card-2", "card-3")) { many.add(b("card-lost-report", r)); many.add(b("card-reissue", r)); }
        many.addAll(List.of(b("simple-pay-unlink", "card-1"), b("travel-card-check", ""), b("travel-insurance-guide", "")));
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
        var e = assertThrows(ApiException.class, () -> run(p, 1, Map.of()));
        assertEquals(409, e.status);
        assertEquals("PLAN_PREREQUISITE", e.code);
        assertTrue(e.getMessage().contains("카드 분실신고"));
        run(p, 0, Map.of());
        assertEquals("DONE", run(p, 1, Map.of()).blocks.get(1).status);
    }

    @Test void 끝난_블록은_다시_실행해도_같은_결과다() {
        var p = build(plan(b("card-lost-report", "card-1")));
        var first = run(p, 0, Map.of()).blocks.getFirst().detail;
        assertEquals(first, run(p, 0, Map.of()).blocks.getFirst().detail);
        assertEquals("STOPPED", profiles.state("a", "sa").card("card-1").status);
    }

    @Test void 자동결제는_고른_건만_해지한다() {
        var p = build(plan(b("card-lost-report", "card-1"), b("card-reissue", "card-1"), b("auto-payment-switch", "card-1")));
        run(p, 0, Map.of());
        run(p, 1, Map.of());
        int sw = p.blocks.stream().filter(x -> x.blockId.equals("auto-payment-switch")).findFirst().orElseThrow().index;
        assertEquals(400, assertThrows(ApiException.class, () -> run(p, sw, Map.of("ap-2", "CANCEL"))).status); // 옮길 카드 없음
        run(p, sw, Map.of("ap-2", "CANCEL", "toRef", "card-3"));
        var aps = profiles.state("a", "sa").card("card-1").autoPayments;
        assertEquals(List.of("SWITCHED", "CANCELLED", "SWITCHED"), aps.stream().map(x -> x.status).toList());
        assertEquals(4, profiles.state("a", "sa").card("card-3").autoPayments.size()); // 기존 2건 + 옮겨 온 2건
        assertEquals("DONE", p.blocks.get(sw).status);
    }

    // 작업 32: AI가 쓴 문장의 ref는 화면에서 이름으로 복원한다(AI 입력은 가명화 그대로)
    private Plan refPlan() {
        return build(new AiPlan(List.of("card-2 분실"), List.of(new AiBlock("card-lost-report", "card-2", "", "card-2 사용을 막아요")),
            List.of("card-2 충전 잔액 처리"), "card-2부터 막아요", List.of(),
            List.of(new AiClient.AiObstacle("card-2를 못 써요", "BLOCK", List.of())),
            List.of(new AiConsidered("auto-payment-switch", "ap-1은 card-1에 걸려 있어요"))));
    }

    @Test void 블록_이유의_card_ref는_가상_카드_이름으로_복원한다() {
        var p = refPlan();
        String travel = profiles.state("a", "sa").card("card-2").display(), credit = profiles.state("a", "sa").card("card-1").display();
        assertEquals(travel + " 분실", p.events.getFirst());
        assertEquals(travel + " 사용을 막아요", p.blocks.getFirst().reason);
        assertEquals(travel + "부터 막아요", p.orderReason);
        assertEquals(List.of(travel + " 충전 잔액 처리"), p.unhandled);
        assertEquals(travel + "를 못 써요", p.obstacles.getFirst().get("text"));
        assertEquals("넷플릭스은 " + credit + "에 걸려 있어요", p.considered.getFirst().get("reason"));
    }

    @Test void 상담_요약_AI_입력용_진행은_ref_그대로다() {
        refPlan();
        var forAi = String.join("\n", service.progress("a", "sa", false));
        assertTrue(forAi.contains("[상담 필요] card-2 충전 잔액 처리"), forAi);
        assertFalse(forAi.contains("7310"));
        assertTrue(String.join("\n", service.progress("a", "sa", true)).contains("7310"));
    }

    // 작업 35: 여행 정보 기억
    @Test void 이전_플랜의_통화로_여행_나라를_기억한다() {
        build(plan(fx("fx-exchange-apply", "JPY")));
        assertEquals("일본", profiles.state("a", "sa").tripCountry);
        assertEquals("일본", service.shape("a", "sa").trip().country());
    }

    @Test void 이미_아는_나라는_다시_묻지_않는다() {
        build(plan(fx("fx-rate-check", "USD")));
        var p = build(plan(b("card-lost-report", "card-1"), ask("COUNTRY")));
        assertTrue(p.blocks.stream().noneMatch(x -> x.blockId.equals("ask-info")));
        assertTrue(p.skipped.stream().anyMatch(x -> "이미 확인한 정보예요".equals(x.get("reason"))));
    }

    @Test void 이미_정해진_여행_나라는_다른_통화로_바꾸지_않는다() {
        profiles.state("a", "sa").tripCountry = "일본";
        build(plan(fx("fx-rate-check", "USD")));
        assertEquals("일본", profiles.state("a", "sa").tripCountry);
    }

    @Test void 환전을_신청한_통화는_AI_입력에_금액_없이_들어간다() {
        profiles.state("a", "sa").exchangeRequests.add(new ProfileStore.ExchangeRequest("ex-1", "JPY", 30000, 271500, "지점", "2026-11-01", "계좌"));
        var shape = service.shape("a", "sa");
        assertEquals(List.of("JPY"), shape.cash());
        assertFalse(shape.toString().contains("30000"));
    }

    @Test void 최근_플랜_두_개를_ref로_요약한다() {
        var first = build(plan(b("card-lost-report", "card-1")));
        run(first, 0, Map.of());
        build(plan(fx("fx-rate-check", "JPY")));
        var third = build(plan(b("travel-card-check", "")));
        var recent = service.recent("a", "sa", third.planId);
        assertEquals(2, recent.size());
        assertTrue(recent.stream().anyMatch(r -> r.done().contains("card-lost-report@card-1")));
        assertTrue(recent.stream().anyMatch(r -> r.pending().contains("fx-rate-check")));
        assertFalse(recent.toString().contains("4821")); // 카드 이름·끝자리 없음
    }

    // 작업 36: 같은 효과의 단계는 하나로, 출국일은 첫 단계로
    private static AiBlock opt(String id, String ref, String param) { return new AiBlock(id, ref, param, "이유", "OPTIONAL"); }
    private static String priorityOf(Plan p, String key) {
        return p.blocks.stream().filter(x -> (x.blockId + (x.targetRef.isEmpty() ? "" : "@" + x.targetRef)).equals(key)).findFirst().orElseThrow().priority;
    }

    @Test void 같은_통화의_환율_확인은_환전_신청에_합친다() {
        var p = build(plan(fx("fx-rate-check", "JPY"), opt("fx-exchange-apply", "", "JPY")));
        assertEquals(List.of("fx-exchange-apply"), ids(p));
        assertEquals("REQUIRED", p.blocks.getFirst().priority); // 둘 중 높은 쪽
        assertTrue(p.skipped.stream().anyMatch(x -> "fx-rate-check".equals(x.get("blockId")) && "환전 화면에서 환율을 함께 보여 줘요".equals(x.get("reason"))));
    }

    @Test void 통화가_다르면_환율과_환전을_둘_다_남긴다() {
        assertEquals(List.of("fx-rate-check", "fx-exchange-apply"), ids(build(plan(fx("fx-rate-check", "JPY"), fx("fx-exchange-apply", "USD")))));
    }

    @Test void 해외결제가_꺼진_카드의_대체_확인과_설정은_설정_하나로() {
        assertEquals(List.of("overseas-payment-check@card-1"), ids(build(plan(b("alt-payment-check", "card-1"), b("overseas-payment-check", "card-1")))));
    }

    @Test void 해외결제가_켜진_카드의_대체_확인과_설정은_확인_하나로() {
        profiles.state("a", "sa").card("card-1").overseasPayment = true;
        assertEquals(List.of("alt-payment-check@card-1"), ids(build(plan(b("alt-payment-check", "card-1"), b("overseas-payment-check", "card-1")))));
    }

    @Test void 해외결제_설정이_여러_장이면_첫_장만_필수() {
        var p = build(plan(b("overseas-payment-check", "card-1"), b("overseas-payment-check", "card-3")));
        assertEquals("REQUIRED", priorityOf(p, "overseas-payment-check@card-1"));
        assertEquals("OPTIONAL", priorityOf(p, "overseas-payment-check@card-3"));
    }

    @Test void 해외에서_쓸_카드가_이미_있으면_해외결제_설정은_선택() {
        profiles.state("a", "sa").card("card-2").overseasPayment = true;
        var p = build(plan(b("overseas-payment-check", "card-1")));
        assertEquals("OPTIONAL", priorityOf(p, "overseas-payment-check@card-1"));
    }

    @Test void 여행_플랜에서_출국일을_모르면_첫_단계로_묻는다() {
        var p = build(plan(b("travel-card-check", ""), b("overseas-payment-check", "card-2"), ask("DEPARTURE_DATE")));
        assertEquals("ask-info", p.blocks.getFirst().blockId);
        var q = build(plan(b("card-lost-report", "card-2"), b("card-reissue", "card-2"), ask("DEPARTURE_DATE")));
        assertEquals(List.of("card-lost-report@card-2", "ask-info", "card-reissue@card-2"), ids(q)); // 보호 조치 다음
    }

    // 작업 38: 상담 요약 입력에 고객이 화면에서 답한 여행 정보를 넣는다
    @Test void 출국일을_답하면_상담_요약_입력에_들어간다() {
        var p = askPlan();
        var today = profiles.today();
        service.answer("a", "sa", p.planId, indexOf(p, "DEPARTURE_DATE"), today.toString());
        var forAi = service.progress("a", "sa", false);
        assertTrue(forAi.contains("[고객 정보] 출국일: " + today + "(오늘)"), forAi.toString());
        assertTrue(forAi.stream().anyMatch(l -> l.startsWith("[완료] 정보 확인") && l.contains("출국일 " + today)), forAi.toString());
    }

    @Test void 여행_나라와_환전_통화도_상담_요약_입력에_들어간다() {
        var s = profiles.state("a", "sa");
        s.tripCountry = "일본";
        s.tripDepartureDate = profiles.today().plusDays(3);
        s.exchangeRequests.add(new ProfileStore.ExchangeRequest("ex-1", "JPY", 30000, 271500, "지점", "2026-11-01", "계좌"));
        build(plan(b("card-lost-report", "card-2")));
        var forAi = service.progress("a", "sa", false);
        assertTrue(forAi.contains("[고객 정보] 여행 나라: 일본"), forAi.toString());
        assertTrue(forAi.contains("[고객 정보] 출국일: " + profiles.today().plusDays(3) + "(3일 뒤)"), forAi.toString());
        assertTrue(forAi.contains("[고객 정보] 환전 신청: JPY"), forAi.toString());
        assertFalse(String.join(" ", forAi).contains("30000"));
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
        run(p, 0, Map.of());
        assertTrue(service.progress("a", "sa", false).getFirst().contains("card-1"));
        assertFalse(String.join(" ", service.progress("a", "sa", false)).contains("4821"));
        assertTrue(service.progress("a", "sa", true).getFirst().contains("4821")); // 상담사 화면용
    }

    @Test void AI_요약의_카드_ref는_상담사_화면에서_카드_이름으로_복원한다() {
        assertEquals("우리 신용카드 끝자리 4821(가상) 분실신고 완료", service.restore("a", "sa", "card-1 분실신고 완료"));
    }

    @Test void 상담_요약에_플랜_진행을_넘긴다() {
        var p = build(plan(List.of("신분증 분실 신고"), b("card-lost-report", "card-1"), b("card-reissue", "card-1")));
        run(p, 0, Map.of());
        var progress = service.progress("a", "sa");
        assertTrue(progress.stream().anyMatch(x -> x.startsWith("[완료] 카드 분실신고")));
        assertTrue(progress.stream().anyMatch(x -> x.startsWith("[남음] 카드 재발급")));
        assertTrue(progress.stream().anyMatch(x -> x.startsWith("[상담 필요] 신분증")));
    }
}
