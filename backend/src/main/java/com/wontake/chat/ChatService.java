package com.wontake.chat;

import com.wontake.ai.AiClient;
import com.wontake.behavior.BehaviorService;
import org.springframework.beans.factory.annotation.Autowired;
import com.wontake.api.ApiException;
import com.wontake.catalog.MenuCatalog;
import com.wontake.plan.PlanService;
import com.wontake.session.DemoStore;
import com.wontake.session.UsageGuard;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChatService {
    /** type이 PLAN이면 plan에 작업 카드 목록이 있고, 그 밖에는 plan이 null이다. */
    public record Reply(String type, String normalized, String reply, String menuId, List<String> menuIds,
                        List<String> steps, int notFoundStreak, String mode, boolean handoffSuggested, PlanService.Plan plan) {}
    public static final int MAX_QUESTION = 500;
    /** 범위 밖 질문에는 AI가 쓴 문장을 보여 주지 않고 이 고정 안내만 보낸다. */
    public static final String OUT_OF_SCOPE_REPLY = "WON테이크는 이 앱을 쓰다 막힌 점만 도와드려요. 이체, 계좌 조회, 인증, 증명서, 환전 같은 메뉴를 찾거나 쓰다가 어려운 점을 말씀해 주세요.";
    /** 작업 목록을 만들었을 때 대화에 남기는 문장. 금액을 다시 물어야 하면 PlanService의 안내 문장을 대신 쓴다. */
    public static final String PLAN_REPLY = "보내실 내용을 정리했어요. 아래 목록을 확인해 주세요. 실제 이체는 하지 않아요.";
    public static final String BLOCKED_REPLY = "앱 이용과 관련 없는 질문이 이어져 잠시 AI 안내를 멈췄어요. 10분 뒤에 다시 물어봐 주시거나, 상담사에게 연결해 주세요.";
    private final DemoStore store;
    private final MenuCatalog catalog;
    private final AiClient ai;
    private final BehaviorService behavior;
    private final UsageGuard guard;
    private final PlanService plans;
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai) { this(store,catalog,ai,null,new UsageGuard()); }
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior) { this(store,catalog,ai,behavior,new UsageGuard()); }
    /** plan 없이 만든 채팅(기존 테스트용)은 이체 묶음 요청을 받으면 숨기지 않고 오류를 낸다. */
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior,UsageGuard guard) { this(store,catalog,ai,behavior,guard,null); }
    @Autowired public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior,UsageGuard guard,PlanService plans) {
        this.store=store; this.catalog=catalog; this.ai=ai; this.behavior=behavior; this.guard=guard; this.plans=plans;
    }
    public Reply chat(String userId, String sessionId, String question, String inputType) {
        return chat(userId,sessionId,question,inputType,null);
    }
    public Reply chat(String userId, String sessionId, String question, String inputType,String contextId) {
        if (question == null || question.isBlank() || question.length() > MAX_QUESTION) throw ApiException.invalid("질문을 1~"+MAX_QUESTION+"자로 입력해 주세요.");
        if (!Set.of("TEXT", "VOICE", "SUGGESTION").contains(Objects.toString(inputType, ""))) throw ApiException.invalid("입력 방식을 확인해 주세요.");
        var s = store.session(userId, sessionId);
        synchronized(s) {
            // 범위 밖 질문이 이어진 세션은 AI를 부르지 않는다(크레딧 보호).
            if (guard.blocked(sessionId)) return record(s, question, inputType, outOfScope(s, BLOCKED_REPLY));
            guard.chat(sessionId);
            // 금액이 빈 작업 카드가 있으면 이번 말에서 그 금액부터 읽는다. 채우면 채팅 AI를 부르지 않는다.
            // 금액을 말하지 않았으면 보통 대화로 넘어가므로, 서버 전체 호출 상한(app.ai.max-calls)을 최대 3번(fill, answer, plan) 쓴다.
            // 채운 차례는 이체 묶음 차례와 같이 범위 안 연속 횟수(guard.inScope)를 건드리지 않는다.
            if (plans != null) {
                var filled = plans.fill(s, question);
                if (filled.isPresent()) return record(s, question, inputType, planReply(s, filled.get()));
            }
            var turns = history(s.messages());
            var allEvents = Boolean.TRUE.equals(store.consent(userId)) ? s.events() : List.<DemoStore.Event>of();
            var events = allEvents.subList(Math.max(0, allEvents.size()-20), allEvents.size());
            var help=behavior==null?null:behavior.context(userId,sessionId,contextId);
            AiClient.Answer result;
            try { result = ai.answer(new AiClient.Context(question.trim(), inputType, turns, events, catalog.all(),help)); }
            catch (ApiException e) {
                if (!"AI_REFUSED".equals(e.code)) throw e;
                result = new AiClient.Answer("", "", List.of(), List.of(), false, AiClient.Answer.OUT_OF_SCOPE);
            }
            if (result != null && result.outOfScope()) {
                guard.outOfScope(sessionId);
                return record(s, question, inputType, outOfScope(s, OUT_OF_SCOPE_REPLY));
            }
            if (result != null && result.transferPlan()) {
                // 같은 질문을 plan에 넘긴다. 세션 사용량(UsageGuard)은 위에서 질문 하나로 이미 셌다.
                // 서버 전체 호출 상한(app.ai.max-calls)은 실제 호출 수라 이체 묶음 하나에 2번 쓴다.
                if (plans == null) throw new IllegalStateException("PlanService 없이 만든 ChatService");
                var plan = plans.plan(s, question.trim());
                // 받는 사람을 못 찾았으면(예: "5만원 이체해 줘") 채팅 AI가 함께 쓴 메뉴 안내를 보여 준다. Haiku가 분류를 틀려도 SPEC대로 메뉴 안내가 나오고, AI를 다시 부르지 않는다.
                // 단, Haiku가 이체 묶음일 때 답 문장을 비워 보낸 적이 있어(2026-10-27 실측), 안내가 형식에 맞지 않으면 plan의 다시 묻는 문장을 쓴다.
                boolean fallback = !plan.type().equals("PLAN") && valid(result);
                if (!fallback) return record(s, question, inputType, planReply(s, plan));
            }
            validate(result);
            guard.inScope(sessionId);
            var ids = List.copyOf(result.menuIds());
            // 상담 버튼은 항상 쓸 수 있다. 범위 안 질문에서 찾지 못함 연속 2번이거나 AI가 권하면 버튼을 강조하도록 알린다(SPEC R3).
            s.notFoundStreak = ids.isEmpty() ? s.notFoundStreak + 1 : 0;
            var reply = new Reply(ids.isEmpty() ? "NOT_FOUND" : "ANSWER", "VOICE".equals(inputType) ? result.normalized() : "",
                result.reply(), ids.isEmpty() ? "" : ids.getFirst(), ids, List.copyOf(result.steps()), s.notFoundStreak, ai.mode(),
                result.handoff() || s.notFoundStreak >= 2, null);
            return record(s, question, inputType, reply);
        }
    }
    private Reply planReply(DemoStore.Session s, PlanService.Plan plan) {
        return new Reply("PLAN", "", plan.message().isEmpty() ? PLAN_REPLY : plan.message(), "", List.of(), List.of(), s.notFoundStreak, ai.mode(), false, plan);
    }
    private Reply outOfScope(DemoStore.Session s, String text) {
        return new Reply("OUT_OF_SCOPE", "", text, "", List.of(), List.of(), s.notFoundStreak, ai.mode(), false, null);
    }
    private static Reply record(DemoStore.Session s, String question, String inputType, Reply reply) {
        s.conversation.add(new DemoStore.Message("USER", question.trim(), inputType, null));
        s.conversation.add(new DemoStore.Message("ASSISTANT", reply.reply(), "", reply));
        while(s.conversation.size()>20) s.conversation.removeFirst();
        return reply;
    }
    /** 범위 밖 문답은 AI의 이전 대화에서 뺀다. 우회 지시가 맥락에 쌓이지 않게 하고 입력 토큰도 줄인다. 상담사 원문에는 남는다. */
    public static List<AiClient.Turn> history(List<DemoStore.Message> messages) {
        var turns = new ArrayList<AiClient.Turn>();
        for (int i = 0; i < messages.size(); i++) {
            var m = messages.get(i);
            var next = i + 1 < messages.size() ? messages.get(i + 1) : null;
            if (m.role().equals("USER") && next != null && next.answer() != null && "OUT_OF_SCOPE".equals(next.answer().type())) { i++; continue; }
            if (m.answer() != null && "OUT_OF_SCOPE".equals(m.answer().type())) continue;
            turns.add(new AiClient.Turn(m.role(), m.message()));
        }
        return turns;
    }
    private void validate(AiClient.Answer a) {
        if (!valid(a)) throw new ApiException(502, "AI_INVALID_RESPONSE", "AI 안내 형식을 확인하지 못했어요. 다시 시도해 주세요.");
    }
    private boolean valid(AiClient.Answer a) {
        return !(a == null || a.reply() == null || a.reply().isBlank() || a.reply().length()>500
            || a.normalized()==null || a.normalized().length()>2000 || a.menuIds()==null || a.menuIds().size()>2
            || a.menuIds().stream().anyMatch(id -> id==null || !catalog.contains(id))
            || a.menuIds().stream().distinct().count()!=a.menuIds().size()
            || a.steps()==null || a.steps().size()>3 || a.steps().stream().anyMatch(s -> s==null || s.isBlank() || s.length()>200));
    }
}
