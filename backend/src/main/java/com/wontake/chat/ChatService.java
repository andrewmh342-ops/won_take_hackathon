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
    /** 업무 플랜(plan)과 이체 승인 목록(transferPlan)은 계약이 달라 서로 다른 필드로 보낸다. */
    public record Reply(String type, String normalized, String reply, String menuId, List<String> menuIds,
                        List<String> steps, int notFoundStreak, String mode, boolean handoffSuggested, Plan plan,
                        com.wontake.plan.PlanService.Plan transferPlan) {
        public Reply(String type, String normalized, String reply, String menuId, List<String> menuIds,
                     List<String> steps, int notFoundStreak, String mode, boolean handoffSuggested, Plan plan) {
            this(type, normalized, reply, menuId, menuIds, steps, notFoundStreak, mode, handoffSuggested, plan, null);
        }
    }
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
    private final com.wontake.plan.PlanService transfers;
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai) { this(store,catalog,ai,null,new UsageGuard()); }
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior) { this(store,catalog,ai,behavior,new UsageGuard()); }
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior,UsageGuard guard) {
        this(store,catalog,ai,behavior,guard,new PlanService(store,new BlockCatalog(),new ProfileStore(store),guard));
    }
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior,UsageGuard guard,PlanService plans) {
        this(store, catalog, ai, behavior, guard, plans, null);
    }
    public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior,UsageGuard guard,com.wontake.plan.PlanService transfers) {
        this(store, catalog, ai, behavior, guard, new PlanService(store,new BlockCatalog(),new ProfileStore(store),guard), transfers);
    }
    @Autowired public ChatService(DemoStore store, MenuCatalog catalog, AiClient ai,BehaviorService behavior,UsageGuard guard,PlanService plans,com.wontake.plan.PlanService transfers) {
        this.store=store; this.catalog=catalog; this.ai=ai; this.behavior=behavior; this.guard=guard; this.plans=plans; this.transfers=transfers;
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
            // 내 적금에서 꺼내는 요청은 AI가 이체 카드(반대 방향)나 이체 메뉴 이동으로 답할 수 있어, AI와 빈 금액 채우기보다 먼저 서버 규칙으로 안내한다.
            if (transfers != null && transfers.fromOwn(question)) return record(s, question, inputType, planReply(s, transfers.fromOwnPlan(s)));
            // 빈 금액이 채워지면 같은 질문으로 업무 플랜을 새로 만들지 않는다.
            if (transfers != null) {
                var filled = transfers.fill(s, question);
                if (filled.isPresent()) return record(s, question, inputType, planReply(s, filled.get()));
            }
            // 작업 25: AI로 보내는 질문·이전 대화·행동 기록·말풍선 맥락은 가명화한 사본이다(대화 기록은 원문).
            var red = plans.redactor();
            var turns = red.turns(userId, sessionId, history(s.messages()));
            var allEvents = Boolean.TRUE.equals(store.consent(userId)) ? s.events() : List.<DemoStore.Event>of();
            var events = red.events(userId, sessionId, allEvents.subList(Math.max(0, allEvents.size()-20), allEvents.size()));
            var help = red.help(userId, sessionId, behavior==null?null:behavior.context(userId,sessionId,contextId));
            String aiQuestion = red.text(userId, sessionId, question.trim());
            AiClient.Answer result;
            // R5: 가명화된 고객 상태와 블록 사전을 같은 호출에 넣는다(추가 호출 없음).
            // 작업 35: 같은 세션의 최근 플랜(사건·끝낸 업무)도 함께 보내 이전 대화를 이어받게 한다.
            var context = new AiClient.Context(aiQuestion, inputType, turns, events, catalog.all(), help,
                plans.shape(userId, sessionId), plans.blocksForAi(), null, plans.recent(userId, sessionId, null));
            try { result = ai.answer(context); }
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
                if (transfers == null) throw new IllegalStateException("이체 PlanService 없이 만든 ChatService");
                var plan = transfers.plan(s, question.trim());
                // 받는 사람을 못 찾았으면(예: "5만원 이체해 줘") 채팅 AI가 함께 쓴 메뉴 안내를 보여 준다. Haiku가 분류를 틀려도 SPEC대로 메뉴 안내가 나오고, AI를 다시 부르지 않는다.
                // 단, Haiku가 이체 묶음일 때 답 문장을 비워 보낸 적이 있어(2026-10-27 실측), 안내가 형식에 맞지 않으면 plan의 다시 묻는 문장을 쓴다.
                boolean fallback = !plan.type().equals("PLAN") && valid(result);
                if (!fallback) return record(s, question, inputType, planReply(s, plan));
            }
            validate(result);
            guard.inScope(sessionId);
            var ids = List.copyOf(result.menuIds());
            Plan plan = result.hasPlan() ? plans.build(userId, sessionId, result.plan()) : null;
            if (plan != null) {
                // 플랜이 있으면 찾지 못함이 아니다. 블록으로 못 하는 일이 있으면 상담 버튼을 강조한다.
                plan.question = question.trim();
                plan.aiInput = aiInput(aiQuestion, plan.aiInput);
                s.notFoundStreak = 0;
                var reply = new Reply("PLAN", "VOICE".equals(inputType) ? result.normalized() : "", shortReply(result.reply()), ids.isEmpty() ? "" : ids.getFirst(), ids,
                    List.of(), 0, ai.mode(), result.handoff() || !plan.unhandled.isEmpty(), plan);
                return record(s, question, inputType, reply);
            }
            // 상담 버튼은 항상 쓸 수 있다. 범위 안 질문에서 찾지 못함 연속 2번이거나 AI가 권하면 버튼을 강조하도록 알린다(SPEC R3).
            s.notFoundStreak = ids.isEmpty() ? s.notFoundStreak + 1 : 0;
            // 유사 표현의 의미 판단은 AI가 맡는다. 확인 질문의 메뉴 이름은 카탈로그에서 가져와 후보 버튼과 일치시킨다.
            boolean confirm = result.needsConfirmation();
            String text = confirm ? "혹시 보고 싶은 게 " + String.join(" 또는 ", ids.stream().map(id -> catalog.find(id).name()).toList()) + "인가요?" : result.reply();
            var reply = new Reply(ids.isEmpty() ? "NOT_FOUND" : confirm ? "CONFIRM_MENU" : "ANSWER", "VOICE".equals(inputType) ? result.normalized() : "",
                text, ids.isEmpty() ? "" : ids.getFirst(), ids, confirm ? List.of() : List.copyOf(result.steps()), s.notFoundStreak, ai.mode(),
                result.handoff() || s.notFoundStreak >= 2, null);
            return record(s, question, inputType, reply);
        }
    }
    private Reply planReply(DemoStore.Session s, com.wontake.plan.PlanService.Plan plan) {
        return new Reply("PLAN", "", plan.message().isEmpty() ? PLAN_REPLY : plan.message(), "", List.of(), List.of(), s.notFoundStreak, ai.mode(), false, null, plan);
    }
    static final int PLAN_REPLY_MAX = 60;
    /** 작업 32: 플랜이 있으면 말풍선은 한 문장(60자 이내). 넘으면 첫 문장만, 첫 문장도 넘으면 59자 + "…". */
    static String shortReply(String reply) {
        String r = Objects.toString(reply, "").trim();
        if (r.length() <= PLAN_REPLY_MAX) return r;
        var m = java.util.regex.Pattern.compile("^.*?[.?!。](?=\\s|$)").matcher(r);
        if (m.find() && m.end() <= PLAN_REPLY_MAX) return m.group();
        return r.substring(0, PLAN_REPLY_MAX - 1) + "…";
    }

    /** 작업 32: 마무리 카드의 [추가로 할 일 찾기]. 바뀐 상태로 AI가 한 번 더 판단한다. */
    public Plan review(String userId, String sessionId, String planId) {
        return replan(userId, sessionId, plans.get(userId, sessionId, planId));
    }

    static final String REPLAN_FAILED = "다시 판단하지 못했어요. 지금까지 확인한 내용 기준으로 정리했어요.";

    /** 작업 23: 고객이 분실 카드를 확인하면 카드 블록을 다시 구성하고, AI가 전체 상황을 다시 판단한다. */
    public Plan confirmCards(String userId, String sessionId, String planId, List<String> cardRefs) {
        var plan = plans.confirmCards(userId, sessionId, planId, cardRefs);
        return replan(userId, sessionId, plan);
    }

    /** 작업 29: 고객이 정보 확인 블록에 답하면 저장하고 AI가 다시 판단한다. */
    public Plan answer(String userId, String sessionId, String planId, Integer index, String value) {
        var plan = plans.answer(userId, sessionId, planId, index, value);
        return replan(userId, sessionId, plan);
    }

    /**
     * 중요한 상태가 바뀌었을 때(카드 확인·정보 입력) AI를 한 번 더 불러 같은 플랜을 갱신한다. 끝낸 업무는 상태로 유지된다.
     * 사용량 상한에 포함하며, 실패하면 이전 플랜을 그대로 두고 알린다(가짜 플랜으로 바꾸지 않음).
     */
    public Plan replan(String userId, String sessionId, Plan plan) {
        var s = store.session(userId, sessionId);
        synchronized (s) {
            plan.notice = "";
            try {
                if (guard.blocked(sessionId)) throw new ApiException(429, "CHAT_BLOCKED", "잠시 AI 안내를 멈췄어요.");
                guard.chat(sessionId);
                var red = plans.redactor();
                var allEvents = Boolean.TRUE.equals(store.consent(userId)) ? s.events() : List.<DemoStore.Event>of();
                var events = red.events(userId, sessionId, allEvents.subList(Math.max(0, allEvents.size()-20), allEvents.size()));
                String aiQuestion = red.text(userId, sessionId, plan.question);
                var done = plan.blocks.stream().filter(b -> !"PENDING".equals(b.status)).map(b -> b.blockId + (b.targetRef.isEmpty() ? "" : "@" + b.targetRef)).toList();
                var context = new AiClient.Context(aiQuestion, "TEXT", red.turns(userId, sessionId, history(s.messages())), events, catalog.all(), null,
                    plans.shape(userId, sessionId), plans.blocksForAi(), new AiClient.PlanState(plan.confirmedCards, done),
                    plans.recent(userId, sessionId, plan.planId));
                var result = ai.answer(context);
                if (result == null || result.outOfScope() || !result.hasPlan()) plan.notice = REPLAN_FAILED;
                else {
                    plans.rebuild(userId, sessionId, plan.planId, result.plan());
                    plan.aiInput = aiInput(aiQuestion, plan.aiInput);
                }
            } catch (ApiException e) {
                plan.notice = REPLAN_FAILED;
            }
            return plan;
        }
    }

    /** 플랜의 "AI에 보낸 정보": 가명화된 질문과 고객 상태 모양. */
    private static Map<String, Object> aiInput(String question, Object customer) {
        var out = new LinkedHashMap<String, Object>();
        out.put("question", question);
        out.put("customer", customer);
        return out;
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
            || a.needsConfirmation() && a.menuIds().isEmpty()
            || a.steps()==null || a.steps().size()>3 || a.steps().stream().anyMatch(s -> s==null || s.isBlank() || s.length()>200));
    }
}
