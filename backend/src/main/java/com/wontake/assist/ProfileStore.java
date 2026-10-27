package com.wontake.assist;

import com.wontake.ai.AiClient.CardShape;
import com.wontake.ai.AiClient.CustomerShape;
import com.wontake.ai.AiClient.TripShape;
import com.wontake.api.ApiException;
import com.wontake.session.DemoStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** R5 시연 고객 A·B의 가상 상태(세션 메모리). 실제 카드·결제와 연결되지 않는다. */
@Component
public class ProfileStore {
    public record Rate(long unit, long krw, String name) {}
    public record Option(String id, String name) {}
    public static class AutoPayment {
        public final String id, category, merchant;
        public final long amount;
        public final int billingDay;
        public String status = "ACTIVE"; // ACTIVE | SWITCHED | CANCELLED
        /** 작업 24: 다른 카드로 옮긴 건은 원래 카드에 SWITCHED + movedTo, 옮긴 카드에 ACTIVE + movedFrom으로 남는다. */
        public String movedTo = "", movedFrom = "";
        AutoPayment(String id, String category, String merchant, long amount, int billingDay) {
            this.id=id; this.category=category; this.merchant=merchant; this.amount=amount; this.billingDay=billingDay;
        }
    }
    /** 환전 신청 1건(가상). 금액은 원 단위 정수. */
    public static class ExchangeRequest {
        public final String id, currency, branch, date, account;
        public final long amount, krw;
        ExchangeRequest(String id, String currency, long amount, long krw, String branch, String date, String account) {
            this.id=id; this.currency=currency; this.amount=amount; this.krw=krw; this.branch=branch; this.date=date; this.account=account;
        }
    }
    public record TravelCardApplication(String account, String receive, String appliedOn, String status) {}
    public static class CardState {
        public final String ref, name, kind, last4;
        public final boolean travel;
        public boolean simplePay, overseasPayment, reissued;
        public final String simplePayName; // 등록된 간편결제 이름(없으면 "")
        public String status = "ACTIVE"; // ACTIVE | STOPPED
        public final List<AutoPayment> autoPayments = new ArrayList<>();
        CardState(String ref, String name, String kind, String last4, boolean travel, String simplePayName, boolean overseasPayment) {
            this.ref=ref; this.name=name; this.kind=kind; this.last4=last4; this.travel=travel; this.simplePayName=simplePayName;
            this.simplePay=!simplePayName.isEmpty(); this.overseasPayment=overseasPayment;
        }
        /** 지금 결제에 쓸 수 있는지(R5 목표 중심: 보유와 구분). 정지·재발급 대기 카드는 쓸 수 없다. */
        public boolean usable() { return "ACTIVE".equals(status); }
        /** 화면에만 쓰는 복원 이름. AI에는 보내지 않는다. */
        public String display() { return name + " 끝자리 " + last4 + "(가상)"; }
    }
    public static class CustomerState {
        public final String profileId, label;
        public final boolean hasTravelCard;
        public final List<CardState> cards;
        final Map<String, Plan> plans = new LinkedHashMap<>();
        public final List<ExchangeRequest> exchangeRequests = new ArrayList<>();
        public TravelCardApplication travelCardApplication;
        /** 정보 확인 블록으로 받은 여행 정보(R5 목표 중심). 없으면 null. */
        public String tripCountry;
        public java.time.LocalDate tripDepartureDate;
        CustomerState(String profileId, String label, List<CardState> cards) {
            this.profileId=profileId; this.label=label; this.cards=cards;
            this.hasTravelCard=cards.stream().anyMatch(c -> c.travel);
        }
        public CardState card(String ref) { return cards.stream().filter(c -> c.ref.equals(ref)).findFirst().orElse(null); }
        public CardState travelCard() { return cards.stream().filter(c -> c.travel).min(Comparator.comparing(c -> !c.usable())).orElse(null); }
        public boolean hasUsableTravelCard() { return cards.stream().anyMatch(c -> c.travel && c.usable()); }
        /** 같은 id가 옮겨 다녔으면 아직 처리하지 않은 건을 먼저 찾는다. */
        public static AutoPayment subscription(CardState card, String id) {
            return card.autoPayments.stream().filter(a -> a.id.equals(id)).min(Comparator.comparing(a -> !"ACTIVE".equals(a.status))).orElse(null);
        }
        /**
         * 작업 24: 고른 구독을 고객이 고른 다른 사용 중 보유 카드로 옮기거나 해지한다(재발급을 기다리지 않음).
         * 모두 검사한 뒤 바꾼다(일부만 바뀌지 않게). 이미 처리한 건은 그대로 둔다.
         */
        public void moveSubscriptions(CardState from, Map<AutoPayment, String> picked, String toRef) {
            boolean switching = picked.entrySet().stream().anyMatch(e -> "SWITCH".equals(e.getValue()) && "ACTIVE".equals(e.getKey().status));
            CardState to = null;
            if (switching) {
                if (toRef == null || toRef.isBlank()) throw ApiException.invalid("옮길 카드를 골라 주세요.");
                to = card(toRef.trim());
                if (to == null || to == from || !to.usable()) throw ApiException.invalid("지금 쓸 수 있는 다른 카드를 골라 주세요.");
            }
            for (var e : picked.entrySet()) {
                var ap = e.getKey();
                if (!"ACTIVE".equals(ap.status)) continue;
                if (!"SWITCH".equals(e.getValue())) { ap.status = "CANCELLED"; continue; }
                ap.status = "SWITCHED";
                ap.movedTo = to.ref;
                var moved = new AutoPayment(ap.id, ap.category, ap.merchant, ap.amount, ap.billingDay);
                moved.movedFrom = from.ref;
                to.autoPayments.add(moved);
            }
        }
    }

    private final DemoStore store;
    private final JsonNode profiles;
    private final Map<String, Rate> rates = new LinkedHashMap<>();
    private final List<Option> accounts = new ArrayList<>(), branches = new ArrayList<>();
    private final String address;
    private final Map<String, CustomerState> states = new ConcurrentHashMap<>();

    private final Clock clock;
    @Autowired public ProfileStore(DemoStore store) { this(store, Clock.system(ZoneId.of("Asia/Seoul"))); }
    public ProfileStore(DemoStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
        try (var input = getClass().getResourceAsStream("/data/profiles.json")) {
            if (input == null) throw new IllegalStateException("시연 고객 데이터가 없습니다.");
            var root = new JsonMapper().readTree(input);
            profiles = root.path("profiles");
            for (var e : root.path("fxRates").properties())
                rates.put(e.getKey(), new Rate(e.getValue().path("unit").asLong(), e.getValue().path("krw").asLong(), e.getValue().path("name").asText()));
            for (var a : root.path("accounts")) accounts.add(new Option(a.path("id").asText(), a.path("name").asText() + " " + a.path("masked").asText()));
            for (var b : root.path("branches")) branches.add(new Option(b.path("id").asText(), b.path("name").asText()));
            address = root.path("address").asText();
        } catch (Exception e) { throw new IllegalStateException("시연 고객 데이터를 읽지 못했습니다.", e); }
    }

    public Map<String, Rate> rates() { return rates; }
    public LocalDate today() { return LocalDate.now(clock); }
    /** 출국까지 남은 날 수(출국일을 모르면 null). AI에는 실제 날짜 대신 이 값만 보낸다. */
    public Integer daysUntilDeparture(CustomerState s) {
        return s.tripDepartureDate == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(today(), s.tripDepartureDate);
    }
    public List<Option> accounts() { return accounts; }
    public List<Option> branches() { return branches; }
    public String address() { return address; }

    /** 세션의 가상 고객 상태. 처음이면 고객 A로 시작한다. 세션 주인 확인은 DemoStore가 한다. */
    public CustomerState state(String userId, String sessionId) {
        store.session(userId, sessionId);
        if (states.size() > 5000) states.clear(); // 시연 서버 메모리 보호
        return states.computeIfAbsent(sessionId, k -> create("A"));
    }
    public void select(String userId, String sessionId, String profileId) {
        if (profileId == null || !profiles.has(profileId)) throw ApiException.invalid("시연 고객은 A·B·C 중에서 골라 주세요.");
        store.session(userId, sessionId);
        states.put(sessionId, create(profileId)); // 상태와 플랜을 함께 초기화
    }
    /** 가명화된 모양: ref·종류·여부·자동결제 분류만. */
    public CustomerShape shape(String userId, String sessionId) {
        var s = state(userId, sessionId);
        synchronized (s) {
            var cards = s.cards.stream().map(c -> new CardShape(c.ref, c.kind, c.travel, c.simplePay, c.overseasPayment, c.status,
                c.autoPayments.stream().filter(a -> "ACTIVE".equals(a.status)).map(a -> a.category).toList(),
                c.usable(), c.reissued ? "REQUESTED" : "NONE")).toList();
            return new CustomerShape(cards, s.hasTravelCard, List.copyOf(rates.keySet()), s.hasUsableTravelCard(),
                new TripShape(s.tripCountry, daysUntilDeparture(s)));
        }
    }

    private CustomerState create(String profileId) {
        var p = profiles.path(profileId);
        var cards = new ArrayList<CardState>();
        for (var c : p.path("cards")) {
            var card = new CardState(c.path("ref").asText(), c.path("name").asText(), c.path("kind").asText(), c.path("last4").asText(),
                c.path("travel").asBoolean(), c.path("simplePay").asText(""), c.path("overseasPayment").asBoolean());
            for (var a : c.path("autoPayments"))
                card.autoPayments.add(new AutoPayment(a.path("id").asText(), a.path("category").asText(), a.path("merchant").asText(),
                    a.path("amount").asLong(), a.path("billingDay").asInt()));
            cards.add(card);
        }
        return new CustomerState(profileId, p.path("label").asText(), List.copyOf(cards));
    }
}
