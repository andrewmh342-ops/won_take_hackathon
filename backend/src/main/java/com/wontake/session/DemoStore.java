package com.wontake.session;

import com.wontake.api.ApiException;
import com.wontake.chat.ChatService;
import com.wontake.plan.PlanService;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 로컬 해커톤 시연 전용. 인증된 사용자 저장소가 아니며 재시작 시 초기화한다. */
@Component
public class DemoStore {
    public record Event(String type, String screenId, String elementId, String query, String timestamp) {}
    public record Message(String role, String message, String inputType, ChatService.Reply answer) {}
    public static class Session {
        public final String userId;
        public final List<Event> eventLog = new ArrayList<>();
        public final List<Message> conversation = new ArrayList<>();
        public volatile long touched = System.currentTimeMillis();
        public int notFoundStreak;
        /** 승인 전 작업 목록(planId → 목록, 오래된 것이 앞). 승인하거나 금액을 채워 새 목록으로 바뀌면 뺀다. 넣고 빼기는 PlanService만 한다. */
        public final Map<String, PlanService.Plan> plans = new LinkedHashMap<>();
        /** 승인한 planId. 같은 목록을 두 번 실행하지 않게 남긴다. */
        public final Set<String> confirmedPlans = new HashSet<>();
        public Session(String userId) { this.userId = userId; }
        public synchronized List<Event> events() { return List.copyOf(eventLog); }
        public synchronized List<Message> messages() { return List.copyOf(conversation); }
    }
    private final Map<String, Boolean> consents = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Set<String> never = ConcurrentHashMap.newKeySet();
    public static String id(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9_-]{1,100}")) throw ApiException.invalid("사용자·세션 ID를 확인해 주세요.");
        return value;
    }
    public Boolean consent(String userId) { return consents.get(id(userId)); }
    public void consent(String userId, boolean agreed) {
        consents.put(id(userId), agreed);
        if (!agreed) sessions.values().stream().filter(s -> s.userId.equals(userId)).forEach(s -> {
            synchronized(s) { s.eventLog.clear(); }
        });
    }
    public synchronized Session session(String userId, String sessionId) {
        id(userId); id(sessionId);
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> now - e.getValue().touched > 8 * 3600_000L);
        if (!sessions.containsKey(sessionId) && sessions.size() >= 1000)
            throw new ApiException(503, "DEMO_CAPACITY", "시연 세션이 많습니다. 서버를 재시작해 주세요.");
        var s = sessions.computeIfAbsent(sessionId, key -> new Session(userId));
        if (!s.userId.equals(userId)) throw new ApiException(403, "SESSION_OWNER_MISMATCH", "세션을 확인해 주세요.");
        s.touched = now;
        return s;
    }
    public Session existing(String sessionId) {
        var s = sessions.get(id(sessionId));
        if (s == null) throw new ApiException(404, "SESSION_NOT_FOUND", "대화를 먼저 시작해 주세요.");
        return s;
    }
    public boolean event(String userId, String sessionId, String type, String screenId, String elementId, String query) {
        if (!Set.of("SCREEN_ENTER", "CLICK", "SEARCH").contains(Objects.toString(type, ""))) throw ApiException.invalid("이벤트 유형을 확인해 주세요.");
        if (!Set.of("home", "menu", "transfer", "account", "security", "certificate", "exchange", "chat").contains(Objects.toString(screenId, ""))) throw ApiException.invalid("화면을 확인해 주세요.");
        if (elementId != null && elementId.length() > 120 || query != null && query.length() > 2000) throw ApiException.invalid("기록 길이를 확인해 주세요.");
        var s = session(userId, sessionId);
        synchronized(s) {
            if (!Boolean.TRUE.equals(consent(userId))) return false;
            s.eventLog.add(new Event(type, screenId, elementId, "SEARCH".equals(type) ? query : null, Instant.now().toString()));
            while(s.eventLog.size() > 200) s.eventLog.removeFirst();
            return true;
        }
    }
    public void close(String userId, String sessionId, String screenId, String action) {
        if (!Set.of("CLOSE", "NEVER").contains(Objects.toString(action, ""))) throw ApiException.invalid("닫기 동작을 확인해 주세요.");
        session(userId, sessionId);
        if (action.equals("NEVER")) never.add(userId);
    }
    public boolean never(String userId) { return never.contains(userId); }
}
