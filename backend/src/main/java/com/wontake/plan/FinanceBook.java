package com.wontake.plan;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/**
 * 데모 고객 한 명의 내 계좌와 받는 곳 목록(data/finance.json). 실제 계좌가 아니며, 작업 목록을 만들 때 읽기만 한다.
 * 이 파일이 주인이다. 화면의 계좌와 잔액(frontend/src/data/accounts.js)은 accounts.test.js가 이 파일과 대조한다.
 */
@Component
public class FinanceBook {
    /** kind: OWN은 내 계좌, CONTACT는 보낸 적 있는 받는 사람. history는 그 계좌로 보낸 금액(원)이고 최근이 뒤다. */
    public record Account(String name, String kind, List<String> aliases, String bank, String number, List<Long> history) {}
    /** withdrawal이 true인 계좌 하나에서 돈이 나간다. 나머지 내 계좌는 옮길 곳(OWN)이 된다. */
    public record MyAccount(String name, String number, long balance, boolean withdrawal, List<String> aliases, List<Long> history) {}
    public record Contact(String name, List<String> aliases, String bank, String number, List<Long> history) {}
    public record Data(List<MyAccount> myAccounts, List<Contact> contacts) {}
    public static final String MY_BANK = "WON";
    private final long balance;
    private final List<Account> accounts;
    public FinanceBook() {
        try (var input = getClass().getResourceAsStream("/data/finance.json")) {
            if (input == null) throw new IllegalStateException("가상 계좌 데이터가 없습니다.");
            var data = new JsonMapper().readValue(input, Data.class);
            var withdrawal = data.myAccounts().stream().filter(MyAccount::withdrawal).toList();
            if (withdrawal.size() != 1) throw new IllegalStateException("출금 계좌는 하나여야 합니다.");
            balance = withdrawal.getFirst().balance();
            var all = new ArrayList<Account>();
            for (var m : data.myAccounts()) if (!m.withdrawal()) all.add(new Account(m.name(), "OWN", m.aliases(), MY_BANK, m.number(), m.history()));
            for (var c : data.contacts()) all.add(new Account(c.name(), "CONTACT", c.aliases(), c.bank(), c.number(), c.history()));
            accounts = List.copyOf(all);
            if (accounts.size() != accounts.stream().map(Account::name).distinct().count()) throw new IllegalStateException("중복 계좌 이름");
            for (var a : accounts)
                if (a.aliases() == null || a.number() == null || a.number().length() < 4
                    || a.history() == null || a.history().isEmpty() || a.history().stream().anyMatch(v -> v == null || v <= 0))
                    throw new IllegalStateException("가상 계좌 확인: " + a.name());
        } catch (Exception e) { throw new IllegalStateException("가상 계좌 데이터를 읽지 못했습니다.", e); }
    }
    /** 출금 계좌의 잔액이다. 작업 목록의 예상 잔액은 여기서 뺀다. */
    public long balance() { return balance; }
    public List<Account> accounts() { return accounts; }
    public Optional<Account> find(String name) { return accounts.stream().filter(a -> a.name().equals(name)).findFirst(); }
}
