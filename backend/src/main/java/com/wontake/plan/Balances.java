package com.wontake.plan;

import com.wontake.api.ApiException;
import com.wontake.session.DemoStore;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 고객별 가상 계좌 잔액. finance.json에서 시작해 승인 시 출금 계좌에서 빼고 내 계좌 수취분은 더한다.
 * 서버 메모리라 재시작하면 처음 잔액으로 돌아간다(2026-10-27 사용자 결정). 실제 돈은 움직이지 않는다.
 * 화면용 계좌 목록도 같은 현재 잔액에서 만든다.
 */
@Component
public class Balances {
    public record Account(String name, String number, long balance, boolean withdrawal) {}
    public record Overview(List<Account> accounts, long totalBalance) {}
    private final FinanceBook book;
    // 불변 스냅샷을 한 번에 교체해 조회 중 생활통장 감소와 적금 증가의 중간 상태가 보이지 않게 한다.
    private final Map<String, Map<String, Long>> current = new ConcurrentHashMap<>();
    public Balances(FinanceBook book) { this.book = book; }

    private Map<String, Long> snapshot(String userId) {
        return current.computeIfAbsent(DemoStore.id(userId), id -> {
            var initial = new HashMap<String, Long>();
            for (var a : book.myAccounts()) initial.put(a.number(), a.balance());
            return Map.copyOf(initial);
        });
    }
    private String withdrawalNumber() {
        return book.myAccounts().stream().filter(FinanceBook.MyAccount::withdrawal).findFirst().orElseThrow().number();
    }
    public long of(String userId) { return snapshot(userId).get(withdrawalNumber()); }

    /** 계좌 원본은 FinanceBook만 읽는다. 화면에는 별칭·이력 없이 표시용 값과 고객별 현재 잔액만 보낸다. */
    public Overview accounts(String userId) {
        var values = snapshot(userId);
        var accounts = book.myAccounts().stream().map(a -> new Account(a.name(), a.number(),
            values.get(a.number()), a.withdrawal())).toList();
        long total = 0;
        for (var a : accounts) total = Math.addExact(total, a.balance());
        return new Overview(accounts, total);
    }

    /** PlanService가 재검사한 목록만 받는다. 잔액 재확인·출금·내 계좌 입금을 한 번에 반영한다(2026-10-27 사용자 승인). */
    synchronized long transfer(String userId, long amount, List<PlanService.Task> tasks) {
        var next = new HashMap<>(snapshot(userId));
        String source = withdrawalNumber();
        long balance = next.get(source);
        if (amount > balance) throw new ApiException(409, "INSUFFICIENT_BALANCE", PlanService.BLOCKED_MESSAGE);
        next.put(source, balance - amount);
        for (var t : tasks) if (t.type().equals("OWN_TRANSFER")) {
            String number = book.find(t.target()).orElseThrow().number();
            next.put(number, Math.addExact(next.get(number), t.amount()));
        }
        current.put(userId, Map.copyOf(next));
        return balance - amount;
    }
}
