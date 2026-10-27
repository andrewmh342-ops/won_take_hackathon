package com.wontake.plan;

import com.wontake.ai.AiClient;
import com.wontake.api.ApiException;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 고객이 가상 비밀번호로 승인하면, 저장된 목록을 서버 규칙으로 다시 검사하고 가상 생활통장 잔액만 줄인다. 승인할 때 AI는 부르지 않는다. */
class PlanConfirmTest {
    static final String PASSWORD = "123456";
    private final FinanceBook book = new FinanceBook();
    private final DemoStore store = new DemoStore();
    private final Balances balances = new Balances(book);
    private final AtomicInteger aiCalls = new AtomicInteger();
    private List<AiClient.TaskDraft> drafts = List.of();
    private final AiClient ai = new AiClient() {
        @Override public Answer answer(Context c) { aiCalls.incrementAndGet(); throw new AssertionError("승인은 채팅 AI를 부르지 않는다"); }
        @Override public PlanDraft plan(PlanContext c) { aiCalls.incrementAndGet(); return new PlanDraft(Answer.IN_SCOPE, drafts); }
        @Override public List<AmountDraft> fill(FillContext c) { aiCalls.incrementAndGet(); return List.of(); }
    };
    private final PlanService service = new PlanService(book, ai, store, balances, PASSWORD);

    private static AiClient.TaskDraft draft(String target, Long amount) { return new AiClient.TaskDraft(target, amount, ""); }
    private PlanService.Plan plan(String user, AiClient.TaskDraft... tasks) {
        drafts = List.of(tasks);
        return service.plan(store.session(user, "s-" + user), "요청");
    }
    private PlanService.Confirmation confirm(String user, String planId) { return service.confirm(user, "s-" + user, planId, PASSWORD); }
    private ApiException fail(String user, String planId, String password, int status, String code) {
        var e = assertThrows(ApiException.class, () -> service.confirm(user, "s-" + user, planId, password));
        assertEquals(status, e.status);
        assertEquals(code, e.code);
        return e;
    }

    @Test void 승인하면_생활통장_잔액이_보낸_만큼_정확히_준다() {
        var p = plan("a", draft("집주인", 650_000L), draft("엄마", 300_000L));
        var done = confirm("a", p.planId());
        assertEquals("CONFIRMED", done.status());
        assertEquals(950_000L, done.totalAmount());
        assertEquals(2_450_000L, done.balanceBefore());
        assertEquals(1_500_000L, done.balanceAfter());
        assertEquals(1_500_000L, balances.of("a"));
        // 다음 목록은 줄어든 잔액에서 계산한다. 다른 고객의 잔액은 따로다.
        var next = plan("a", draft("엄마", 100_000L));
        assertEquals(1_500_000L, next.balance());
        assertEquals(1_400_000L, next.expectedBalance());
        assertEquals(2_450_000L, balances.of("b"));
    }
    @Test void 잔액과_같은_금액은_승인한다() {
        var p = plan("a", draft("WON 차곡차곡 적금", 2_450_000L));
        assertEquals(0L, confirm("a", p.planId()).balanceAfter());
    }
    @Test void 승인할_때_AI를_부르지_않는다() {
        var p = plan("a", draft("엄마", 300_000L));
        assertEquals(1, aiCalls.get());
        confirm("a", p.planId());
        assertEquals(1, aiCalls.get());
    }
    @Test void 없는_planId는_찾지_못한다() {
        plan("a", draft("엄마", 300_000L));
        fail("a", "없는-목록", PASSWORD, 404, "PLAN_NOT_FOUND");
    }
    @Test void 다른_세션의_목록은_찾지_못한다() {
        var p = plan("a", draft("엄마", 300_000L));
        var e = assertThrows(ApiException.class, () -> service.confirm("a", "s-other", p.planId(), PASSWORD));
        assertEquals("PLAN_NOT_FOUND", e.code);
    }
    @Test void 틀린_비밀번호는_거절하고_잔액을_그대로_둔다() {
        var p = plan("a", draft("엄마", 300_000L));
        fail("a", p.planId(), "000000", 401, "PASSWORD_MISMATCH");
        fail("a", p.planId(), "", 401, "PASSWORD_MISMATCH");
        assertEquals(2_450_000L, balances.of("a"));
        // 틀려도 목록은 남아 있어 맞는 비밀번호로 다시 승인할 수 있다.
        assertEquals(2_150_000L, confirm("a", p.planId()).balanceAfter());
    }
    @Test void 다섯_번_틀리면_맞는_비밀번호도_막는다() {
        var p = plan("a", draft("엄마", 300_000L));
        for (int i = 1; i < PlanService.MAX_PASSWORD_FAILURES; i++) fail("a", p.planId(), "000000", 401, "PASSWORD_MISMATCH");
        fail("a", p.planId(), "000000", 423, "PASSWORD_LOCKED");
        fail("a", p.planId(), PASSWORD, 423, "PASSWORD_LOCKED");
        assertEquals(2_450_000L, balances.of("a"));
        // 잠금은 고객별이다. 새 세션을 열어도 풀리지 않고, 다른 고객은 그대로 승인한다.
        drafts = List.of(draft("엄마", 300_000L));
        var other = service.plan(store.session("a", "s-new"), "요청");
        assertEquals("PASSWORD_LOCKED", assertThrows(ApiException.class, () -> service.confirm("a", "s-new", other.planId(), PASSWORD)).code);
        assertEquals(2_150_000L, confirm("b", plan("b", draft("엄마", 300_000L)).planId()).balanceAfter());
    }
    @Test void 맞게_입력하면_틀린_횟수를_처음부터_센다() {
        var p = plan("a", draft("엄마", 300_000L));
        for (int i = 1; i < PlanService.MAX_PASSWORD_FAILURES; i++) fail("a", p.planId(), "000000", 401, "PASSWORD_MISMATCH");
        confirm("a", p.planId());
        var next = plan("a", draft("엄마", 100_000L));
        for (int i = 1; i < PlanService.MAX_PASSWORD_FAILURES; i++) fail("a", next.planId(), "000000", 401, "PASSWORD_MISMATCH");
    }
    @Test void 빈_금액이_남으면_승인하지_않는다() {
        var p = plan("a", draft("집주인", null), draft("엄마", 300_000L));
        fail("a", p.planId(), PASSWORD, 409, "NEEDS_AMOUNT");
        assertEquals(2_450_000L, balances.of("a"));
    }
    @Test void 주소록에_없는_곳은_승인하지_않는다() {
        var p = plan("a", draft("친구 A", 10_000L));
        fail("a", p.planId(), PASSWORD, 409, "NEEDS_ACCOUNT");
    }
    @Test void 잔액보다_많으면_승인하지_않는다() {
        var p = plan("a", draft("WON 차곡차곡 적금", 2_450_001L));
        fail("a", p.planId(), PASSWORD, 409, "INSUFFICIENT_BALANCE");
        assertEquals(2_450_000L, balances.of("a"));
    }
    @Test void 두_번_승인하면_한_번만_실행한다() {
        var p = plan("a", draft("엄마", 300_000L));
        confirm("a", p.planId());
        fail("a", p.planId(), PASSWORD, 409, "PLAN_ALREADY_CONFIRMED");
        assertEquals(2_150_000L, balances.of("a"));
    }
    /** 만들 때는 승인할 수 있던 목록도, 앞선 승인으로 잔액이 줄었으면 승인할 때 다시 막는다. */
    @Test void 승인할_때_지금_잔액으로_다시_검사한다() {
        var first = plan("a", draft("WON 차곡차곡 적금", 1_300_000L));
        var second = plan("a", draft("WON 차곡차곡 적금", 1_300_000L));
        assertTrue(second.canConfirm());
        confirm("a", first.planId());
        fail("a", second.planId(), PASSWORD, 409, "INSUFFICIENT_BALANCE");
        assertEquals(1_150_000L, balances.of("a"));
    }
    @Test void 주의_작업은_경고만_하고_승인한다() {
        var p = plan("a", draft("엄마", 1_000_000L));
        assertEquals("HIGH", p.tasks().getFirst().risk());
        assertEquals(1_450_000L, confirm("a", p.planId()).balanceAfter());
    }
    @Test void 작업이_없는_답은_저장하지_않는다() {
        var p = plan("a");
        assertEquals("NO_TASK", p.type());
        assertNull(p.planId());
    }
    @Test void 비밀번호_설정이_비면_서버를_띄우지_않는다() {
        assertThrows(IllegalStateException.class, () -> new PlanService(book, ai, store, balances, " "));
    }
}
