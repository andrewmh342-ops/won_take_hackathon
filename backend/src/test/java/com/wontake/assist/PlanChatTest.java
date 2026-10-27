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

    @Test void 모의_AI도_트래블카드_분실과_여행을_엮는다() {
        var r = chat(new FakeAiClient()).chat("a", "sa", "일본 가려는데, 트래블카드 잃어버렸어", "TEXT");
        var p = (Plan) r.plan();
        assertEquals("card-lost-report", p.blocks.getFirst().blockId);
        assertEquals("card-2", p.blocks.getFirst().targetRef);
        assertTrue(ids(r).contains("ask-info"));
        assertTrue(ids(r).indexOf("ask-info") > ids(r).indexOf("card-lost-report"));
        assertTrue(p.blocks.stream().anyMatch(b -> b.blockId.equals("alt-payment-check") && b.targetRef.equals("card-1")));
        assertFalse(ids(r).contains("simple-pay-unlink"));
        assertTrue(p.goalStatus.stream().anyMatch(g -> g.type().equals("PAYMENT_ABROAD")));
        assertTrue(p.blocks.stream().filter(b -> b.blockId.equals("travel-insurance-guide")).allMatch(b -> b.priority.equals("OPTIONAL")));
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
        assertEquals(13, c.blocks().size()); // SPEC R5 목표 중심: 블록 11→13
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

    // 작업 23: 확인 후 AI 재판단
    @Test void 분실_카드를_확인하면_AI가_확인한_카드로_다시_판단한다() {
        var seen = new ArrayList<AiClient.Context>();
        var service = chat(c -> { seen.add(c); return new FakeAiClient().answer(c); });
        var r = service.chat("a", "sa", "카드 한 장 잃어버렸어", "TEXT");
        var p = (Plan) r.plan();
        assertTrue(p.needsCardConfirm);
        var after = service.confirmCards("a", "sa", p.planId, List.of("card-3"));
        assertEquals(2, seen.size());
        assertEquals(List.of("card-3"), seen.get(1).planState().confirmedLostCards());
        assertEquals("카드 한 장 잃어버렸어", seen.get(1).question());
        assertTrue(after.blocks.stream().filter(b -> PlanService.LOSS_BLOCKS.contains(b.blockId)).allMatch(b -> b.targetRef.equals("card-3")));
        assertEquals(p.planId, after.planId);
    }

    @Test void 재판단이_실패하면_확인한_카드_기준으로_정리하고_알린다() {
        var calls = new int[1];
        var service = chat(c -> { if (calls[0]++ > 0) throw new com.wontake.api.ApiException(504, "AI_TIMEOUT", "늦음"); return new FakeAiClient().answer(c); });
        var p = (Plan) service.chat("a", "sa", "카드 한 장 잃어버렸어", "TEXT").plan();
        var after = service.confirmCards("a", "sa", p.planId, List.of("card-3"));
        assertTrue(after.notice.contains("다시 판단하지 못했어요"));
        assertFalse(after.needsCardConfirm);
        assertTrue(after.blocks.stream().anyMatch(b -> b.blockId.equals("card-lost-report") && b.targetRef.equals("card-3")));
    }

    // 작업 29: 정보 입력 후 AI 재판단
    @Test void 출국일을_입력하면_AI가_다시_판단하고_끝낸_업무는_유지한다() {
        var seen = new ArrayList<AiClient.Context>();
        var service = chat(c -> { seen.add(c); return new FakeAiClient().answer(c); });
        var p = (Plan) service.chat("a", "sa", "일본 가려는데, 트래블카드 잃어버렸어", "TEXT").plan();
        service.confirmCards("a", "sa", p.planId, List.of("card-2"));
        plans.execute("a", "sa", p.planId, 0, Map.of()); // 분실신고(card-2)
        int ask = p.blocks.stream().filter(b -> b.blockId.equals("ask-info")).findFirst().orElseThrow().index;
        var after = service.answer("a", "sa", p.planId, ask, profiles.today().plusDays(1).toString());
        var last = seen.getLast();
        assertEquals(1, last.customer().trip().daysUntilDeparture());
        assertTrue(last.planState().doneBlocks().contains("card-lost-report@card-2"));
        assertTrue(after.blocks.stream().anyMatch(b -> b.blockId.equals("card-lost-report") && b.status.equals("DONE")));
        assertTrue(after.blocks.stream().noneMatch(b -> b.blockId.equals("ask-info") && b.status.equals("PENDING")));
        assertTrue(after.goalStatus.stream().anyMatch(g -> g.type().equals("PAYMENT_ABROAD") && g.status().equals("SECURED")));
    }

    // 작업 25: 대화 입력 전체 가명화
    @Test void AI에는_가명화한_질문과_이전_대화가_가고_화면_기록은_원문이다() {
        var seen = new ArrayList<AiClient.Context>();
        var service = chat(c -> { seen.add(c); return new FakeAiClient().answer(c); });
        service.chat("a", "sa", "자동결제 관리에서 넷플릭스 55,000원 결제 확인", "TEXT");
        var r = service.chat("a", "sa", "4821 카드 잃어버렸어, 010-1234-5678로 연락 주세요", "TEXT");
        var last = seen.getLast();
        assertEquals("card-1 카드 잃어버렸어, [번호]로 연락 주세요", last.question());
        assertTrue(last.history().stream().anyMatch(t -> t.message().equals("자동결제 관리에서 ap-1 [금액] 결제 확인")));
        assertTrue(last.history().stream().noneMatch(t -> t.message().contains("4821") || t.message().contains("넷플릭스")));
        var plan = (Plan) r.plan();
        assertEquals(List.of("card-1"), plan.suggestedCards); // AI가 ref로 받은 카드
        assertEquals("card-1 카드 잃어버렸어, [번호]로 연락 주세요", ((Map<?, ?>) plan.aiInput).get("question"));
        var history = store.session("a", "sa").messages();
        assertTrue(history.stream().anyMatch(m -> m.message().equals("4821 카드 잃어버렸어, 010-1234-5678로 연락 주세요")));
    }

    // 작업 31: 고객 C(트래블카드 1장뿐) — 잃어버리면 해외 결제 수단이 남지 않는다
    @Test void 고객_C가_트래블카드를_잃어버리면_해외_결제_수단_미확보로_남는다() {
        profiles.select("a", "sa", "C");
        var service = chat(new FakeAiClient());
        var p = (Plan) service.chat("a", "sa", "일본 가려는데, 트래블카드 잃어버렸어", "TEXT").plan();
        assertEquals(List.of("card-1"), p.suggestedCards);
        service.confirmCards("a", "sa", p.planId, List.of("card-1"));
        plans.execute("a", "sa", p.planId, 0, Map.of());
        int ask = p.blocks.stream().filter(b -> b.blockId.equals("ask-info")).findFirst().orElseThrow().index;
        var after = service.answer("a", "sa", p.planId, ask, profiles.today().plusDays(1).toString());
        assertTrue(after.goalStatus.stream().anyMatch(g -> g.type().equals("PAYMENT_ABROAD") && g.status().equals("UNSECURED")));
        assertTrue(after.blocks.stream().anyMatch(b -> b.blockId.equals("fx-exchange-apply") && b.priority.equals("REQUIRED")));
        assertTrue(after.blocks.stream().anyMatch(b -> b.blockId.equals("counselor-handoff")));
        assertTrue(after.considered.stream().anyMatch(c -> c.get("blockId").equals("simple-pay-unlink")));
        assertTrue(after.considered.stream().anyMatch(c -> c.get("blockId").equals("auto-payment-switch")));
        assertFalse(after.complete);
        assertFalse(after.remaining.isEmpty());
    }

    // 작업 32: 플랜이 있는 답은 한 문장(60자 이내)
    private String planReply(String reply) {
        var service = chat(c -> { var a = new FakeAiClient().answer(c); return new AiClient.Answer(reply, a.normalized(), a.menuIds(), a.steps(), a.handoff(), a.scope(), a.plan()); });
        return service.chat("a", "sa", "카드 한 장 잃어버렸어", "TEXT").reply();
    }

    @Test void 플랜_답은_60자_이하면_그대로() {
        String sixty = "가".repeat(59) + ".";
        assertEquals(sixty, planReply(sixty));
    }

    @Test void 플랜_답은_60자를_넘으면_첫_문장만() {
        assertEquals("많이 놀라셨겠어요.", planReply("많이 놀라셨겠어요. 먼저 카드 분실신고로 카드 사용을 바로 정지하는 것이 가장 급해요. 이 카드는 휴대폰 간편결제에도 등록되어 있어서 그것도 해제해요."));
        assertEquals("정말요?", planReply("정말요? " + "나".repeat(70)));
    }

    @Test void 첫_문장도_60자를_넘으면_59자와_말줄임() {
        var r = planReply("다".repeat(80) + ".");
        assertEquals(60, r.length());
        assertEquals("다".repeat(59) + "…", r);
    }

    @Test void 플랜이_없는_답은_줄이지_않는다() {
        String longer = "전체계좌조회에서 볼 수 있어요. " + "라".repeat(80);
        var r = chat(c -> new AiClient.Answer(longer, "", List.of("all-accounts"), List.of())).chat("a", "sa", "계좌 보여줘", "TEXT");
        assertEquals(longer, r.reply());
    }

    @Test void 플랜은_대화_기록에_남아_다시_그릴_수_있다() {
        chat(new FakeAiClient()).chat("a", "sa", "카드 한 장 잃어버렸어", "TEXT");
        var last = store.session("a", "sa").messages().getLast();
        assertNotNull(last.answer().plan());
    }
}
