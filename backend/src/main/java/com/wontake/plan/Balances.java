package com.wontake.plan;

import com.wontake.api.ApiException;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 고객별 가상 생활통장(출금 계좌) 잔액. finance.json의 잔액에서 시작해 승인한 작업 목록만큼 줄인다.
 * 서버 메모리라 재시작하면 처음 잔액으로 돌아간다(2026-10-27 사용자 결정). 실제 돈은 움직이지 않는다.
 * 화면의 계좌 잔액(frontend/src/data/accounts.js)은 아직 고정값이라 승인 뒤에도 그대로 보인다.
 */
@Component
public class Balances {
    private final FinanceBook book;
    private final Map<String, Long> current = new ConcurrentHashMap<>();
    public Balances(FinanceBook book) { this.book = book; }

    public long of(String userId) { return current.computeIfAbsent(userId, id -> book.balance()); }

    /** 잔액 확인과 빼기를 한 번에 해서, 같은 고객의 두 목록을 동시에 승인해도 잔액이 음수가 되지 않게 한다. 남은 잔액을 돌려준다. */
    synchronized long withdraw(String userId, long amount) {
        long balance = of(userId);
        if (amount > balance) throw new ApiException(409, "INSUFFICIENT_BALANCE", PlanService.BLOCKED_MESSAGE);
        current.put(userId, balance - amount);
        return balance - amount;
    }
}
