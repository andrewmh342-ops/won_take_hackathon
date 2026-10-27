package com.wontake.plan;

import com.wontake.ai.AiClient;
import com.wontake.api.ApiException;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 한 문장을 작업 카드로 나눈다. AI는 받는 곳과 금액만 고르고, 종류와 위험도와 잔액 계산은 서버 규칙이 한다. 거래는 하지 않는다. */
class PlanServiceTest {
    private final FinanceBook book = new FinanceBook();
    private final DemoStore store = new DemoStore();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<AiClient.PlanContext> seen = new ArrayList<>();

    private AiClient ai(String scope, AiClient.TaskDraft... drafts) {
        return new AiClient() {
            @Override public Answer answer(Context c) { throw new AssertionError("작업 목록은 채팅 답변을 부르지 않는다"); }
            @Override public PlanDraft plan(PlanContext c) { calls.incrementAndGet(); seen.add(c); return new PlanDraft(scope, List.of(drafts)); }
        };
    }
    private PlanService service(AiClient ai) { return new PlanService(book, ai, store, new Balances(book), "123456"); }
    private static AiClient.TaskDraft draft(String target, Long amount) { return new AiClient.TaskDraft(target, amount, ""); }
    private PlanService.Plan plan(AiClient.TaskDraft... drafts) { return service(ai("IN_SCOPE", drafts)).plan(store.session("a", "sa"), "요청"); }
    private static void invalid(Executable e) { assertEquals("AI_INVALID_RESPONSE", assertThrows(ApiException.class, e).code); }

    @Test void 복합_요청은_말한_순서대로_카드를_만들고_예상_잔액을_계산한다() {
        var p = plan(draft("집주인", 650_000L), draft("WON 차곡차곡 적금", 500_000L), draft("엄마", 300_000L));
        assertEquals("PLAN", p.type());
        assertEquals(List.of("집주인", "WON 차곡차곡 적금", "엄마"), p.tasks().stream().map(PlanService.Task::target).toList());
        assertEquals(List.of(1, 2, 3), p.tasks().stream().map(PlanService.Task::order).toList());
        assertEquals(List.of(650_000L, 500_000L, 300_000L), p.tasks().stream().map(PlanService.Task::amount).toList());
        assertEquals(2_450_000L, p.balance());
        assertEquals(1_450_000L, p.totalAmount());
        assertEquals(1_000_000L, p.expectedBalance());
        assertTrue(p.canConfirm());
        assertTrue(p.blockers().isEmpty());
        assertEquals("", p.message());
    }

    @Test void 금액을_말하지_않으면_채우지_않고_다시_묻는다() {
        var p = plan(draft("집주인", null), draft("엄마", 300_000L));
        var t = p.tasks().getFirst();
        assertEquals("NEEDS_AMOUNT", t.status());
        assertEquals(0L, t.amount()); // 최근 이체 금액 같은 값으로 채우지 않는다
        assertEquals(300_000L, p.totalAmount());
        assertFalse(p.canConfirm());
        assertEquals(List.of("NEEDS_AMOUNT"), p.blockers());
        assertEquals(PlanService.NEEDS_AMOUNT_MESSAGE, p.message());
    }

    @Test void 모르는_사람에_금액도_없으면_계좌와_금액을_모두_묻는다() {
        var p = plan(draft("A", null));
        assertEquals("NEEDS_ACCOUNT", p.tasks().getFirst().status());
        assertEquals(List.of("NEEDS_ACCOUNT", "NEEDS_AMOUNT"), p.blockers());
        assertFalse(p.canConfirm());
    }

    @Test void 내_계좌로_옮기기는_중간_낮음() {
        var t = plan(draft("WON 차곡차곡 적금", 500_000L)).tasks().getFirst();
        assertEquals("OWN_TRANSFER", t.type());
        assertEquals("DEMO-ACCOUNT-02", t.account()); // 내 계좌는 화면처럼 번호를 그대로 보여 준다
        assertEquals("MEDIUM_LOW", t.risk());
        assertEquals("READY", t.status());
    }

    @Test void 자주_보낸_사람에게_평소_금액은_중간() {
        var t = plan(draft("엄마", 300_000L)).tasks().getFirst();
        assertEquals("TRANSFER", t.type());
        assertEquals("MEDIUM", t.risk());
        assertEquals(List.of("FREQUENT_RECIPIENT"), t.riskReasons());
        assertTrue(t.account().endsWith("0103"));
        assertFalse(t.account().contains("1002")); // 계좌번호는 끝 4자리만 보여 준다
    }

    @Test void 평소_최대_금액의_3배까지는_중간_넘으면_높음() {
        assertEquals("MEDIUM", plan(draft("엄마", 900_000L)).tasks().getFirst().risk());
        var t = plan(draft("엄마", 900_001L)).tasks().getFirst();
        assertEquals("HIGH", t.risk());
        assertEquals(List.of("ABNORMAL_AMOUNT"), t.riskReasons());
    }

    @Test void 주소록에_없는_사람은_높음이고_계좌를_받기_전에는_승인할_수_없다() {
        var p = plan(draft("A", 50_000L));
        var t = p.tasks().getFirst();
        assertEquals("NEEDS_ACCOUNT", t.status());
        assertEquals("HIGH", t.risk());
        assertEquals(List.of("NEW_RECIPIENT"), t.riskReasons());
        assertEquals("", t.account());
        assertFalse(p.canConfirm());
        assertEquals(List.of("NEEDS_ACCOUNT"), p.blockers());
    }

    @Test void 합계가_잔액과_같으면_승인_가능_1원_넘으면_불가() {
        var same = plan(draft("WON 차곡차곡 적금", book.balance()));
        assertEquals(0L, same.expectedBalance());
        assertTrue(same.canConfirm());
        var over = plan(draft("WON 차곡차곡 적금", book.balance() + 1));
        assertEquals(-1L, over.expectedBalance());
        assertFalse(over.canConfirm());
        assertEquals(List.of("INSUFFICIENT_BALANCE"), over.blockers());
        assertEquals(1, over.tasks().size()); // 막지 않고 목록은 보여 준다
    }

    @Test void 작업은_5개까지이고_6개면_AI_응답_오류() {
        var five = new AiClient.TaskDraft[5];
        Arrays.fill(five, draft("엄마", 10_000L));
        assertEquals(5, plan(five).tasks().size());
        var six = new AiClient.TaskDraft[6];
        Arrays.fill(six, draft("엄마", 10_000L));
        invalid(() -> plan(six));
    }

    @Test void AI가_0원_음수_빈_대상을_주면_AI_응답_오류() {
        invalid(() -> plan(draft("엄마", 0L)));
        invalid(() -> plan(draft("엄마", -1L)));
        invalid(() -> plan(draft(" ", 1_000L)));
        invalid(() -> plan(draft(null, 1_000L)));
    }

    @Test void 합계가_long을_넘으면_AI_응답_오류() {
        invalid(() -> plan(draft("엄마", Long.MAX_VALUE), draft("엄마", 1L)));
    }

    @Test void AI가_작업을_찾지_못하면_고정_안내만() {
        var p = plan();
        assertEquals("NO_TASK", p.type());
        assertEquals(PlanService.NO_TASK_MESSAGE, p.message());
        assertTrue(p.tasks().isEmpty());
        assertFalse(p.canConfirm());
    }

    @Test void 범위_밖이면_AI가_준_작업을_버리고_고정_안내만() {
        var p = service(ai("OUT_OF_SCOPE", draft("엄마", 1_000L))).plan(store.session("a", "sa"), "오늘 날씨 어때");
        assertEquals("OUT_OF_SCOPE", p.type());
        assertEquals(PlanService.OUT_OF_SCOPE_MESSAGE, p.message());
        assertTrue(p.tasks().isEmpty());
        assertFalse(p.canConfirm());
    }



    @Test void 작업_목록을_만들어도_잔액은_그대로다() {
        plan(draft("엄마", 300_000L));
        assertEquals(2_450_000L, plan(draft("엄마", 300_000L)).balance());
        assertEquals(2_450_000L, book.balance());
    }

    @Test void AI에는_계좌번호와_이체_이력을_보내지_않는다() throws Exception {
        plan(draft("엄마", 1_000L));
        var sent = new JsonMapper().writeValueAsString(seen.getLast());
        assertTrue(sent.contains("엄마"));
        for (var a : book.accounts()) {
            assertFalse(sent.contains(a.number()));
            assertFalse(sent.contains(String.valueOf(a.history().getLast())));
        }
    }

}
