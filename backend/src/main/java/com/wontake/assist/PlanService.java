package com.wontake.assist;

import com.wontake.ai.AiClient;
import com.wontake.ai.AiClient.AiPlan;
import com.wontake.ai.AiClient.BlockInfo;
import com.wontake.ai.AiClient.CustomerShape;
import com.wontake.api.ApiException;
import com.wontake.assist.BlockCatalog.Block;
import com.wontake.assist.Plan.PlanBlock;
import com.wontake.assist.ProfileStore.CardState;
import com.wontake.assist.ProfileStore.CustomerState;
import com.wontake.session.DemoStore;
import com.wontake.session.UsageGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.text.NumberFormat;
import java.util.*;

/**
 * R5 플랜 검증·복원·실행. 순서는 AI가 정하고, 서버는 사전·보유·선후 관계·보호 우선·통화·개수·길이만 검사한다(고정 순서 없음).
 * 블록 완료와 목표 달성(GoalCheck)을 따로 계산한다. 실행은 가상 고객 상태만 바꾼다.
 */
@Service
public class PlanService {
    static final int MAX_BLOCKS = 12, MAX_REASON = 120, MAX_UNHANDLED = 100, MAX_PLANS = 5;
    static final String HANDOFF = "counselor-handoff";
    /** 카드 분실 플랜을 이루는 블록(분실 카드 확인 대상). */
    static final Set<String> LOSS_BLOCKS = Set.of("card-lost-report", "simple-pay-unlink", "card-reissue", "auto-payment-switch");
    /** 작업 35: 플랜에 통화 블록이 들어가면 그 나라를 여행 나라로 기억한다(다음 질문에서 다시 묻지 않게). */
    static final Map<String, String> CURRENCY_COUNTRY = Map.of("JPY", "일본", "USD", "미국", "EUR", "유럽", "CNY", "중국");
    static final Set<String> GOAL_TYPES = Set.of("CARD_PROTECTION", "PAYMENT_ABROAD", "CASH_ABROAD", "OTHER");
    private static final Map<String, String> SKIP_REASON = Map.of(
        "CARD_STOPPED", "이미 정지된 카드예요", "NO_SIMPLE_PAY", "간편결제에 등록되지 않은 카드예요", "REISSUED", "이미 재발급을 신청했어요",
        "NO_AUTO_PAYMENTS", "이 카드에 걸린 자동결제가 없어요", "OVERSEAS_ON", "이미 해외결제가 켜져 있어요",
        "HAS_USABLE_TRAVEL_CARD", "쓸 수 있는 트래블카드가 있어 새로 신청하지 않아요", "LOST_OR_STOPPED", "지금 쓸 수 없는 카드예요");
    private record Item(Block block, String ref, String param, String reason, boolean adjusted, String priority) {
        Item withPriority(String p) { return new Item(block, ref, param, reason, adjusted, p); }
        Item adjust() { return new Item(block, ref, param, reason, true, priority); }
        boolean same(String id, String r, String p) { return block.id().equals(id) && ref.equals(r) && param.equals(p); }
    }
    private final DemoStore store;
    private final BlockCatalog catalog;
    private final ProfileStore profiles;
    private final UsageGuard guard;
    private final Redactor redactor;

    public PlanService(DemoStore store, BlockCatalog catalog, ProfileStore profiles) { this(store, catalog, profiles, new UsageGuard()); }
    @Autowired public PlanService(DemoStore store, BlockCatalog catalog, ProfileStore profiles, UsageGuard guard) {
        this.store = store; this.catalog = catalog; this.profiles = profiles; this.guard = guard;
        this.redactor = new Redactor(profiles);
    }

    public CustomerShape shape(String userId, String sessionId) { return profiles.shape(userId, sessionId); }
    /** 작업 25: AI 입력 가명화(같은 가상 고객 상태 기준). */
    public Redactor redactor() { return redactor; }
    public List<BlockInfo> blocksForAi() { return catalog.forAi(); }

    /** AI 플랜을 검증해 저장한다. 남은 블록이 없으면 null(일반 안내로 처리). */
    public Plan build(String userId, String sessionId, AiPlan ai) {
        if (ai == null || ai.blocks() == null) return null;
        var state = profiles.state(userId, sessionId);
        var aiInput = profiles.shape(userId, sessionId);
        synchronized (state) {
            var plan = new Plan();
            plan.planId = UUID.randomUUID().toString();
            plan.aiInput = aiInput;
            plan.source = ai;
            assemble(plan, state, ai, null);
            if (plan.blocks.isEmpty()) return null;
            state.plans.put(plan.planId, plan);
            while (state.plans.size() > MAX_PLANS) state.plans.remove(state.plans.keySet().iterator().next());
            return plan;
        }
    }

    /**
     * AI 플랜을 검증·조립해 plan에 채운다. confirmed가 있으면(고객이 분실 카드를 확인함) 분실 블록은 그 카드에만 둔다.
     * 서버는 순서를 새로 정하지 않고 사전·보유·선후 관계·보호 우선·개수·길이만 검사한다. 뺀 업무는 이유와 함께 남긴다.
     */
    void assemble(Plan plan, CustomerState state, AiPlan ai, List<String> confirmed) {
        var unhandled = new LinkedHashSet<String>();
        for (var u : Objects.requireNonNullElse(ai.unhandled(), List.<String>of()))
            if (u != null && !u.isBlank()) unhandled.add(clip(names(state, u.trim()), MAX_UNHANDLED));
        var skipped = new ArrayList<Map<String, Object>>();
        var aiBlocks = Objects.requireNonNullElse(ai.blocks(), List.<AiClient.AiBlock>of());
        var hint = new LinkedHashSet<String>();
        for (var b : aiBlocks)
            if (b != null && "card-lost-report".equals(b.blockId()) && state.card(Objects.toString(b.targetRef(), "").trim()) != null) hint.add(b.targetRef().trim());
        Set<String> lost = confirmed != null ? new LinkedHashSet<>(confirmed) : hint;
        var previous = plan.blocks == null ? List.<PlanBlock>of() : plan.blocks;
        // 재판단: 이전 플랜에서 끝낸 업무는 이미 충족돼도 빼지 않고 그대로 남긴다(고객이 한 일이 사라지지 않게).
        var finished = previous.stream().filter(o -> !"PENDING".equals(o.status)).toList();
        var items = new ArrayList<Item>();
        for (var b : aiBlocks) {
            if (b == null) continue;
            var block = catalog.find(b.blockId());
            if (block == null) { skipped.add(skip(Objects.toString(b.blockId(), "?"), Objects.toString(b.blockId(), "?"), "", "앱에서 지원하지 않는 업무예요")); continue; }
            String ref = block.cardTarget() ? Objects.toString(b.targetRef(), "").trim() : "";
            var card = state.card(ref);
            if (block.cardTarget() && card == null) { skipped.add(skip(block.id(), block.name(), ref, "보유하지 않은 카드예요")); continue; }
            if (confirmed != null && LOSS_BLOCKS.contains(block.id()) && !confirmed.contains(ref)) { skipped.add(skip(block.id(), block.name(), card.display(), "확인한 분실 카드가 아니에요")); continue; }
            if (Set.of("alt-payment-check", "overseas-payment-check").contains(block.id()) && lost.contains(ref)) { skipped.add(skip(block.id(), block.name(), card.display(), "잃어버린 카드예요")); continue; }
            String param = block.currencyParam() || block.infoParam() ? Objects.toString(b.param(), "").trim().toUpperCase(Locale.ROOT) : "";
            if (block.infoParam() && !Set.of("DEPARTURE_DATE", "COUNTRY").contains(param)) { skipped.add(skip(block.id(), block.name(), "", "묻는 정보 종류를 확인하지 못했어요")); continue; }
            if (block.currencyParam() && !profiles.rates().containsKey(param)) {
                unhandled.add(clip((param.isEmpty() ? "여행 나라" : param) + " 통화 환율·환전 안내(시연 지원 통화 아님)", MAX_UNHANDLED));
                skipped.add(skip(block.id(), block.name(), param, "시연에서 지원하지 않는 통화예요"));
                continue;
            }
            final String fp = param;
            boolean wasDone = finished.stream().anyMatch(o -> o.blockId.equals(block.id()) && o.targetRef.equals(ref) && o.param.equals(fp));
            if (!wasDone && skip(block, card, state)) { skipped.add(skip(block.id(), block.name(), card == null ? "" : card.display(), SKIP_REASON.getOrDefault(block.skipWhen(), "이미 처리됐어요"))); continue; }
            if (!wasDone && block.infoParam() && knownInfo(param, state)) { skipped.add(skip(block.id(), block.name(), "", "이미 확인한 정보예요")); continue; } // 다시 묻지 않는다
            if (items.stream().anyMatch(i -> i.same(block.id(), ref, fp))) continue;
            String priority = "OPTIONAL".equals(b.priority()) ? "OPTIONAL" : "REQUIRED";
            items.add(new Item(block, ref, fp, clip(names(state, Objects.toString(b.reason(), "").trim()), MAX_REASON), false, priority));
        }
        if (confirmed != null) // 확인한 분실 카드는 보호(분실신고)가 빠지지 않게 한다
            for (var ref : confirmed) {
                var card = state.card(ref);
                if (card != null && !"STOPPED".equals(card.status) && items.stream().noneMatch(i -> i.same("card-lost-report", ref, "")))
                    items.add(0, new Item(catalog.find("card-lost-report"), ref, "", "확인한 분실 카드라 사용 정지를 넣었어요.", true, "REQUIRED"));
            }
        int kept = 0;
        for (var o : finished)
            if (items.stream().noneMatch(i -> i.same(o.blockId, o.targetRef, o.param)) && catalog.find(o.blockId) != null && (o.targetRef.isEmpty() || state.card(o.targetRef) != null))
                items.add(kept++, new Item(catalog.find(o.blockId), o.targetRef, o.param, o.reason, o.adjusted, o.priority));
        if (state.tripCountry == null)
            items.stream().filter(i -> i.block.currencyParam()).map(i -> CURRENCY_COUNTRY.get(i.param)).filter(Objects::nonNull).findFirst()
                .ifPresent(country -> state.tripCountry = country);
        dedupe(items, state, lost, finished, skipped);
        departureFirst(items, finished);
        fixOrder(items, state);
        protectFirst(items);
        if (items.size() > MAX_BLOCKS) items.subList(MAX_BLOCKS, items.size()).clear();
        if (!unhandled.isEmpty() && items.stream().noneMatch(i -> i.block.id().equals(HANDOFF))) {
            if (items.size() >= MAX_BLOCKS) items.removeLast();
            items.add(new Item(catalog.find(HANDOFF), "", "", "블록으로 처리할 수 없는 일을 상담사에게 이어서 맡겨요.", false, "REQUIRED"));
        }
        plan.events = Objects.requireNonNullElse(ai.events(), List.<String>of()).stream().filter(e -> e != null && !e.isBlank()).map(e -> clip(names(state, e.trim()), MAX_UNHANDLED)).limit(5).toList();
        plan.orderReason = clip(names(state, Objects.toString(ai.orderReason(), "").trim()), MAX_REASON);
        plan.unhandled = List.copyOf(unhandled);
        plan.goals = Objects.requireNonNullElse(ai.goals(), List.<AiClient.AiGoal>of()).stream()
            .filter(g -> g != null && GOAL_TYPES.contains(g.type())).limit(4)
            .map(g -> Map.<String, Object>of("type", g.type(), "text", clip(Objects.toString(g.text(), "").trim(), 80), "required", g.required())).toList();
        plan.obstacles = Objects.requireNonNullElse(ai.obstacles(), List.<AiClient.AiObstacle>of()).stream()
            .filter(o -> o != null && o.text() != null && !o.text().isBlank()).limit(5)
            .map(o -> Map.<String, Object>of("text", clip(names(state, o.text().trim()), MAX_REASON),
                "resolution", Set.of("BLOCK", "QUESTION", "HANDOFF").contains(o.resolution()) ? o.resolution() : "BLOCK")).toList();
        plan.considered = Objects.requireNonNullElse(ai.considered(), List.<AiClient.AiConsidered>of()).stream()
            .filter(c -> c != null && c.blockId() != null).limit(8)
            .map(c -> Map.<String, Object>of("blockId", c.blockId(), "name", catalog.find(c.blockId()) == null ? c.blockId() : catalog.find(c.blockId()).name(),
                "reason", clip(names(state, Objects.toString(c.reason(), "").trim()), MAX_REASON))).toList();
        plan.skipped = List.copyOf(skipped);
        plan.suggestedCards = List.copyOf(hint);
        plan.confirmedCards = confirmed == null ? null : List.copyOf(confirmed);
        plan.needsCardConfirm = confirmed == null && items.stream().anyMatch(i -> LOSS_BLOCKS.contains(i.block.id()));
        plan.blocks = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            var it = items.get(i);
            var pb = new PlanBlock();
            pb.index = i; pb.blockId = it.block.id(); pb.name = it.block.name(); pb.type = it.block.type(); pb.menuId = it.block.menuId();
            pb.targetRef = it.ref; pb.param = it.param; pb.reason = it.reason; pb.adjusted = it.adjusted; pb.priority = it.priority;
            pb.target = it.ref.isEmpty() ? "" : state.card(it.ref).display();
            // 재판단해도 이미 열어 본 확인·안내 블록은 그대로 둔다(상태로 정해지는 블록은 refresh가 계산)
            for (var old : previous) if (old.blockId.equals(pb.blockId) && old.targetRef.equals(pb.targetRef) && old.param.equals(pb.param)) pb.status = old.status;
            plan.blocks.add(pb);
        }
        refresh(plan, state);
    }

    /**
     * 작업 23: 고객이 잃어버린 카드를 직접 확인한다. AI가 고른 분실 블록 종류를 확인한 카드마다 다시 구성하고
     * 보유 여부·선후 관계를 다시 검증한다. AI 재판단은 ChatService가 이어서 한다.
     */
    public Plan confirmCards(String userId, String sessionId, String planId, List<String> cardRefs) {
        var state = profiles.state(userId, sessionId);
        synchronized (state) {
            var plan = owned(state, planId);
            if (cardRefs == null || cardRefs.isEmpty() || cardRefs.size() > state.cards.size()) throw ApiException.invalid("잃어버린 카드를 골라 주세요.");
            var refs = new ArrayList<String>();
            for (var ref : cardRefs) {
                if (ref == null || state.card(ref) == null) throw ApiException.invalid("보유한 카드를 골라 주세요.");
                if (!refs.contains(ref)) refs.add(ref);
            }
            if (plan.confirmedCards != null) throw new ApiException(409, "PLAN_ALREADY_CONFIRMED", "이미 잃어버린 카드를 확인했어요.");
            var source = Objects.requireNonNullElse(plan.source, AiPlan.EMPTY);
            var blocks = new ArrayList<AiClient.AiBlock>();
            for (var b : source.blocks()) {
                if (b != null && LOSS_BLOCKS.contains(b.blockId())) for (var ref : refs) blocks.add(new AiClient.AiBlock(b.blockId(), ref, b.param(), b.reason(), b.priority()));
                else blocks.add(b);
            }
            var rebound = new AiPlan(source.events(), blocks, source.unhandled(), source.orderReason(), source.goals(), source.obstacles(), source.considered());
            plan.source = rebound;
            assemble(plan, state, rebound, refs);
            return plan;
        }
    }

    /** 재판단: 같은 플랜을 새 AI 판단으로 다시 조립한다. 확인한 분실 카드와 끝낸 업무(상태로 계산)는 유지된다. */
    public Plan rebuild(String userId, String sessionId, String planId, AiPlan ai) {
        var state = profiles.state(userId, sessionId);
        synchronized (state) {
            var plan = owned(state, planId);
            plan.source = ai;
            plan.aiInput = profiles.shape(userId, sessionId);
            assemble(plan, state, ai, plan.confirmedCards);
            return plan;
        }
    }

    /** 이 플랜이 분실 카드 확인을 기다리는지(화면 처리도 막는다). */
    public boolean awaitingCardConfirm(String userId, String sessionId, String planId) {
        var state = profiles.state(userId, sessionId);
        synchronized (state) {
            var plan = state.plans.get(planId);
            return plan != null && plan.needsCardConfirm;
        }
    }

    /** 블록 하나를 실행한다. 선행 블록이 끝나지 않았으면 409, 끝난 블록은 같은 결과를 돌려준다. */
    public Plan execute(String userId, String sessionId, String planId, Integer index, Map<String, String> options) {
        var state = profiles.state(userId, sessionId);
        guard.plan(sessionId);
        synchronized (state) {
            var plan = owned(state, planId);
            if (index == null || index < 0 || index >= plan.blocks.size()) throw ApiException.invalid("블록 번호를 확인해 주세요.");
            var pb = plan.blocks.get(index);
            if (!"PENDING".equals(pb.status)) return plan;
            if (plan.needsCardConfirm && LOSS_BLOCKS.contains(pb.blockId)) throw unconfirmed();
            var block = catalog.find(pb.blockId);
            for (var req : block.requires()) {
                var reqBlock = catalog.find(req);
                String ref = reqBlock.cardTarget() ? pb.targetRef : "", param = reqBlock.currencyParam() ? pb.param : "";
                if (satisfied(req, state.card(ref), state)) continue;
                var done = plan.blocks.stream().anyMatch(o -> o.blockId.equals(req) && o.targetRef.equals(ref) && o.param.equals(param) && !"PENDING".equals(o.status));
                if (!done) throw new ApiException(409, "PLAN_PREREQUISITE", "먼저 " + reqBlock.name() + object(reqBlock.name()) + " 끝내 주세요.");
            }
            var card = state.card(pb.targetRef);
            switch (pb.blockId) {
                case "card-lost-report" -> card.status = "STOPPED";
                case "simple-pay-unlink" -> card.simplePay = false;
                case "card-reissue" -> card.reissued = true;
                case "auto-payment-switch" -> {
                    // 건별 SWITCH(기본)·CANCEL, 옮길 카드는 options.toRef(작업 24: 다른 사용 중 보유 카드)
                    var chosen = Objects.requireNonNullElse(options, Map.<String, String>of());
                    var picked = new LinkedHashMap<ProfileStore.AutoPayment, String>();
                    for (var ap : card.autoPayments) if ("ACTIVE".equals(ap.status)) picked.put(ap, "CANCEL".equals(chosen.get(ap.id)) ? "CANCEL" : "SWITCH");
                    state.moveSubscriptions(card, picked, chosen.get("toRef"));
                }
                case "overseas-payment-check" -> card.overseasPayment = true;
                default -> { }
            }
            // CHECK·안내 LINK·HANDOFF는 화면을 열면 끝난다. 신청 LINK·정보 입력·ACTION은 가상 상태로 완료를 계산한다(refresh).
            if (!"ACTION".equals(pb.type) && !COMPLETED_BY_STATE.contains(pb.blockId)) pb.status = "CHECK".equals(pb.type) ? "DONE" : "VISITED";
            refresh(plan, state);
            return plan;
        }
    }

    static final int MAX_COUNTRY = 20, MAX_TRIP_DAYS = 365;
    /**
     * 작업 29: 정보 확인(ask-info) 블록에 고객이 답한다. 출국일은 오늘~365일 뒤(yyyy-MM-dd), 나라는 1~20자.
     * 가상 상태에 저장하고 블록을 완료로 계산한다. AI 재판단은 ChatService가 이어서 한다.
     */
    public Plan answer(String userId, String sessionId, String planId, Integer index, String value) {
        var state = profiles.state(userId, sessionId);
        guard.plan(sessionId);
        synchronized (state) {
            var plan = owned(state, planId);
            if (index == null || index < 0 || index >= plan.blocks.size()) throw ApiException.invalid("블록 번호를 확인해 주세요.");
            var pb = plan.blocks.get(index);
            if (!"ask-info".equals(pb.blockId)) throw ApiException.invalid("정보를 묻는 단계가 아니에요.");
            String v = Objects.toString(value, "").trim();
            switch (pb.param) {
                case "DEPARTURE_DATE" -> {
                    java.time.LocalDate date;
                    try { date = java.time.LocalDate.parse(v); } catch (java.time.DateTimeException e) { throw ApiException.invalid("출국일을 날짜로 골라 주세요."); }
                    var today = profiles.today();
                    if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_TRIP_DAYS))) throw ApiException.invalid("출국일은 오늘부터 1년 안으로 골라 주세요.");
                    state.tripDepartureDate = date;
                }
                case "COUNTRY" -> {
                    if (v.isEmpty() || v.length() > MAX_COUNTRY) throw ApiException.invalid("나라 이름을 1~" + MAX_COUNTRY + "자로 적어 주세요.");
                    state.tripCountry = v;
                }
                default -> throw ApiException.invalid("정보를 묻는 단계가 아니에요.");
            }
            refreshAll(state);
            return plan;
        }
    }

    static ApiException unconfirmed() { return new ApiException(409, "PLAN_CARD_UNCONFIRMED", "먼저 잃어버린 카드를 확인해 주세요."); }
    Plan owned(CustomerState state, String planId) {
        var plan = planId == null ? null : state.plans.get(planId);
        if (plan == null) throw new ApiException(404, "PLAN_NOT_FOUND", "플랜을 찾지 못했어요. 다시 물어봐 주세요.");
        return plan;
    }

    /** 화면에서 가상 상태를 바꾼 뒤 열린 플랜들의 블록 상태를 다시 계산한다. */
    public void refreshAll(CustomerState state) {
        synchronized (state) { for (var p : state.plans.values()) refresh(p, state); }
    }
    /** 화면 위 진행 띠용: 플랜별 필수 블록 완료 수와 목표 달성 여부. */
    public Map<String, Map<String, Object>> progressSummary(CustomerState state) {
        synchronized (state) {
            var out = new LinkedHashMap<String, Map<String, Object>>();
            for (var p : state.plans.values()) {
                var required = p.blocks.stream().filter(b -> "REQUIRED".equals(b.priority)).toList();
                long done = required.stream().filter(b -> !"PENDING".equals(b.status)).count();
                out.put(p.planId, Map.of("done", done, "total", required.size(), "events", p.events, "complete", p.complete));
            }
            return out;
        }
    }

    /** 상담사 화면용 최근 플랜 진행(복원된 카드 이름). */
    public List<String> progress(String userId, String sessionId) { return progress(userId, sessionId, true); }
    /** restored=false면 AI 요약 입력용: 카드는 ref만 쓴다(가명화 유지). */
    public List<String> progress(String userId, String sessionId, boolean restored) {
        var state = profiles.state(userId, sessionId);
        synchronized (state) {
            if (state.plans.isEmpty()) return List.of();
            Plan last = null;
            for (var p : state.plans.values()) last = p;
            var lines = new ArrayList<String>();
            // 작업 38: 고객이 화면에서 직접 답한 정보(확인된 사실). 대화에는 없으므로 요약 AI가 "말하지 않았다"고 쓰지 않게 넣는다.
            if (state.tripCountry != null) lines.add("[고객 정보] 여행 나라: " + state.tripCountry);
            if (state.tripDepartureDate != null) lines.add("[고객 정보] 출국일: " + state.tripDepartureDate + "(" + daysText(profiles.daysUntilDeparture(state)) + ")");
            var cash = state.exchangeRequests.stream().map(r -> r.currency).distinct().toList();
            if (!cash.isEmpty()) lines.add("[고객 정보] 환전 신청: " + String.join(", ", cash)); // 금액은 넣지 않는다
            for (var b : last.blocks) {
                if (b.blockId.equals(HANDOFF)) continue;
                String card = restored ? b.target : b.targetRef;
                String who = b.targetRef.isEmpty() ? (b.param.isEmpty() ? "" : " · " + infoText(b.param, state)) : " · " + card;
                lines.add(("PENDING".equals(b.status) ? "[남음] " : "[완료] ") + b.name + who);
            }
            for (var g : last.goalStatus) if (!"SECURED".equals(g.status())) lines.add("[목표 미확보] " + g.label() + ": " + (restored ? g.detail() : g.status()));
            // 화면용 문장은 이름으로 복원돼 있다. AI 요약 입력에는 다시 가명화해서 넣는다.
            for (var u : last.unhandled) lines.add("[상담 필요] " + (restored ? u : Redactor.redact(state, u)));
            return lines;
        }
    }

    private static String daysText(Integer days) {
        if (days == null) return "날짜 확인 필요";
        return days == 0 ? "오늘" : days == 1 ? "내일" : days > 1 ? days + "일 뒤" : (-days) + "일 전";
    }
    /** 진행 줄의 블록 값: 정보 확인은 답한 값을, 통화는 코드 그대로 쓴다. */
    private static String infoText(String param, CustomerState state) {
        return switch (param) {
            case "DEPARTURE_DATE" -> state.tripDepartureDate == null ? "출국일" : "출국일 " + state.tripDepartureDate;
            case "COUNTRY" -> state.tripCountry == null ? "여행 나라" : "여행 나라 " + state.tripCountry;
            default -> param;
        };
    }

    /** AI가 쓴 글의 카드 ref(card-1 등)를 상담사가 읽을 카드 이름으로 바꾼다. */
    public String restore(String userId, String sessionId, String text) {
        if (text == null) return null;
        var state = profiles.state(userId, sessionId);
        synchronized (state) { return names(state, text); }
    }

    private static final java.util.regex.Pattern REF = java.util.regex.Pattern.compile("\\b(card|ap)-\\d+(?!\\d)");
    /** 작업 32: AI가 쓴 문장의 카드·구독 ref를 화면용 가상 이름으로 복원한다(AI 입력은 가명화 그대로). */
    static String names(CustomerState state, String text) {
        if (text == null || text.isEmpty()) return text;
        return REF.matcher(text).replaceAll(m -> {
            String ref = m.group();
            var card = state.card(ref);
            if (card != null) return java.util.regex.Matcher.quoteReplacement(card.display());
            for (var c : state.cards) for (var ap : c.autoPayments) if (ap.id.equals(ref)) return java.util.regex.Matcher.quoteReplacement(ap.merchant);
            return java.util.regex.Matcher.quoteReplacement(ref);
        });
    }

    /**
     * 작업 35: 같은 세션의 최근 플랜 2개(지금 다시 판단하는 플랜은 뺀다)를 AI 입력용으로 요약한다.
     * 사건 이름은 화면용으로 복원돼 있으므로 다시 가명화하고, 업무는 blockId@카드 ref로만 쓴다.
     */
    public List<AiClient.RecentPlan> recent(String userId, String sessionId, String excludePlanId) {
        var state = profiles.state(userId, sessionId);
        synchronized (state) {
            var all = state.plans.values().stream().filter(p -> !p.planId.equals(excludePlanId)).toList();
            var out = new ArrayList<AiClient.RecentPlan>();
            for (var p : all.subList(Math.max(0, all.size() - 2), all.size())) {
                var done = new ArrayList<String>(); var pending = new ArrayList<String>();
                for (var b : p.blocks) {
                    if (b.blockId.equals(HANDOFF)) continue;
                    String key = b.blockId + (b.targetRef.isEmpty() ? "" : "@" + b.targetRef);
                    ("PENDING".equals(b.status) ? pending : done).add(key);
                }
                out.add(new AiClient.RecentPlan(p.events.stream().map(e -> Redactor.redact(state, e)).toList(), done, pending));
            }
            return out;
        }
    }

    /** 작업 32: 원테이크 화면을 다시 열 때 쓰는 현재 플랜. */
    public Plan get(String userId, String sessionId, String planId) {
        var state = profiles.state(userId, sessionId);
        synchronized (state) { return owned(state, planId); }
    }

    private static Map<String, Object> skip(String id, String name, String target, String reason) {
        return Map.of("blockId", id, "name", name, "target", target, "reason", reason);
    }
    /** 긴급 보호 조치는 질문 때문에 늦추지 않는다: 정보 확인 블록은 마지막 분실신고 뒤로. */
    private static boolean finishedBefore(Item i, List<PlanBlock> finished) {
        return finished.stream().anyMatch(o -> i.same(o.blockId, o.targetRef, o.param));
    }
    private static int indexOf(List<Item> items, String id, String ref, String param) {
        for (int k = 0; k < items.size(); k++) if (items.get(k).same(id, ref, param)) return k;
        return -1;
    }
    /**
     * 작업 36: 같은 효과의 단계를 하나로 합친다(이전 플랜에서 끝낸 업무는 건드리지 않는다).
     * 같은 통화의 환율 확인 + 환전 신청 → 환전 신청(필수 우선), 같은 카드의 대체 결제 수단 확인 + 해외결제 설정 → 하나,
     * 해외결제 설정은 첫 장만 필수(해외에서 쓸 카드가 이미 있으면 모두 선택).
     */
    private static void dedupe(List<Item> items, CustomerState state, Set<String> lost, List<PlanBlock> finished, List<Map<String, Object>> skipped) {
        for (var rate : List.copyOf(items)) {
            if (!rate.block.id().equals("fx-rate-check") || finishedBefore(rate, finished)) continue;
            int at = indexOf(items, "fx-exchange-apply", "", rate.param);
            if (at < 0) continue;
            if ("REQUIRED".equals(rate.priority)) items.set(at, items.get(at).withPriority("REQUIRED"));
            items.remove(rate);
            skipped.add(skip(rate.block.id(), rate.block.name(), rate.param, "환전 화면에서 환율을 함께 보여 줘요"));
        }
        for (var alt : List.copyOf(items)) {
            if (!alt.block.id().equals("alt-payment-check") || finishedBefore(alt, finished)) continue;
            int at = indexOf(items, "overseas-payment-check", alt.ref, "");
            if (at < 0) continue;
            var card = state.card(alt.ref);
            if (card.overseasPayment) {
                var setting = items.remove(at);
                skipped.add(skip(setting.block.id(), setting.block.name(), card.display(), "이미 해외결제가 켜져 있어요"));
            } else {
                items.remove(alt);
                skipped.add(skip(alt.block.id(), alt.block.name(), card.display(), "해외결제 설정에서 함께 확인해요"));
            }
        }
        boolean payable = state.cards.stream().anyMatch(c -> c.usable() && c.overseasPayment && !lost.contains(c.ref));
        boolean first = true;
        for (int k = 0; k < items.size(); k++) {
            var i = items.get(k);
            if (!i.block.id().equals("overseas-payment-check") || finishedBefore(i, finished)) continue;
            if (payable || !first) items.set(k, i.withPriority("OPTIONAL"));
            first = false;
        }
    }
    /** 작업 36: 출국일을 묻는 단계는 끝낸 업무 다음 맨 앞에 둔다. 보호 조치가 있으면 protectFirst가 그 뒤로 옮긴다. */
    private static void departureFirst(List<Item> items, List<PlanBlock> finished) {
        int at = indexOf(items, "ask-info", "", "DEPARTURE_DATE");
        if (at < 0) return;
        int front = 0;
        while (front < items.size() && finishedBefore(items.get(front), finished)) front++;
        if (at > front) items.add(front, items.remove(at));
    }
    private static void protectFirst(List<Item> items) {
        int last = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).block.id().equals("card-lost-report")) last = i;
        for (int i = 0; i < last; i++) {
            var it = items.get(i);
            if (it.block.id().equals("ask-info")) { items.remove(i); items.add(last, it.adjust()); i--; last--; }
        }
    }
    // 꼭 지켜야 할 선후 관계만 맞춘다. AI가 정한 나머지 순서는 그대로 둔다.
    private void fixOrder(List<Item> items, CustomerState state) {
        for (int guardCount = 0; guardCount < 50; guardCount++) {
            boolean changed = false;
            outer:
            for (int i = 0; i < items.size(); i++) {
                var it = items.get(i);
                for (var req : it.block.requires()) {
                    var reqBlock = catalog.find(req);
                    String ref = reqBlock.cardTarget() ? it.ref : "", param = reqBlock.currencyParam() ? it.param : "";
                    if (satisfied(req, state.card(ref), state)) continue;
                    int j = -1;
                    for (int k = 0; k < items.size(); k++) if (items.get(k).same(req, ref, param)) { j = k; break; }
                    if (j < 0) { items.add(i, new Item(reqBlock, ref, param, "먼저 해야 하는 업무라 함께 넣었어요.", true, it.priority)); changed = true; break outer; }
                    if (j > i) { items.remove(i); items.add(j, it.adjust()); changed = true; break outer; }
                }
            }
            if (!changed) return;
        }
    }
    private static boolean satisfied(String req, CardState card, CustomerState state) {
        return switch (req) {
            case "card-lost-report" -> card != null && "STOPPED".equals(card.status);
            case "card-reissue" -> card != null && card.reissued;
            default -> false;
        };
    }
    private static boolean skip(Block block, CardState card, CustomerState state) {
        return switch (Objects.toString(block.skipWhen(), "")) {
            case "CARD_STOPPED" -> "STOPPED".equals(card.status);
            case "NO_SIMPLE_PAY" -> !card.simplePay;
            case "REISSUED" -> card.reissued;
            case "NO_AUTO_PAYMENTS" -> card.autoPayments.isEmpty();
            case "OVERSEAS_ON" -> card.overseasPayment;
            case "HAS_USABLE_TRAVEL_CARD" -> state.hasUsableTravelCard();
            case "LOST_OR_STOPPED" -> !card.usable();
            default -> false; // INFO_KNOWN은 param에 따라 knownInfo로 판단
        };
    }
    /** 가상 상태로 완료가 정해지는 신청 LINK·정보 입력 블록. */
    static final Set<String> COMPLETED_BY_STATE = Set.of("travel-card-apply", "fx-exchange-apply", "ask-info");
    /** SPEC R5 블록 완료 조건. null이면 상태로 정하지 않는 블록(열어 봄으로 완료). */
    private static Boolean completed(PlanBlock b, CardState card, CustomerState state) {
        return switch (b.blockId) {
            case "card-lost-report" -> "STOPPED".equals(card.status);
            case "simple-pay-unlink" -> !card.simplePay;
            case "card-reissue" -> card.reissued;
            case "auto-payment-switch" -> card.autoPayments.stream().noneMatch(a -> "ACTIVE".equals(a.status));
            case "overseas-payment-check" -> card.overseasPayment;
            case "travel-card-apply" -> state.travelCardApplication != null;
            case "fx-exchange-apply" -> state.exchangeRequests.stream().anyMatch(r -> r.currency.equals(b.param));
            case "ask-info" -> knownInfo(b.param, state);
            default -> null;
        };
    }
    /** 목표 판정에 쓰는 분실 카드: 고객이 확인했으면 그 카드, 아니면 AI가 고른 카드. */
    static Set<String> lostRefs(Plan plan) {
        return new LinkedHashSet<>(plan.confirmedCards != null ? plan.confirmedCards : plan.suggestedCards);
    }
    private void refresh(Plan plan, CustomerState state) {
        refreshBlocks(plan, state);
        var types = plan.goals.stream().map(g -> (String) g.get("type")).toList();
        String currency = plan.blocks.stream().filter(b -> b.blockId.startsWith("fx-")).map(b -> b.param).findFirst().orElse("");
        plan.goalStatus = GoalCheck.evaluate(state, types, lostRefs(plan), currency, profiles.daysUntilDeparture(state));
        var required = plan.goals.stream().filter(g -> Boolean.TRUE.equals(g.get("required"))).map(g -> (String) g.get("type")).toList();
        var unsecured = plan.goalStatus.stream().filter(g -> required.contains(g.type()) && !"SECURED".equals(g.status())).toList();
        boolean blocksDone = plan.blocks.stream().noneMatch(b -> "REQUIRED".equals(b.priority) && "PENDING".equals(b.status));
        plan.complete = blocksDone && unsecured.isEmpty();
        var remaining = new ArrayList<String>();
        for (var g : unsecured) remaining.add(g.label() + " — " + g.detail());
        for (var u : plan.unhandled) remaining.add("상담 필요: " + u);
        plan.remaining = remaining;
    }
    private void refreshBlocks(Plan plan, CustomerState state) {
        var won = NumberFormat.getIntegerInstance(Locale.KOREA);
        for (var b : plan.blocks) {
            var card = state.card(b.targetRef);
            var done = completed(b, card, state);
            if (done != null) b.status = done ? "DONE" : ("ACTION".equals(b.type) || "INPUT".equals(b.type) ? "PENDING" : b.status);
            b.detail = switch (b.blockId) {
                case "card-lost-report" -> "STOPPED".equals(card.status) ? "사용 정지됨(가상)" : "사용 중";
                case "simple-pay-unlink" -> card.simplePay ? card.simplePayName + "에 등록되어 있어요" : "간편결제 해제됨(가상)";
                case "card-reissue" -> card.reissued ? "재발급 신청 접수(가상) · 수령 3~5영업일, 받기 전에는 쓸 수 없어요" : "아직 재발급 전이에요";
                case "auto-payment-switch" -> {
                    long switched = card.autoPayments.stream().filter(a -> "SWITCHED".equals(a.status)).count();
                    long cancelled = card.autoPayments.stream().filter(a -> "CANCELLED".equals(a.status)).count();
                    yield switched + cancelled == 0 ? "자동결제 " + card.autoPayments.size() + "건"
                        : "다른 카드로 변경 " + switched + "건 · 해지 " + cancelled + "건(가상)";
                }
                case "overseas-payment-check" -> "해외결제 " + (card.overseasPayment ? "켜짐(가상)" : "꺼짐");
                case "fx-rate-check" -> {
                    var r = profiles.rates().get(b.param);
                    yield r.unit() + " " + b.param + "(" + r.name() + ") = " + won.format(r.krw()) + "원 · 시연용 가상 환율";
                }
                case "travel-card-check" -> state.hasUsableTravelCard() ? "사용 가능: " + state.travelCard().display()
                    : state.hasTravelCard ? "보유한 트래블카드(" + state.travelCard().display() + ")가 정지되어 지금은 쓸 수 없어요" : "보유한 트래블카드가 없어요";
                case "travel-card-apply" -> state.travelCardApplication != null ? "신청 접수 · " + state.travelCardApplication.status() + ", 받기 전에는 쓸 수 없어요" : "트래블카드 신청 화면으로 이동해요";
                case "fx-exchange-apply" -> {
                    var mine = state.exchangeRequests.stream().filter(r -> r.currency.equals(b.param)).toList();
                    yield mine.isEmpty() ? b.param + " 환전 신청 화면으로 이동해요"
                        : b.param + " 환전 신청 " + mine.size() + "건 · " + won.format(mine.getLast().amount) + " " + b.param + " = " + won.format(mine.getLast().krw) + "원(가상)";
                }
                case "travel-insurance-guide" -> "가입 안내 화면으로 이동해요(상품 추천 없음)";
                case "ask-info" -> "DEPARTURE_DATE".equals(b.param)
                    ? (state.tripDepartureDate == null ? "언제 출국하시나요?" : "출국일: " + state.tripDepartureDate)
                    : (state.tripCountry == null ? "어느 나라로 가시나요?" : "여행 나라: " + state.tripCountry);
                case "alt-payment-check" -> !card.usable() ? "지금 쓸 수 없는 카드예요"
                    : card.overseasPayment ? "해외에서 사용 가능(해외결제 켜짐)" : "해외결제가 꺼져 있어요. 켜야 해외에서 쓸 수 있어요";
                case HANDOFF -> plan.unhandled.isEmpty() ? "지금까지 내용을 상담사에게 넘겨요" : "남은 일: " + String.join(", ", plan.unhandled);
                default -> "";
            };
            if (b.blockId.equals("auto-payment-switch")) {
                var list = new ArrayList<Map<String, Object>>();
                for (var ap : card.autoPayments)
                    list.add(Map.of("id", ap.id, "category", ap.category, "merchant", ap.merchant, "amount", ap.amount, "billingDay", ap.billingDay,
                        "status", ap.status, "movedTo", ap.movedTo, "movedFrom", ap.movedFrom));
                b.items = list;
            }
        }
    }
    static boolean knownInfo(String param, CustomerState state) {
        return "DEPARTURE_DATE".equals(param) ? state.tripDepartureDate != null : state.tripCountry != null;
    }
    private static String clip(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }
    /** 받침이 있으면 "을", 없으면 "를". */
    static String object(String word) {
        char c = word.charAt(word.length() - 1);
        return c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) % 28 != 0 ? "을" : "를";
    }
}
