package com.wontake.session;

import com.wontake.api.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 대화 악용(무료 AI처럼 쓰기, 크레딧 소모)을 막는 사용량 제한. 숫자는 SPEC R2 "이용 범위와 사용량" 규칙이다.
 * 시연 서버는 로그인 없이 세션마다 새 가상 고객이므로, 세션 제한과 서버 전체 제한을 함께 둔다.
 */
@Component
public class UsageGuard {
    public static final int PER_MINUTE = 6;
    public static final int PER_SESSION = 40;
    public static final int SERVER_PER_MINUTE = 60;
    public static final int OUT_OF_SCOPE_LIMIT = 3;
    public static final long BLOCK_MILLIS = 600_000;
    public static final int SUMMARY_PER_SESSION = 5;
    public static final int PLAN_PER_MINUTE = 20;
    private static final long MINUTE = 60_000;

    private static class Usage {
        final Deque<Long> recent = new ArrayDeque<>(), planRecent = new ArrayDeque<>();
        int total, outOfScope, summaries;
        long blockedUntil = Long.MIN_VALUE;
    }
    private final Clock clock;
    private final Map<String, Usage> sessions = new ConcurrentHashMap<>();
    private final Deque<Long> server = new ArrayDeque<>();

    @Autowired public UsageGuard() { this(Clock.systemUTC()); }
    public UsageGuard(Clock clock) { this.clock = clock; }

    /** AI 대화를 1번 쓰기 전에 부른다. 한도를 넘으면 AI를 부르지 않도록 예외를 던진다. */
    public void chat(String sessionId) {
        long now = clock.millis();
        var u = usage(sessionId);
        synchronized (this) {
            synchronized (u) {
                prune(u.recent, now); prune(server, now);
                if (u.total >= PER_SESSION) throw new ApiException(429, "CHAT_SESSION_LIMIT", "이번 대화에서 질문할 수 있는 횟수를 모두 썼어요. 상담사에게 연결해 주세요.");
                if (u.recent.size() >= PER_MINUTE) throw new ApiException(429, "CHAT_RATE_LIMIT", "질문이 너무 빨라요. 잠시 뒤에 다시 물어봐 주세요.");
                if (server.size() >= SERVER_PER_MINUTE) throw new ApiException(503, "CHAT_BUSY", "지금 문의가 많아요. 잠시 뒤에 다시 물어봐 주세요.");
                u.recent.addLast(now); server.addLast(now); u.total++;
            }
        }
    }
    /** R5 플랜 블록 실행(AI 호출 없음)의 세션당 분당 상한. */
    public void plan(String sessionId) {
        long now = clock.millis();
        var u = usage(sessionId);
        synchronized (u) {
            prune(u.planRecent, now);
            if (u.planRecent.size() >= PLAN_PER_MINUTE) throw new ApiException(429, "PLAN_RATE_LIMIT", "너무 빨리 누르고 있어요. 잠시 뒤에 다시 눌러 주세요.");
            u.planRecent.addLast(now);
        }
    }
    public void outOfScope(String sessionId) {
        var u = usage(sessionId);
        synchronized (u) { if (++u.outOfScope >= OUT_OF_SCOPE_LIMIT) { u.blockedUntil = clock.millis() + BLOCK_MILLIS; u.outOfScope = 0; } }
    }
    public void inScope(String sessionId) {
        var u = usage(sessionId);
        synchronized (u) { u.outOfScope = 0; }
    }
    public boolean blocked(String sessionId) {
        var u = usage(sessionId);
        synchronized (u) { return clock.millis() < u.blockedUntil; }
    }
    /** 상담 요약 AI 호출 허용 여부. 넘으면 AI 없이 기록만 전달한다. */
    public boolean summary(String sessionId) {
        var u = usage(sessionId);
        synchronized (u) { return u.summaries++ < SUMMARY_PER_SESSION; }
    }

    private Usage usage(String sessionId) {
        if (sessions.size() > 5000) sessions.clear(); // 시연 서버 메모리 보호
        return sessions.computeIfAbsent(sessionId, k -> new Usage());
    }
    private static void prune(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() >= MINUTE) times.removeFirst();
    }
}
