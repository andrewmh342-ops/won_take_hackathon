package com.wontake.assist;

import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** SPEC R5 목표 중심 판단: 블록 완료와 목표 달성을 분리한다. 고객 A: card-1 신용, card-2 트래블, card-3 체크(작업 34부터 모두 해외결제 꺼짐으로 시작). */
class GoalCheckTest {
    private final DemoStore store = new DemoStore();
    private final ProfileStore profiles = new ProfileStore(store);
    private ProfileStore.CustomerState a() { return profiles.state("a", "sa"); }
    private GoalCheck.GoalStatus payment(Set<String> lost) {
        return GoalCheck.evaluate(a(), List.of("PAYMENT_ABROAD"), lost, "JPY", null).getFirst();
    }

    @Test void 재발급_신청은_해외_결제_수단_확보가_아니다() {
        for (var ref : List.of("card-1", "card-2", "card-3")) a().card(ref).status = "STOPPED";
        a().card("card-2").reissued = true;
        var s = payment(Set.of("card-2"));
        assertEquals("UNSECURED", s.status());
        assertTrue(s.detail().contains("수령 대기"));
    }

    @Test void 해외결제가_켜진_사용_중인_다른_카드가_있으면_확보() {
        a().card("card-1").overseasPayment = true; // 작업 34: 기본값이 꺼짐이라 켠 상태를 만든다
        var s = payment(Set.of("card-2"));
        assertEquals("SECURED", s.status());
        assertTrue(s.detail().contains("4821"));
    }

    @Test void 확인한_분실_카드는_아직_정지_전이어도_수단으로_세지_않는다() {
        a().card("card-3").status = "STOPPED";
        assertEquals("NEEDS_SETUP", payment(Set.of("card-1")).status()); // card-1은 분실, card-2는 해외결제 꺼짐
    }

    @Test void 사용_중인_카드는_있으나_해외결제가_꺼져_있으면_설정_필요() {
        a().card("card-1").overseasPayment = false;
        a().card("card-3").overseasPayment = false;
        assertEquals("NEEDS_SETUP", payment(Set.of("card-2")).status());
    }

    @Test void 사용_가능한_카드가_없으면_미확보() {
        for (var ref : List.of("card-1", "card-2", "card-3")) a().card(ref).status = "STOPPED";
        var s = payment(Set.of());
        assertEquals("UNSECURED", s.status());
        assertTrue(s.detail().contains("사용 가능한 카드가 없어요"));
    }

    @Test void 출국까지_남은_날을_함께_알린다() {
        for (var ref : List.of("card-1", "card-2", "card-3")) a().card(ref).status = "STOPPED";
        a().card("card-2").reissued = true;
        var s = GoalCheck.evaluate(a(), List.of("PAYMENT_ABROAD"), Set.of("card-2"), "JPY", 1).getFirst();
        assertTrue(s.detail().contains("출국까지 1일"));
    }

    @Test void 분실_카드_보호는_확인한_카드가_모두_정지되면_확보() {
        var before = GoalCheck.evaluate(a(), List.of("CARD_PROTECTION"), Set.of("card-1", "card-2"), "", null).getFirst();
        assertEquals("PENDING", before.status());
        a().card("card-1").status = "STOPPED"; a().card("card-2").status = "STOPPED";
        assertEquals("SECURED", GoalCheck.evaluate(a(), List.of("CARD_PROTECTION"), Set.of("card-1", "card-2"), "", null).getFirst().status());
    }

    @Test void 현지_현금은_그_통화_환전_신청이_있으면_확보() {
        assertEquals("NOT_READY", GoalCheck.evaluate(a(), List.of("CASH_ABROAD"), Set.of(), "JPY", null).getFirst().status());
        a().exchangeRequests.add(new ProfileStore.ExchangeRequest("ex-1", "JPY", 10000, 90500, "공항", "2026-10-28", "통장"));
        assertEquals("SECURED", GoalCheck.evaluate(a(), List.of("CASH_ABROAD"), Set.of(), "JPY", null).getFirst().status());
    }

    @Test void 기타_목표는_서버가_판정하지_않는다() {
        assertTrue(GoalCheck.evaluate(a(), List.of("OTHER"), Set.of(), "", null).isEmpty());
    }

    @Test void 블록_사전에_효과와_새_블록이_있다() {
        var catalog = new BlockCatalog();
        assertEquals(13, catalog.all().size());
        assertTrue(catalog.find("card-reissue").effect().contains("즉시 사용할 수 없"));
        assertEquals("3~5영업일", catalog.find("card-reissue").leadTime());
        assertEquals(List.of("PAYMENT_ABROAD"), catalog.find("overseas-payment-check").achieves());
        assertTrue(catalog.find("fx-rate-check").achieves().isEmpty());
        assertEquals("INPUT", catalog.find("ask-info").type());
        assertEquals("CARD", catalog.find("alt-payment-check").target());
        assertTrue(catalog.forAi().stream().allMatch(b -> b.effect() != null && !b.effect().isBlank()));
    }
}
