package com.wontake.plan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 데모 고객의 계좌는 finance.json 한 곳이 주인이다. 화면(frontend/src/data/accounts.js)도 같은 값을 쓴다. */
class FinanceBookTest {
    private final FinanceBook book = new FinanceBook();

    @Test void 출금_가능_잔액은_출금_계좌_잔액이다() {
        assertEquals(2_450_000L, book.balance());
    }
    @Test void 내_적금은_옮길_곳이고_출금_계좌는_받는_곳이_아니다() {
        assertEquals("OWN", book.find("WON 차곡차곡 적금").orElseThrow().kind());
        assertTrue(book.find("WON 생활통장").isEmpty());
    }
}
