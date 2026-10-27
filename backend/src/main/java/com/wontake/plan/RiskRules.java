package com.wontake.plan;

import java.util.*;

/** 위험도는 AI가 아니라 이 규칙이 정한다(기획서 3장 위험도 표, 4장 "판정은 백엔드 규칙"). */
final class RiskRules {
    record Risk(String level, List<String> reasons) {}
    /**
     * 이상 금액: 그 사람에게 보낸 이력 중 최대 금액의 이 배수를 넘으면 HIGH.
     * 기획서 예시(평소 30만 → 300만, 10배)는 걸리고 평소보다 조금 많은 금액은 걸리지 않게 3배로 정했다(2026-10-27 승인).
     */
    static final long ABNORMAL_MULTIPLIER = 3;
    private RiskRules() {}

    /** account가 null이면 주소록에 없는 받는 사람이다. 이름만으로 남의 계좌를 찾지 않으므로(기획서 5장) 첫 거래로 본다. */
    static Risk of(FinanceBook.Account account, long amount) {
        if (account == null) return new Risk("HIGH", List.of("NEW_RECIPIENT"));
        if (account.kind().equals("OWN")) return new Risk("MEDIUM_LOW", List.of("OWN_ACCOUNT"));
        long usualMax = Collections.max(account.history());
        if (amount > usualMax * ABNORMAL_MULTIPLIER) return new Risk("HIGH", List.of("ABNORMAL_AMOUNT"));
        return new Risk("MEDIUM", List.of("FREQUENT_RECIPIENT"));
    }
}
