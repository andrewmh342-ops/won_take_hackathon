package com.wontake.ai;

import java.util.*;

/** 테스트·키 없는 연결 확인용 규칙. 실제 AI 추론으로 시연하지 않는다. */
public class FakeAiClient implements AiClient {
    @Override public Answer answer(Context c) {
        String q = c.question();
        List<String> ids = new ArrayList<>();
        if (q.contains("내역")) {
            String domain = "";
            for (var t : c.history()) {
                if (t.role().equals("USER")) {
                    if (t.message().contains("환전")) domain="exchange";
                    else if (t.message().contains("이체")) domain="transfer";
                }
            }
            for (var event : c.recentEvents()) {
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
        final var selected=ids;
        String names = String.join(", ", c.menus().stream().filter(m -> selected.contains(m.id())).map(m -> m.name()).toList());
        return new Answer(ids.isEmpty() ? "연결 테스트용 모의 응답입니다. 관련 메뉴를 찾지 못했어요." :
            "연결 테스트용 모의 응답입니다. " + names + " 메뉴를 확인해 보세요.",
            "VOICE".equals(c.inputType()) ? q : "", ids, List.of());
    }
}
