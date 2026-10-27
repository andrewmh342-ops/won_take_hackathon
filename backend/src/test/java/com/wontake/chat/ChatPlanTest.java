package com.wontake.chat;

import com.wontake.ai.AiClient;
import com.wontake.catalog.MenuCatalog;
import com.wontake.plan.FinanceBook;
import com.wontake.plan.PlanService;
import com.wontake.session.DemoStore;
import com.wontake.session.UsageGuard;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 채팅에서 받는 사람이 있는 이체 요청을 하면, 채팅 AI가 알아채고 서버가 plan을 이어 불러 작업 카드를 대화로 돌려준다. 거래는 하지 않는다. */
class ChatPlanTest {
    private final MenuCatalog catalog = new MenuCatalog();
    private final DemoStore store = new DemoStore();
    private final UsageGuard guard = new UsageGuard();
    private final AtomicInteger answers = new AtomicInteger(), plans = new AtomicInteger();

    private AiClient ai(String scope, Long rentAmount) {
        return ai(scope, List.of(new AiClient.TaskDraft("집주인", rentAmount, "월세"), new AiClient.TaskDraft("엄마", 300_000L, "")));
    }
    /** 채팅 AI는 이체 묶음으로 분류할 때도 메뉴 안내 답을 함께 쓴다. plan이 작업을 못 찾으면 그 답을 대신 보여 준다. */
    private AiClient ai(String scope, List<AiClient.TaskDraft> tasks) {
        return new AiClient() {
            @Override public Answer answer(Context c) { answers.incrementAndGet(); return new Answer("계좌이체 메뉴에서 보낼 수 있어요.", "", List.of("instant-transfer"), List.of(), false, scope); }
            @Override public PlanDraft plan(PlanContext c) { plans.incrementAndGet(); return new PlanDraft(Answer.IN_SCOPE, tasks); }
        };
    }
    private ChatService service(AiClient ai) { return new ChatService(store, catalog, ai, null, guard, new PlanService(new FinanceBook(), ai)); }

    @Test void 이체_묶음이면_작업_카드를_대화로_돌려준다() {
        var reply = service(ai(AiClient.Answer.TRANSFER_PLAN, 650_000L)).chat("a", "sa", "월세 65만원 내고 엄마한테 30만원 보내줘", "TEXT");
        assertEquals("PLAN", reply.type());
        assertEquals(ChatService.PLAN_REPLY, reply.reply());
        assertEquals(2, reply.plan().tasks().size());
        assertEquals(1_500_000L, reply.plan().expectedBalance());
        assertTrue(reply.plan().canConfirm());
        assertTrue(reply.menuIds().isEmpty());
        assertFalse(reply.handoffSuggested());
        assertEquals(1, answers.get());
        assertEquals(1, plans.get());
    }
    @Test void 금액이_빠지면_다시_묻는_문장을_답으로_쓴다() {
        var reply = service(ai(AiClient.Answer.TRANSFER_PLAN, (Long) null)).chat("a", "sa", "월세 내고 엄마한테 30만원 보내줘", "TEXT");
        assertEquals(PlanService.NEEDS_AMOUNT_MESSAGE, reply.reply());
        assertEquals("NEEDS_AMOUNT", reply.plan().tasks().getFirst().status());
        assertFalse(reply.plan().canConfirm());
    }
    /** 채팅 AI와 plan AI를 한 번씩 부르지만, 고객 질문은 하나라서 세션 사용량은 한 번만 센다. */
    @Test void 이체_묶음도_세션_사용량은_질문_하나로_센다() {
        var service = service(ai(AiClient.Answer.TRANSFER_PLAN, 650_000L));
        for (int i = 0; i < UsageGuard.PER_MINUTE; i++) assertEquals("PLAN", service.chat("a", "sa", "엄마한테 30만원 보내줘", "TEXT").type());
    }
    @Test void 받는_사람을_못_찾으면_메뉴_안내로_돌아간다() {
        var reply = service(ai(AiClient.Answer.TRANSFER_PLAN, List.of())).chat("a", "sa", "5만원 이체해 줘", "TEXT");
        assertEquals("ANSWER", reply.type());
        assertEquals("계좌이체 메뉴에서 보낼 수 있어요.", reply.reply());
        assertEquals("instant-transfer", reply.menuId());
        assertNull(reply.plan());
        assertEquals(1, answers.get());
        assertEquals(1, plans.get());
    }
    /** Haiku가 이체 묶음으로 분류하며 답 문장을 비워 보낸 적이 있다(2026-10-27 실측). 그래도 오류 대신 카드나 다시 묻는 문장을 보여 준다. */
    private AiClient emptyReplyAi(List<AiClient.TaskDraft> tasks) {
        return new AiClient() {
            @Override public Answer answer(Context c) { return new Answer("", "", List.of(), List.of(), false, Answer.TRANSFER_PLAN); }
            @Override public PlanDraft plan(PlanContext c) { return new PlanDraft(Answer.IN_SCOPE, tasks); }
        };
    }
    @Test void 채팅_답이_비어도_작업을_찾으면_카드를_보여_준다() {
        var reply = service(emptyReplyAi(List.of(new AiClient.TaskDraft("엄마", 300_000L, "")))).chat("a", "sa", "엄마한테 30만원 보내줘", "TEXT");
        assertEquals("PLAN", reply.type());
        assertEquals(1, reply.plan().tasks().size());
    }
    @Test void 채팅_답도_비고_작업도_없으면_다시_묻는다() {
        var reply = service(emptyReplyAi(List.of())).chat("a", "sa", "5만원 이체해 줘", "TEXT");
        assertEquals("PLAN", reply.type());
        assertEquals(PlanService.NO_TASK_MESSAGE, reply.reply());
        assertTrue(reply.plan().tasks().isEmpty());
    }
    @Test void 일반_질문은_plan을_부르지_않는다() {
        var reply = service(ai(AiClient.Answer.IN_SCOPE, 650_000L)).chat("a", "sa", "이체는 어디서 해요", "TEXT");
        assertNull(reply.plan());
        assertEquals(0, plans.get());
    }
    @Test void 작업_카드는_대화_기록에도_남는다() {
        service(ai(AiClient.Answer.TRANSFER_PLAN, 650_000L)).chat("a", "sa", "월세 65만원 내고 엄마한테 30만원 보내줘", "TEXT");
        var last = store.session("a", "sa").messages().getLast();
        assertEquals(ChatService.PLAN_REPLY, last.message());
        assertEquals(2, last.answer().plan().tasks().size());
    }
}
