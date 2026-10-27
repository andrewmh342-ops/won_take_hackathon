package com.wontake.chat;

import com.wontake.ai.*;
import com.wontake.catalog.MenuCatalog;
import com.wontake.plan.*;
import com.wontake.session.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 두 브랜치의 플랜과 메뉴 이동 계약을 같은 대화 서비스에서 확인한다. */
class MergedPlanFlowTest {
    @Test void 업무_플랜과_이체_목록이_서로_다른_필드로_복원된다() {
        var store = new DemoStore();
        var ai = new FakeAiClient();
        var book = new FinanceBook();
        var transfer = new PlanService(book, ai, store, new Balances(book), "123456");
        var chat = new ChatService(store, new MenuCatalog(), ai, null, new UsageGuard(), transfer);
        var task = chat.chat("merge-a", "merge-s", "카드를 잃어버렸어요", "TEXT");
        assertNotNull(task.plan());
        assertNull(task.transferPlan());
        assertFalse(task.plan().blocks.isEmpty());
        var payment = chat.chat("merge-a", "merge-s", "엄마한테 30만원 보내줘", "TEXT");
        assertNull(payment.plan());
        assertEquals(300000L, payment.transferPlan().totalAmount());
        var history = store.session("merge-a", "merge-s").messages();
        assertNotNull(history.get(1).answer().plan());
        assertNotNull(history.get(3).answer().transferPlan());
        var menu = chat.chat("merge-a", "merge-s", "최근 이채내역 보여줘", "TEXT");
        assertEquals("ANSWER", menu.type());
        assertEquals("transfer-history", menu.menuId());
    }
}
