package com.wontake.plan;

import com.wontake.ai.AiClient;
import com.wontake.api.ApiException;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 한 문장 → 작업 카드 목록(기획서 차별점 1). AI는 받는 곳과 금액만 고르고, 이체 종류, 위험도, 예상 잔액은 서버가 정한다.
 * 실제 거래는 하지 않는다. 따로 된 API 없이 채팅(ChatService)이 받는 사람을 말한 이체 요청을 알아챘을 때만 부른다(2026-10-27 사용자 결정).
 * 입력 검사, 범위 밖 횟수와 차단, 사용량 세기는 채팅(ChatService, UsageGuard)만 한다. 여기서는 UsageGuard를 쓰지 않는다.
 */
@Service
public class PlanService {
    /**
     * status: READY, NEEDS_ACCOUNT(주소록에 없음), NEEDS_AMOUNT(금액을 말하지 않음, amount=0).
     * 금액이 비면 최근 이체 금액 같은 값으로 채우지 않고 사용자에게 다시 묻는다(2026-10-27 승인). 돈을 얼마 보낼지는 사용자가 말한 값만 쓴다.
     */
    public record Task(int order, String type, String target, String account, long amount, String memo,
                       String risk, List<String> riskReasons, String status) {}
    public record Plan(String type, String message, List<Task> tasks, long balance, long totalAmount, long expectedBalance,
                       boolean canConfirm, List<String> blockers, String mode) {}
    /** 작업 상한(2026-10-27 승인). resources/ai/plan-system.txt의 "최대 5개"와 같이 고친다. */
    public static final int MAX_TASKS = 5;
    /** 프롬프트는 메모를 10자 안쪽으로 쓰라고 하지만, AI가 조금 넘겨도 카드를 버리지 않게 서버 한도는 넉넉히 둔다. */
    static final int MAX_TARGET = 30, MAX_MEMO = 50;
    public static final String OUT_OF_SCOPE_MESSAGE = "한 번에 맡기기는 이체와 내 계좌로 옮기기만 준비해요. 누구에게 얼마를 보낼지 말씀해 주세요.";
    public static final String NO_TASK_MESSAGE = "준비할 이체를 찾지 못했어요. 누구에게 얼마를 보낼지 말씀해 주세요.";
    public static final String NEEDS_AMOUNT_MESSAGE = "금액을 말씀하지 않은 작업이 있어요. 얼마를 보낼지 알려 주세요.";
    private final FinanceBook book;
    private final AiClient ai;
    private final List<AiClient.Payee> payees;

    public PlanService(FinanceBook book, AiClient ai) {
        this.book = book; this.ai = ai;
        // AI에는 이름과 별칭만 보낸다. 계좌번호와 이체 이력은 서버에만 둔다.
        payees = book.accounts().stream().map(a -> new AiClient.Payee(a.name(), a.kind(), a.aliases())).toList();
    }

    public Plan plan(String request) {
        AiClient.PlanDraft draft;
        try { draft = ai.plan(new AiClient.PlanContext(request.trim(), payees)); }
        catch (ApiException e) {
            if (!"AI_REFUSED".equals(e.code)) throw e;
            draft = new AiClient.PlanDraft(AiClient.Answer.OUT_OF_SCOPE, List.of());
        }
        if (draft != null && draft.outOfScope()) { return empty("OUT_OF_SCOPE", OUT_OF_SCOPE_MESSAGE); }
        if (draft == null || draft.tasks() == null || draft.tasks().size() > MAX_TASKS) throw invalid();
        if (draft.tasks().isEmpty()) return empty("NO_TASK", NO_TASK_MESSAGE);
        var tasks = new ArrayList<Task>();
        long total = 0;
        for (var d : draft.tasks()) {
            var task = task(tasks.size() + 1, d);
            tasks.add(task);
            try { total = Math.addExact(total, task.amount()); } catch (ArithmeticException e) { throw invalid(); }
        }
        long expected = book.balance() - total;
        var blockers = new ArrayList<String>();
        if (tasks.stream().anyMatch(t -> t.status().equals("NEEDS_ACCOUNT"))) blockers.add("NEEDS_ACCOUNT");
        // 금액 0은 AI 검증에서 이미 막으므로, 0인 작업은 금액을 말하지 않은 작업뿐이다.
        boolean needsAmount = tasks.stream().anyMatch(t -> t.amount() == 0);
        if (needsAmount) blockers.add("NEEDS_AMOUNT");
        // 잔액이 모자라도 목록은 보여 준다. 어떤 작업을 뺄지 사용자가 볼 수 있게 승인만 막는다(2026-10-27 승인).
        if (expected < 0) blockers.add("INSUFFICIENT_BALANCE");
        return new Plan("PLAN", needsAmount ? NEEDS_AMOUNT_MESSAGE : "", List.copyOf(tasks), book.balance(), total, expected,
            blockers.isEmpty(), List.copyOf(blockers), ai.mode());
    }

    private Task task(int order, AiClient.TaskDraft d) {
        if (d == null || d.target() == null || d.target().isBlank() || d.target().length() > MAX_TARGET
            || d.amount() != null && d.amount() <= 0 || d.memo() != null && d.memo().length() > MAX_MEMO) throw invalid();
        String target = d.target().trim(), memo = d.memo() == null ? "" : d.memo().trim();
        long amount = d.amount() == null ? 0 : d.amount();
        var account = book.find(target).orElse(null);
        if (account == null) {
            var risk = RiskRules.of(null, amount);
            return new Task(order, "TRANSFER", target, "", amount, memo, risk.level(), risk.reasons(), "NEEDS_ACCOUNT");
        }
        var risk = RiskRules.of(account, amount);
        return new Task(order, account.kind().equals("OWN") ? "OWN_TRANSFER" : "TRANSFER", account.name(), masked(account), amount,
            memo, risk.level(), risk.reasons(), d.amount() == null ? "NEEDS_AMOUNT" : "READY");
    }
    /** 내 계좌는 화면(계좌조회)처럼 번호를 그대로, 남의 계좌는 은행과 끝 4자리만 보여 준다. */
    private static String masked(FinanceBook.Account a) {
        return a.kind().equals("OWN") ? a.number() : a.bank() + " *" + a.number().substring(a.number().length() - 4);
    }
    private Plan empty(String type, String message) {
        return new Plan(type, message, List.of(), book.balance(), 0, book.balance(), false, List.of(), ai.mode());
    }
    private static ApiException invalid() { return new ApiException(502, "AI_INVALID_RESPONSE", "AI 작업 목록 형식을 확인하지 못했어요. 다시 시도해 주세요."); }
}
