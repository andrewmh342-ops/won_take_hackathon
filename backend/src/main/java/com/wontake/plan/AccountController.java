package com.wontake.plan;

import org.springframework.web.bind.annotation.*;

/** 사용자 승인 범위: 홈·계좌조회·이체 화면에서 서버의 고객별 가상 잔액을 읽는다. AI는 호출하지 않는다. */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final Balances balances;
    public AccountController(Balances balances) { this.balances = balances; }
    @GetMapping public Balances.Overview accounts(@RequestParam String userId) {
        return balances.accounts(userId);
    }
}
