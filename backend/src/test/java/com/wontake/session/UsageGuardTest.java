package com.wontake.session;

import com.wontake.api.ApiException;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class UsageGuardTest {
    static class TestClock extends Clock {
        long millis = 0;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    }
    private final TestClock clock = new TestClock();
    private final UsageGuard guard = new UsageGuard(clock);

    private String code(Runnable r) { return assertThrows(ApiException.class, r::run).code; }

    @Test void 분당_6번째까지_허용하고_7번째는_거절한다() {
        for (int i = 0; i < 6; i++) guard.chat("s");
        assertEquals("CHAT_RATE_LIMIT", code(() -> guard.chat("s")));
    }

    @Test void 첫_질문_60초_뒤에는_다시_허용한다() {
        for (int i = 0; i < 6; i++) guard.chat("s");
        clock.millis = 59_999;
        assertEquals("CHAT_RATE_LIMIT", code(() -> guard.chat("s")));
        clock.millis = 60_000;
        guard.chat("s");
    }

    @Test void 세션당_40번째까지_허용하고_41번째는_거절한다() {
        for (int i = 0; i < 40; i++) { guard.chat("s"); clock.millis += 10_001; }
        assertEquals("CHAT_SESSION_LIMIT", code(() -> guard.chat("s")));
        guard.chat("other");
    }

    @Test void 범위_밖_연속_3번이면_10분간_AI를_부르지_않는다() {
        guard.outOfScope("s"); guard.outOfScope("s");
        assertFalse(guard.blocked("s"));
        guard.outOfScope("s");
        assertTrue(guard.blocked("s"));
        clock.millis = 599_999;
        assertTrue(guard.blocked("s"));
        clock.millis = 600_000;
        assertFalse(guard.blocked("s"));
    }

    @Test void 범위_안_질문이_오면_범위_밖_연속_횟수를_되돌린다() {
        guard.outOfScope("s"); guard.outOfScope("s");
        guard.inScope("s");
        guard.outOfScope("s");
        assertFalse(guard.blocked("s"));
    }

    @Test void 상담_요약_AI는_세션당_5번까지만_부른다() {
        for (int i = 0; i < 5; i++) assertTrue(guard.summary("s"));
        assertFalse(guard.summary("s"));
    }

    @Test void 서버_전체_분당_60번을_넘으면_거절한다() {
        for (int i = 0; i < 60; i++) guard.chat("s" + i);
        assertEquals("CHAT_BUSY", code(() -> guard.chat("s-new")));
    }
}
