package com.wontake.ai;

import com.wontake.api.ApiException;
import java.util.*;
import java.util.regex.Pattern;

/** 테스트·키 없는 연결 확인용 규칙. 실제 AI 추론으로 시연하지 않는다. */
public class FakeAiClient implements AiClient {
    private static final List<String> FINANCE_WORDS = List.of("앱", "은행", "계좌", "통장", "이체", "송금", "출금", "입금", "한도", "잔액", "내역",
        "인증", "비밀번호", "로그인", "기기", "증명", "서류", "환전", "환율", "외화", "카드", "대출", "예금", "적금", "수수료", "상담", "돈", "오류", "안 돼", "안돼");
    @Override public ProactiveSuggestion suggest(BehaviorContext context) {
        String category=context.visits().getLast().category();
        String question=category.equals("이체/출금") ? "이체나 송금 메뉴를 찾는 데 도움이 필요하세요?" : category+" 메뉴를 찾는 데 도움이 필요하세요?";
        return new ProactiveSuggestion(true,question,context.visits().stream().map(Visit::menuId).distinct().limit(2).toList());
    }
    @Override public HandoffSummary summarize(HandoffContext c) {
        String purpose=c.conversation().stream().filter(t->t.role().equals("USER")).reduce((a,b)->b)
            .map(t->"(모의) 고객 마지막 질문: "+t.message()).orElse("");
        var tried=new LinkedHashSet<String>();
        for(var e:c.clickTimeline()) for(var m:c.menus()) if(m.id().equals(e.elementId())) tried.add(m.name()+" 방문");
        for(var t:c.conversation()) if(t.role().equals("ASSISTANT"))
            for(var m:c.menus()) if(t.message().contains(m.name())) tried.add(m.name()+" 안내받음");
        return new HandoffSummary(purpose,"",tried.isEmpty()?"":"(모의) "+String.join(", ",tried),"");
    }
    private static final Pattern MAN_WON = Pattern.compile("(\\d+)\\s*만\\s*원"), WON = Pattern.compile("(\\d[\\d,]*)\\s*원"),
        WHO = Pattern.compile("(\\S+?)(?:한테|에게)"), SEND = Pattern.compile("\\d\\s*(만\\s*)?원|보내|이체|송금|넣어");
    /** 연결 테스트용: 쉼표로 나눈 조각마다 받는 곳 별칭(없으면 "~한테")과 "N만원"/"N원"을 찾는다. */
    @Override public PlanDraft plan(PlanContext c) {
        var tasks = new ArrayList<TaskDraft>();
        for (var part : c.request().split("[,，]")) {
            String target = null;
            for (var p : c.payees()) for (var alias : p.aliases()) if (target == null && part.contains(alias)) target = p.name();
            if (target == null) { var who = WHO.matcher(part); if (who.find()) target = who.group(1); }
            if (target != null) tasks.add(new TaskDraft(target, amount(part), ""));
        }
        if (tasks.isEmpty() && FINANCE_WORDS.stream().noneMatch(c.request()::contains)) return new PlanDraft(Answer.OUT_OF_SCOPE, List.of());
        return new PlanDraft(Answer.IN_SCOPE, tasks);
    }
    /** long을 넘는 금액은 null(말하지 않음)로 바꾸지 않는다. 그러면 잘못 읽은 것이 "금액을 다시 묻기"로 숨는다. */
    private static Long amount(String part) {
        try {
            var m = MAN_WON.matcher(part);
            if (m.find()) return Math.multiplyExact(Long.parseLong(m.group(1)), 10_000L);
            m = WON.matcher(part);
            return m.find() ? Long.parseLong(m.group(1).replace(",", "")) : null;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new ApiException(502, "AI_INVALID_RESPONSE", "AI 작업 목록 형식을 확인하지 못했어요. 다시 시도해 주세요.");
        }
    }
    @Override public Answer answer(Context c) {
        String q = c.question();
        // 연결 테스트용 판정: "누구한테/에게"와 돈을 보내는 말이 함께 있으면 이체 요청으로 본다("상담사에게 연결해 줘"는 아니다).
        // 실제 판정은 CLAUDE 모드에서 지침으로 한다. 프론트 mock.js의 같은 판정과 함께 고친다.
        if (WHO.matcher(q).find() && SEND.matcher(q).find()) return new Answer("연결 테스트용 모의 응답입니다. 계좌이체 메뉴를 확인해 보세요.", "", List.of("instant-transfer"), List.of(), false, Answer.TRANSFER_PLAN);
        List<String> ids = new ArrayList<>();
        if(q.contains("방금 제안") && c.helpContext()!=null) ids=c.helpContext().visits().stream().map(Visit::menuId).distinct().limit(2).toList();
        else if (q.contains("내역")) {
            String domain = "";
            for (var t : c.history()) {
                if (t.role().equals("USER")) {
                    if (t.message().contains("환전")) domain="exchange";
                    else if (t.message().contains("이체")) domain="transfer";
                }
            }
            var contextEvents = c.recentEvents();
            if(c.helpContext()!=null && contextEvents.stream().noneMatch(e->Set.of("exchange","transfer","account").contains(e.screenId())))
                contextEvents=c.helpContext().recentEvents();
            for (var event : contextEvents) {
                if (event.screenId().equals("exchange")) domain="exchange";
                else if (event.screenId().equals("transfer")) domain="transfer";
                else if (event.screenId().equals("account")) domain="account";
            }
            if (q.contains("환전")) domain="exchange";
            else if (q.contains("이체")) domain="transfer";
            ids = switch(domain) {
                case "exchange" -> List.of("exchange-history");
                case "transfer" -> List.of("transfer-history");
                case "account" -> List.of("transaction-history");
                default -> List.of("transfer-history", "exchange-history");
            };
        } else if (q.contains("한도")) ids=List.of("transfer-limit-manage");
        else {
            for (var m : c.menus()) if (q.contains(m.name())) { ids=List.of(m.id()); break; }
            if (ids.isEmpty()) {
                if (q.contains("환전")) ids=List.of("exchange-apply");
                else if (q.contains("이체") || q.contains("송금")) ids=List.of("instant-transfer");
                else if (q.contains("계좌") || q.contains("잔액")) ids=List.of("all-accounts");
                else if (q.contains("증명")) ids=List.of("balance-certificate");
                else if (q.contains("인증")) ids=List.of("auth-center");
            }
        }
        // 연결 테스트용 범위 판정: 메뉴도 금융앱 이용 낱말도 없으면 범위 밖. 실제 판정은 CLAUDE 모드에서 지침으로 한다.
        if (ids.isEmpty() && FINANCE_WORDS.stream().noneMatch(q::contains))
            return new Answer("", "", List.of(), List.of(), false, Answer.OUT_OF_SCOPE);
        final var selected=ids;
        String names = String.join(", ", c.menus().stream().filter(m -> selected.contains(m.id())).map(m -> m.name()).toList());
        return new Answer(ids.isEmpty() ? "연결 테스트용 모의 응답입니다. 관련 메뉴를 찾지 못했어요." :
            "연결 테스트용 모의 응답입니다. " + names + " 메뉴를 확인해 보세요.",
            "VOICE".equals(c.inputType()) ? q : "", ids, List.of(), q.contains("상담"));
    }
}
