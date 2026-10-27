package com.wontake.assist;

import com.wontake.assist.ProfileStore.CardState;
import com.wontake.assist.ProfileStore.CustomerState;
import java.util.*;

/**
 * SPEC R5 목표 중심 판단: 목표 상태를 가상 상태로 사실대로 판정한다(블록 완료와 분리).
 * 판정만 하고 업무·순서를 정하지 않는다. 그건 AI의 몫이다.
 */
public final class GoalCheck {
    public record GoalStatus(String type, String label, String status, String detail) {}
    private GoalCheck() {}

    /** lostRefs: 고객이 잃어버렸다고 확인(또는 AI가 추정)한 카드. 정지 전이어도 결제 수단으로 세지 않는다. */
    public static List<GoalStatus> evaluate(CustomerState s, List<String> types, Set<String> lostRefs, String currency, Integer daysUntilDeparture) {
        var out = new ArrayList<GoalStatus>();
        for (var type : new LinkedHashSet<>(types)) {
            switch (type) {
                case "CARD_PROTECTION" -> {
                    var lost = s.cards.stream().filter(c -> lostRefs.contains(c.ref)).toList();
                    long stopped = lost.stream().filter(c -> "STOPPED".equals(c.status)).count();
                    out.add(new GoalStatus(type, "분실 카드 보호", !lost.isEmpty() && stopped == lost.size() ? "SECURED" : "PENDING",
                        lost.isEmpty() ? "잃어버린 카드를 확인해 주세요" : "분실 카드 " + lost.size() + "장 중 " + stopped + "장 정지"));
                }
                case "PAYMENT_ABROAD" -> out.add(payment(s, lostRefs, daysUntilDeparture));
                case "CASH_ABROAD" -> {
                    var mine = s.exchangeRequests.stream().filter(r -> currency == null || currency.isEmpty() || r.currency.equals(currency)).toList();
                    out.add(new GoalStatus(type, "현지 현금", mine.isEmpty() ? "NOT_READY" : "SECURED",
                        mine.isEmpty() ? "아직 환전을 신청하지 않았어요" : mine.getLast().currency + " 환전 신청됨(수령 전)"));
                }
                default -> { } // OTHER 등은 서버가 판정하지 않는다
            }
        }
        return out;
    }

    private static GoalStatus payment(CustomerState s, Set<String> lostRefs, Integer days) {
        String label = "해외 결제 수단";
        var usable = s.cards.stream().filter(c -> c.usable() && !lostRefs.contains(c.ref)).toList();
        var ready = usable.stream().filter(c -> c.overseasPayment).findFirst();
        if (ready.isPresent()) return new GoalStatus("PAYMENT_ABROAD", label, "SECURED", "확보: " + ready.get().display() + "(해외결제 켜짐)");
        if (!usable.isEmpty()) return new GoalStatus("PAYMENT_ABROAD", label, "NEEDS_SETUP", "설정 필요: " + usable.getFirst().display() + "의 해외결제가 꺼져 있어요");
        String when = days == null ? "" : ", 출국까지 " + days + "일";
        var waiting = s.cards.stream().filter(c -> c.reissued).map(CardState::display).toList();
        if (!waiting.isEmpty() || s.travelCardApplication != null)
            return new GoalStatus("PAYMENT_ABROAD", label, "UNSECURED", "미확보(새 카드 수령 대기" + when + "). 출국 전에 받을 수 있는지 확인이 필요해요");
        return new GoalStatus("PAYMENT_ABROAD", label, "UNSECURED", "미확보: 지금 해외에서 사용 가능한 카드가 없어요" + when);
    }
}
