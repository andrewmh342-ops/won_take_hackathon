package com.wontake.ai;

import java.util.*;

/** 테스트·키 없는 연결 확인용 규칙. 실제 AI 추론으로 시연하지 않는다. */
public class FakeAiClient implements AiClient {
    private static final List<String> FINANCE_WORDS = List.of("앱", "은행", "계좌", "통장", "이체", "송금", "출금", "입금", "한도", "잔액", "내역",
        "인증", "비밀번호", "로그인", "기기", "증명", "서류", "환전", "환율", "외화", "카드", "대출", "예금", "적금", "수수료", "상담", "돈", "오류", "안 돼", "안돼");
    @Override public ProactiveSuggestion suggest(BehaviorContext context) {
        String category=context.visits().getLast().category();
        String question=category.equals("이체/출금") ? "이체나 송금 메뉴를 찾는 데 도움이 필요하세요?" : category+" 메뉴를 찾는 데 도움이 필요하세요?";
        return new ProactiveSuggestion(true,question,context.visits().stream().map(Visit::menuId).distinct().limit(2).toList());
    }
    /** 연결 테스트용 결정적 플랜. 실제 순서 판단은 CLAUDE 모드에서 한다. 보유 여부 걸러내기는 서버(PlanService)가 한다. */
    private static AiPlan plan(Context c) {
        if (c.customer() == null) return AiPlan.EMPTY;
        String q = c.question();
        var blocks = new ArrayList<AiBlock>(); var events = new ArrayList<String>(); var unhandled = new ArrayList<String>();
        if (List.of("잃어버", "잃었", "분실", "도난").stream().anyMatch(q::contains)) {
            var cards = c.customer().cards();
            if (List.of("한 장", "한장", "1장").stream().anyMatch(q::contains)) cards = cards.subList(0, Math.min(1, cards.size()));
            else if (List.of("두 장", "두장", "2장").stream().anyMatch(q::contains)) cards = cards.subList(0, Math.min(2, cards.size()));
            events.add("카드 분실");
            for (var card : cards) blocks.add(new AiBlock("card-lost-report", card.ref(), "", "(모의) 부정 사용을 막기 위해 먼저 정지해요."));
            for (var card : cards) if (card.simplePay()) blocks.add(new AiBlock("simple-pay-unlink", card.ref(), "", "(모의) 휴대폰 결제도 막아요."));
            for (var card : cards) blocks.add(new AiBlock("card-reissue", card.ref(), "", "(모의) 새 카드를 신청해요."));
            for (var card : cards) if (!card.autoPayments().isEmpty()) blocks.add(new AiBlock("auto-payment-switch", card.ref(), "", "(모의) 정기결제가 끊기지 않게 옮겨요."));
            if (q.contains("신분증")) unhandled.add("신분증 분실 신고");
            if (q.contains("OTP")) unhandled.add("OTP 재발급");
        }
        if (List.of("여행", "출국").stream().anyMatch(q::contains)) {
            var countries = new LinkedHashMap<String, String>();
            countries.put("일본", "JPY"); countries.put("미국", "USD"); countries.put("유럽", "EUR"); countries.put("프랑스", "EUR");
            countries.put("중국", "CNY"); countries.put("몽골", "MNT");
            String currency = countries.entrySet().stream().filter(e -> q.contains(e.getKey())).map(Map.Entry::getValue).findFirst().orElse("");
            events.add("해외여행 준비");
            if (!currency.isEmpty()) blocks.add(new AiBlock("fx-rate-check", "", currency, "(모의) 여행지 환율부터 확인해요."));
            blocks.add(new AiBlock("travel-card-check", "", "", "(모의) 해외에서 쓸 카드를 확인해요."));
            blocks.add(new AiBlock("travel-card-apply", "", "", "(모의) 트래블카드가 없으면 신청해요."));
            for (var card : c.customer().cards()) blocks.add(new AiBlock("overseas-payment-check", card.ref(), "", "(모의) 해외에서 결제되게 해요."));
            if (!currency.isEmpty()) blocks.add(new AiBlock("fx-exchange-apply", "", currency, "(모의) 현금이 필요하면 환전해요."));
            blocks.add(new AiBlock("travel-insurance-guide", "", "", "(모의) 보험 가입 방법을 확인해요."));
        }
        return new AiPlan(events, blocks, unhandled, blocks.isEmpty() ? "" : "(모의) 급한 일부터 배치했어요.");
    }
    @Override public HandoffSummary summarize(HandoffContext c) {
        String purpose=c.conversation().stream().filter(t->t.role().equals("USER")).reduce((a,b)->b)
            .map(t->"(모의) 고객 마지막 질문: "+t.message()).orElse("");
        var tried=new LinkedHashSet<String>();
        for(var e:c.clickTimeline()) for(var m:c.menus()) if(m.id().equals(e.elementId())) tried.add(m.name()+" 방문");
        for(var t:c.conversation()) if(t.role().equals("ASSISTANT"))
            for(var m:c.menus()) if(t.message().contains(m.name())) tried.add(m.name()+" 안내받음");
        for(var line:c.planProgress()) if(line.startsWith("[완료] ")) tried.add(line.substring(5));
        var left=c.planProgress().stream().filter(l->!l.startsWith("[완료] ")).toList();
        return new HandoffSummary(purpose,"",tried.isEmpty()?"":"(모의) "+String.join(", ",tried),left.isEmpty()?"":"(모의) "+String.join(", ",left));
    }
    @Override public Answer answer(Context c) {
        String q = c.question();
        var plan = plan(c);
        if (!plan.blocks().isEmpty())
            return new Answer("연결 테스트용 모의 플랜입니다. 해야 할 일을 순서대로 정리했어요.", "VOICE".equals(c.inputType()) ? q : "",
                List.of(), List.of(), false, Answer.IN_SCOPE, plan);
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
