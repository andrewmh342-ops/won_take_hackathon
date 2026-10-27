package com.wontake.plan;

import com.wontake.ai.AiClient;
import com.wontake.api.ApiException;
import com.wontake.session.DemoStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 한 문장 → 작업 카드 목록(기획서 차별점 1). AI는 받는 곳과 금액만 고르고, 이체 종류, 위험도, 예상 잔액은 서버가 정한다.
 * 따로 된 API 없이 채팅(ChatService)이 받는 사람을 말한 이체 요청을 알아챘을 때만 부른다(2026-10-27 사용자 결정).
 * 입력 검사, 범위 밖 횟수와 차단, 사용량 세기는 채팅(ChatService, UsageGuard)만 한다. 여기서는 UsageGuard를 쓰지 않는다.
 * 고객이 가상 비밀번호로 승인하면 Balances가 출금·내 계좌 입금을 함께 반영한다. 실제 돈은 움직이지 않는다.
 * 승인할 때 AI는 부르지 않는다. AI는 목록만 준비하고, 비밀번호와 승인 결과는 보지 않는다(승인은 대화 기록에도 남기지 않는다).
 */
@Service
public class PlanService {
    /**
     * status: READY, NEEDS_ACCOUNT(주소록에 없음), NEEDS_AMOUNT(금액을 말하지 않음, amount=0).
     * 금액이 비면 최근 이체 금액 같은 값으로 채우지 않고 사용자에게 다시 묻는다(2026-10-27 승인). 돈을 얼마 보낼지는 사용자가 말한 값만 쓴다.
     */
    public record Task(int order, String type, String target, String account, long amount, String memo,
                       String risk, List<String> riskReasons, String status) {}
    /** planId는 type이 PLAN일 때만 있다. 승인은 이 id로 저장된 목록만 실행한다. */
    public record Plan(String planId, String type, String message, List<Task> tasks, long balance, long totalAmount, long expectedBalance,
                       boolean canConfirm, List<String> blockers, String mode) {}
    public record Confirmation(String planId, String status, long totalAmount, long balanceBefore, long balanceAfter) {}
    /** 작업 상한(2026-10-27 승인). resources/ai/plan-system.txt의 "최대 5개"와 같이 고친다. */
    public static final int MAX_TASKS = 5;
    /** 프롬프트는 메모를 10자 안쪽으로 쓰라고 하지만, AI가 조금 넘겨도 카드를 버리지 않게 서버 한도는 넉넉히 둔다. */
    static final int MAX_TARGET = 30, MAX_MEMO = 50;
    /** 세션 하나에 남겨 두는 승인 전 목록 수. 시연 대화는 20개 메시지까지만 남기므로 그 절반으로 둔다. 넘으면 오래된 것부터 지운다. */
    static final int MAX_OPEN_PLANS = 10;
    /** 이 횟수만큼 비밀번호를 틀리면 그 고객의 승인을 잠근다(2026-10-27 사용자 결정). 맞게 입력하면 0으로 돌아가고, 서버를 재시작하면 풀린다. */
    public static final int MAX_PASSWORD_FAILURES = 5;
    /** 승인이 막혔을 때의 서버 문장. 화면은 code(NEEDS_ACCOUNT 등)를 frontend/src/plan/planView.js의 BLOCKER 문구로 바꿔 보여 준다. */
    public static final String BLOCKED_MESSAGE = "지금은 승인할 수 없는 목록이에요. 카드의 안내를 확인해 주세요.";
    public static final String OUT_OF_SCOPE_MESSAGE = "한 번에 맡기기는 이체와 내 계좌로 옮기기만 준비해요. 누구에게 얼마를 보낼지 말씀해 주세요.";
    public static final String NO_TASK_MESSAGE = "준비할 이체를 찾지 못했어요. 누구에게 얼마를 보낼지 말씀해 주세요.";
    public static final String NEEDS_AMOUNT_MESSAGE = "이체 금액이 입력되지 않은 항목이 있습니다. 얼마를 이체하시겠어요?";
    private final FinanceBook book;
    private final AiClient ai;
    private final DemoStore store;
    private final Balances balances;
    private final byte[] password;
    /** 고객(userId)별 연속으로 틀린 횟수. 세션별로 세면 새 대화를 열어 잠금을 풀 수 있어 고객별로 센다. */
    private final Map<String, Integer> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<AiClient.Payee> payees;

    /** password는 시연용 가상 간편 비밀번호다(app.plan.password). 비어 있으면 아무 값이나 통과하지 않게 서버를 띄우지 않는다. */
    public PlanService(FinanceBook book, AiClient ai, DemoStore store, Balances balances, @Value("${app.plan.password}") String password) {
        if (password == null || password.isBlank()) throw new IllegalStateException("가상 비밀번호(app.plan.password)가 비어 있습니다.");
        this.book = book; this.ai = ai; this.store = store; this.balances = balances;
        this.password = password.getBytes(StandardCharsets.UTF_8);
        // AI에는 이름과 별칭만 보낸다. 계좌번호와 이체 이력은 서버에만 둔다.
        payees = book.accounts().stream().map(a -> new AiClient.Payee(a.name(), a.kind(), a.aliases())).toList();
    }

    /** 채팅의 세션 잠금(synchronized(s)) 안에서 부른다. 작업이 있는 목록은 세션에 저장한다. */
    public Plan plan(DemoStore.Session s, String request) {
        AiClient.PlanDraft draft;
        try { draft = ai.plan(new AiClient.PlanContext(request.trim(), payees)); }
        catch (ApiException e) {
            if (!"AI_REFUSED".equals(e.code)) throw e;
            draft = new AiClient.PlanDraft(AiClient.Answer.OUT_OF_SCOPE, List.of());
        }
        if (draft != null && draft.outOfScope()) { return empty(s, "OUT_OF_SCOPE", OUT_OF_SCOPE_MESSAGE); }
        if (draft == null || draft.tasks() == null || draft.tasks().size() > MAX_TASKS) throw invalid();
        if (draft.tasks().isEmpty()) return empty(s, "NO_TASK", NO_TASK_MESSAGE);
        var tasks = new ArrayList<Task>();
        for (var d : draft.tasks()) {
            if (d == null || d.target() == null || d.target().isBlank() || d.target().length() > MAX_TARGET
                || d.amount() != null && d.amount() <= 0 || d.memo() != null && d.memo().length() > MAX_MEMO) throw invalid();
            tasks.add(task(tasks.size() + 1, d.target().trim(), d.amount(), d.memo() == null ? "" : d.memo().trim()));
        }
        return save(s, tasks);
    }

    /**
     * 세션의 가장 최근 목록에 금액이 빈 작업이 있으면, 고객의 이번 말에서 그 금액만 받아 채운 새 목록을 만든다(채팅 세션 잠금 안에서 부른다).
     * AI는 빈 곳의 금액만 고른다. 이미 정한 받는 곳과 금액은 저장된 목록 그대로 두고, 위험도와 잔액은 서버 규칙으로 다시 계산한다.
     * 고객이 금액을 말하지 않았으면(모두 null) 비워 두고 보통 대화로 넘긴다. 옛 목록은 지워 승인할 수 없게 한다.
     */
    public Optional<Plan> fill(DemoStore.Session s, String message) {
        var pending = s.plans.values().stream().reduce((a, b) -> b)
            .filter(p -> p.tasks().stream().anyMatch(t -> t.status().equals("NEEDS_AMOUNT"))).orElse(null);
        if (pending == null) return Optional.empty();
        var targets = pending.tasks().stream().filter(t -> t.status().equals("NEEDS_AMOUNT")).map(Task::target).distinct().toList();
        List<AiClient.AmountDraft> drafts;
        // AI가 거절한 말은 채우지 않고 보통 대화로 넘긴다. 범위 밖 안내와 차단 횟수는 채팅이 맡는다.
        try { drafts = ai.fill(new AiClient.FillContext(targets, message.trim())); }
        catch (ApiException e) {
            if (!"AI_REFUSED".equals(e.code)) throw e;
            return Optional.empty();
        }
        if (drafts == null || drafts.size() > targets.size()) throw invalid();
        var amounts = new HashMap<String, Long>();
        var named = new HashSet<String>();
        for (var d : drafts) {
            if (d == null || !targets.contains(d.target()) || !named.add(d.target()) || d.amount() != null && d.amount() <= 0) throw invalid();
            if (d.amount() != null) amounts.put(d.target(), d.amount());
        }
        if (amounts.isEmpty()) return Optional.empty();
        var tasks = pending.tasks().stream().map(t -> task(t.order(), t.target(),
            t.status().equals("NEEDS_AMOUNT") && amounts.containsKey(t.target()) ? amounts.get(t.target()) : said(t), t.memo())).toList();
        s.plans.remove(pending.planId());
        return Optional.of(save(s, tasks));
    }

    /**
     * 저장된 목록을 가상 비밀번호로 승인한다. 받는 곳과 금액만 저장된 목록에서 가져오고, 주소록, 빈 금액, 잔액은 지금 규칙으로 다시 검사한다.
     * HIGH 위험도는 화면에서 경고만 하고 승인을 막지 않는다. 같은 planId는 한 번만 실행한다.
     * 확인 순서: 목록이 없으면 404, 잠겼으면 423, 비밀번호가 틀리면 401(목록은 남는다, 5번째로 틀리면 423), 이미 승인했으면 409, 규칙에 걸리면 그 코드로 409.
     */
    public Confirmation confirm(String userId, String sessionId, String planId, String password) {
        if (planId == null || planId.isBlank() || password == null) throw ApiException.invalid("승인할 목록과 비밀번호를 확인해 주세요.");
        var s = store.session(userId, sessionId);
        synchronized (s) {
            var plan = s.plans.get(planId);
            boolean done = s.confirmedPlans.contains(planId);
            if (plan == null && !done) throw new ApiException(404, "PLAN_NOT_FOUND", "승인할 목록을 찾지 못했어요. 가장 최근 목록에서 다시 승인해 주세요.");
            checkPassword(userId, password);
            if (done) throw new ApiException(409, "PLAN_ALREADY_CONFIRMED", "이미 승인한 목록이에요.");
            var checked = build(planId, userId, plan.tasks().stream().map(t -> task(t.order(), t.target(), said(t), t.memo())).toList());
            if (!checked.canConfirm()) throw new ApiException(409, checked.blockers().getFirst(), BLOCKED_MESSAGE);
            // 다른 세션에서 같은 고객이 먼저 승인했을 수 있어, 잔액은 빼는 순간에 한 번 더 확인한다.
            long after = balances.transfer(userId, checked.totalAmount(), checked.tasks());
            s.plans.remove(planId);
            s.confirmedPlans.add(planId);
            return new Confirmation(planId, "CONFIRMED", checked.totalAmount(), after + checked.totalAmount(), after);
        }
    }

    private void checkPassword(String userId, String input) {
        if (failures.getOrDefault(userId, 0) >= MAX_PASSWORD_FAILURES) throw locked();
        if (MessageDigest.isEqual(password, input.getBytes(StandardCharsets.UTF_8))) { failures.remove(userId); return; }
        int count = failures.merge(userId, 1, Integer::sum);
        if (count >= MAX_PASSWORD_FAILURES) throw locked();
        throw new ApiException(401, "PASSWORD_MISMATCH", "비밀번호가 맞지 않아요. (" + count + "/" + MAX_PASSWORD_FAILURES + "회)");
    }
    private static ApiException locked() {
        return new ApiException(423, "PASSWORD_LOCKED", "비밀번호를 " + MAX_PASSWORD_FAILURES + "번 틀려 승인을 잠갔어요. 상담사에게 연결해 주세요.");
    }

    /** amount가 null이면 고객이 금액을 말하지 않은 작업이다. 받는 곳을 주소록에서 다시 찾아 종류, 계좌 표시, 위험도, 상태를 정한다. */
    private Task task(int order, String target, Long amount, String memo) {
        long value = amount == null ? 0 : amount;
        var account = book.find(target).orElse(null);
        if (account == null) {
            var risk = RiskRules.of(null, value);
            return new Task(order, "TRANSFER", target, "", value, memo, risk.level(), risk.reasons(), "NEEDS_ACCOUNT");
        }
        var risk = RiskRules.of(account, value);
        return new Task(order, account.kind().equals("OWN") ? "OWN_TRANSFER" : "TRANSFER", account.name(), masked(account), value,
            memo, risk.level(), risk.reasons(), amount == null ? "NEEDS_AMOUNT" : "READY");
    }
    /** 저장된 작업의 금액 0은 말하지 않은 금액이다. 0원 작업은 AI 검증에서 막으므로 다른 뜻이 없다. */
    private static Long said(Task t) { return t.amount() == 0 ? null : t.amount(); }

    private Plan build(String planId, String userId, List<Task> tasks) {
        long total = 0;
        for (var t : tasks) {
            try { total = Math.addExact(total, t.amount()); } catch (ArithmeticException e) { throw invalid(); }
        }
        long balance = balances.of(userId), expected = balance - total;
        var blockers = new ArrayList<String>();
        if (tasks.stream().anyMatch(t -> t.status().equals("NEEDS_ACCOUNT"))) blockers.add("NEEDS_ACCOUNT");
        boolean needsAmount = tasks.stream().anyMatch(t -> t.amount() == 0);
        if (needsAmount) blockers.add("NEEDS_AMOUNT");
        // 잔액이 모자라도 목록은 보여 준다. 어떤 작업을 뺄지 사용자가 볼 수 있게 승인만 막는다(2026-10-27 승인).
        if (expected < 0) blockers.add("INSUFFICIENT_BALANCE");
        return new Plan(planId, "PLAN", needsAmount ? NEEDS_AMOUNT_MESSAGE : "", List.copyOf(tasks), balance, total, expected,
            blockers.isEmpty(), List.copyOf(blockers), ai.mode());
    }
    private Plan save(DemoStore.Session s, List<Task> tasks) {
        var plan = build(UUID.randomUUID().toString(), s.userId, tasks);
        s.plans.put(plan.planId(), plan);
        while (s.plans.size() > MAX_OPEN_PLANS) s.plans.remove(s.plans.keySet().iterator().next());
        return plan;
    }
    /** 내 계좌는 화면(계좌조회)처럼 번호를 그대로, 남의 계좌는 은행과 끝 4자리만 보여 준다. */
    private static String masked(FinanceBook.Account a) {
        return a.kind().equals("OWN") ? a.number() : a.bank() + " *" + a.number().substring(a.number().length() - 4);
    }
    private Plan empty(DemoStore.Session s, String type, String message) {
        long balance = balances.of(s.userId);
        return new Plan(null, type, message, List.of(), balance, 0, balance, false, List.of(), ai.mode());
    }
    private static ApiException invalid() { return new ApiException(502, "AI_INVALID_RESPONSE", "AI 작업 목록 형식을 확인하지 못했어요. 다시 시도해 주세요."); }
}
