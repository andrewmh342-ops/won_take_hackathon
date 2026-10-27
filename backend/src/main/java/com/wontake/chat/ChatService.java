package com.wontake.chat;

import com.wontake.ai.AiClient;
import com.wontake.behavior.BehaviorService;
import org.springframework.beans.factory.annotation.Autowired;
import com.wontake.api.ApiException;
import com.wontake.assist.BlockCatalog;
import com.wontake.assist.Plan;
import com.wontake.assist.PlanService;
import com.wontake.assist.ProfileStore;
import com.wontake.catalog.MenuCatalog;
import com.wontake.session.DemoStore;
import com.wontake.session.UsageGuard;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChatService {
    public record Reply(String type, String normalized, String reply, String menuId, List<String> menuIds,
                        List<String> steps, int notFoundStreak, String mode, boolean handoffSuggested, Plan plan) {}
    public static final int MAX_QUESTION = 500;
    /** 범위 밖 질문에는 AI가 쓴 문장을 보여 주지 않고 이 고정 안내만 보낸다. */
    public static final String OUT_OF_SCOPE_REPLY = "WON테이크는 이 앱을 쓰다 막힌 점만 도와드려요. 이체, 계좌 조회, 인증, 증명서, 환전 같은 메뉴를 찾거나 쓰다가 어려운 점을 말씀해 주세요.";
    public static final String BLOCKED_REPLY = "앱 이용과 관련 없는 질문이 이어져 잠시 AI 안내를 멈췄어요. 10분 뒤에 다시 물어봐 주시거나, 상담사에게 연결해 주세요.";
    private final DemoStore store;
    private final MenuCatalog catalog;
    private final AiClient ai;
    private final BehaviorService behavior;
    private final UsageGuard guard;
    private final PlanService plans;
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai) { this(store,catalog,ai,null,new UsageGuard()); }
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior) { this(store,catalog,ai,behavior,new UsageGuard()); }
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior,UsageGuard guard) {
        this(store,catalog,ai,behavior,guard,new PlanService(store,new BlockCatalog(),new ProfileStore(store),guard));
    }
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
            var turns = history(s.messages());
            var allEvents = Boolean.TRUE.equals(store.consent(userId)) ? s.events() : List.<DemoStore.Event>of();
            var events = allEvents.subList(Math.max(0, allEvents.size()-20), allEvents.size());
            var help=behavior==null?null:behavior.context(userId,sessionId,contextId);
            AiClient.Answer result;
            // R5: 가명화된 고객 상태와 블록 사전을 같은 호출에 넣는다(추가 호출 없음).
            var context = new AiClient.Context(question.trim(), inputType, turns, events, catalog.all(), help,
                plans.shape(userId, sessionId), plans.blocksForAi());
            try { result = ai.answer(context); }
            catch (ApiException e) {
                if (!"AI_REFUSED".equals(e.code)) throw e;
                result = new AiClient.Answer("", "", List.of(), List.of(), false, AiClient.Answer.OUT_OF_SCOPE);
            }
            if (result != null && result.outOfScope()) {
                guard.outOfScope(sessionId);
                return record(s, question, inputType, outOfScope(s, OUT_OF_SCOPE_REPLY));
            }
            validate(result);
            guard.inScope(sessionId);
            var ids = List.copyOf(result.menuIds());
            Plan plan = result.hasPlan() ? plans.build(userId, sessionId, result.plan()) : null;
            if (plan != null) {
                // 플랜이 있으면 찾지 못함이 아니다. 블록으로 못 하는 일이 있으면 상담 버튼을 강조한다.
                s.notFoundStreak = 0;
                var reply = new Reply("PLAN", "VOICE".equals(inputType) ? result.normalized() : "", result.reply(), ids.isEmpty() ? "" : ids.getFirst(), ids,
                    List.of(), 0, ai.mode(), result.handoff() || !plan.unhandled.isEmpty(), plan);
                return record(s, question, inputType, reply);
            }
            // 상담 버튼은 항상 쓸 수 있다. 범위 안 질문에서 찾지 못함 연속 2번이거나 AI가 권하면 버튼을 강조하도록 알린다(SPEC R3).
            s.notFoundStreak = ids.isEmpty() ? s.notFoundStreak + 1 : 0;
            var reply = new Reply(ids.isEmpty() ? "NOT_FOUND" : "ANSWER", "VOICE".equals(inputType) ? result.normalized() : "",
                result.reply(), ids.isEmpty() ? "" : ids.getFirst(), ids, List.copyOf(result.steps()), s.notFoundStreak, ai.mode(),
                result.handoff() || s.notFoundStreak >= 2, null);
            return record(s, question, inputType, reply);
        }
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
        if (a == null || a.reply() == null || a.reply().isBlank() || a.reply().length()>500
            || a.normalized()==null || a.normalized().length()>2000 || a.menuIds()==null || a.menuIds().size()>2
            || a.menuIds().stream().anyMatch(id -> id==null || !catalog.contains(id))
            || a.menuIds().stream().distinct().count()!=a.menuIds().size()
            || a.steps()==null || a.steps().size()>3 || a.steps().stream().anyMatch(s -> s==null || s.isBlank() || s.length()>200))
            throw new ApiException(502, "AI_INVALID_RESPONSE", "AI 안내 형식을 확인하지 못했어요. 다시 시도해 주세요.");
    }
}
