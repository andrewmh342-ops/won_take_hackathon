package com.wontake.assist;

import com.wontake.api.ApiException;
import com.wontake.assist.ProfileStore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/**
 * R5 업무 화면의 처리(가상 상태 변경)와 화면용 상태. 실제 카드·결제·환전·보험과 연결되지 않는다.
 * 화면과 플랜은 같은 상태를 쓰므로 처리 후 열린 플랜 블록 상태를 다시 계산한다.
 */
@Service
public class DemoActionService {
    public static final long EXCHANGE_LIMIT_KRW = 2_000_000L;
    static final long MAX_FOREIGN = 100_000_000L; // 계산 넘침 방지용 입력 상한
    static final Set<String> RECEIVE = Set.of("MAIL", "BRANCH");
    private final ProfileStore profiles;
    private final PlanService plans;
    private final Clock clock;

    @Autowired public DemoActionService(ProfileStore profiles, PlanService plans) { this(profiles, plans, Clock.system(ZoneId.of("Asia/Seoul"))); }
    public DemoActionService(ProfileStore profiles, PlanService plans, Clock clock) { this.profiles = profiles; this.plans = plans; this.clock = clock; }

    /** 원화 = ⌊외화 × 환율 ÷ 기준 단위⌋ (원 미만 절사, SPEC R5). */
    public static long toKrw(long amount, Rate rate) { return Math.floorDiv(amount * rate.krw(), rate.unit()); }
    public static boolean overLimit(long krw) { return krw > EXCHANGE_LIMIT_KRW; }

    public Map<String, Object> act(String userId, String sessionId, Map<String, Object> body) {
        var state = profiles.state(userId, sessionId);
        String action = text(body, "action");
        // 작업 23: 플랜에서 넘어온 카드 처리는 고객이 분실 카드를 확인한 뒤에만 한다(메뉴로 직접 들어오면 고객이 고른 카드라 그대로 처리).
        String planId = text(body, "planId");
        if (!planId.isEmpty() && Set.of("CARD_STOP", "CARD_REISSUE", "SIMPLE_PAY_UNLINK", "SUBSCRIPTION").contains(action)
            && plans.awaitingCardConfirm(userId, sessionId, planId)) throw PlanService.unconfirmed();
        synchronized (state) {
            switch (action) {
                case "CARD_STOP" -> card(state, body).status = "STOPPED";
                case "CARD_REISSUE" -> {
                    var c = card(state, body);
                    if (!"STOPPED".equals(c.status)) throw new ApiException(409, "CARD_NOT_STOPPED", "먼저 분실신고를 해 주세요.");
                    c.reissued = true;
                }
                case "SUBSCRIPTION" -> subscription(state, card(state, body), body.get("choices"), text(body, "toRef"));
                case "SIMPLE_PAY_UNLINK" -> card(state, body).simplePay = false;
                case "OVERSEAS" -> {
                    var c = card(state, body);
                    if (!(body.get("on") instanceof Boolean on)) throw ApiException.invalid("켜기·끄기를 확인해 주세요.");
                    c.overseasPayment = on;
                }
                case "TRAVEL_CARD_APPLY" -> travelCard(state, body);
                case "EXCHANGE_APPLY" -> exchange(state, body);
                default -> throw ApiException.invalid("처리할 동작을 확인해 주세요.");
            }
            plans.refreshAll(state);
            return view(state);
        }
    }

    public Map<String, Object> state(String userId, String sessionId) {
        var state = profiles.state(userId, sessionId);
        synchronized (state) { return view(state); }
    }

    private void subscription(CustomerState state, CardState card, Object raw, String toRef) {
        if (!(raw instanceof Map<?, ?> choices) || choices.isEmpty()) throw ApiException.invalid("처리할 구독을 골라 주세요.");
        var picked = new LinkedHashMap<AutoPayment, String>();
        for (var e : choices.entrySet()) {
            var ap = CustomerState.subscription(card, String.valueOf(e.getKey()));
            if (ap == null) throw ApiException.invalid("구독을 확인해 주세요.");
            String choice = String.valueOf(e.getValue());
            if (!Set.of("SWITCH", "CANCEL").contains(choice)) throw ApiException.invalid("다른 카드로 변경 또는 해지를 골라 주세요.");
            picked.put(ap, choice);
        }
        state.moveSubscriptions(card, picked, toRef);
    }

    private void travelCard(CustomerState state, Map<String, Object> body) {
        var account = option(profiles.accounts(), text(body, "account"), "출금 계좌를 골라 주세요.");
        String receive = text(body, "receive");
        if (!RECEIVE.contains(receive)) throw ApiException.invalid("수령 방법을 골라 주세요.");
        if (state.hasUsableTravelCard()) throw new ApiException(409, "TRAVEL_CARD_EXISTS", "이미 쓸 수 있는 트래블카드가 있어요.");
        if (state.travelCardApplication != null) return; // 이미 신청: 같은 결과
        state.travelCardApplication = new TravelCardApplication(account.name(), "MAIL".equals(receive) ? "우편 수령" : "영업점 수령",
            LocalDate.now(clock).toString(), "발급 대기(가상)");
    }

    private void exchange(CustomerState state, Map<String, Object> body) {
        String currency = text(body, "currency").toUpperCase(Locale.ROOT);
        var rate = profiles.rates().get(currency);
        if (rate == null) throw ApiException.invalid("환전할 통화를 골라 주세요.");
        long amount = wholeNumber(body.get("amount"));
        var branch = option(profiles.branches(), text(body, "branch"), "수령 지점을 골라 주세요.");
        var account = option(profiles.accounts(), text(body, "account"), "출금 계좌를 골라 주세요.");
        LocalDate date;
        try { date = LocalDate.parse(text(body, "date")); } catch (DateTimeException e) { throw ApiException.invalid("수령일을 확인해 주세요."); }
        var today = LocalDate.now(clock);
        if (!date.isAfter(today) || date.isAfter(today.plusDays(30))) throw ApiException.invalid("수령일은 내일부터 30일 뒤까지 고를 수 있어요.");
        long krw = toKrw(amount, rate);
        if (overLimit(krw)) throw new ApiException(400, "EXCHANGE_LIMIT", "1회 환전은 200만 원까지예요(가상 한도).");
        state.exchangeRequests.add(new ExchangeRequest("ex-" + (state.exchangeRequests.size() + 1), currency, amount, krw, branch.name(), date.toString(), account.name()));
    }

    private Map<String, Object> view(CustomerState s) {
        var out = new LinkedHashMap<String, Object>();
        out.put("profileId", s.profileId); out.put("label", s.label);
        var cards = new ArrayList<Map<String, Object>>();
        for (var c : s.cards) {
            var m = new LinkedHashMap<String, Object>();
            m.put("ref", c.ref); m.put("name", c.name); m.put("kind", c.kind); m.put("last4", c.last4); m.put("display", c.display());
            m.put("travel", c.travel); m.put("status", c.status); m.put("reissued", c.reissued); m.put("usable", c.usable());
            m.put("simplePay", c.simplePay); m.put("simplePayName", c.simplePayName); m.put("overseasPayment", c.overseasPayment);
            var subs = new ArrayList<Map<String, Object>>();
            for (var a : c.autoPayments) subs.add(Map.of("id", a.id, "category", a.category, "merchant", a.merchant, "amount", a.amount, "billingDay", a.billingDay,
                "status", a.status, "movedTo", a.movedTo, "movedFrom", a.movedFrom));
            m.put("subscriptions", subs);
            cards.add(m);
        }
        out.put("cards", cards);
        out.put("hasTravelCard", s.hasTravelCard);
        out.put("hasUsableTravelCard", s.hasUsableTravelCard());
        out.put("travelCardApplication", s.travelCardApplication);
        var rates = new ArrayList<Map<String, Object>>();
        profiles.rates().forEach((code, r) -> rates.add(Map.of("code", code, "name", r.name(), "unit", r.unit(), "krw", r.krw())));
        out.put("fxRates", rates);
        out.put("exchangeRequests", List.copyOf(s.exchangeRequests));
        out.put("accounts", profiles.accounts());
        out.put("branches", profiles.branches());
        out.put("address", profiles.address());
        out.put("exchangeLimit", EXCHANGE_LIMIT_KRW);
        out.put("today", LocalDate.now(clock).toString());
        out.put("plans", plans.progressSummary(s));
        return out;
    }

    private static CardState card(CustomerState s, Map<String, Object> body) {
        var c = s.card(text(body, "ref"));
        if (c == null) throw ApiException.invalid("카드를 확인해 주세요.");
        return c;
    }
    private static Option option(List<Option> options, String id, String message) {
        return options.stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow(() -> ApiException.invalid(message));
    }
    private static String text(Map<String, Object> body, String key) {
        return body.get(key) instanceof String v ? v.trim() : "";
    }
    /** 외화 금액은 1 이상 정수(숫자)만 받는다. 문자열·소수·0·음수는 400. */
    private static long wholeNumber(Object raw) {
        if (!(raw instanceof Integer || raw instanceof Long || raw instanceof java.math.BigInteger)) throw ApiException.invalid("외화 금액은 1 이상 정수로 입력해 주세요.");
        long v = ((Number) raw).longValue();
        if (v < 1 || v > MAX_FOREIGN) throw ApiException.invalid("외화 금액은 1 이상 정수로 입력해 주세요.");
        return v;
    }
}
