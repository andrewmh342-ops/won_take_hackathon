package com.wontake.assist;

import com.wontake.ai.AiClient.AiBlock;
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
 * R5 플랜 검증·복원·실행. 순서는 AI가 정하고, 서버는 사전·보유 여부·선후 관계·통화·개수·길이만 검사한다(고정 순서 없음).
 * 실행은 가상 고객 상태만 바꾼다.
 */
@Service
public class PlanService {
    static final int MAX_BLOCKS = 12, MAX_REASON = 120, MAX_UNHANDLED = 100, MAX_PLANS = 5;
    static final String HANDOFF = "counselor-handoff";
    private record Item(Block block, String ref, String param, String reason, boolean adjusted) {
        Item adjust() { return new Item(block, ref, param, reason, true); }
        boolean same(String id, String r, String p) { return block.id().equals(id) && ref.equals(r) && param.equals(p); }
    }
    private final DemoStore store;
    private final BlockCatalog catalog;
    private final ProfileStore profiles;
    private final UsageGuard guard;

    public PlanService(DemoStore store, BlockCatalog catalog, ProfileStore profiles) { this(store, catalog, profiles, new UsageGuard()); }
    @Autowired public PlanService(DemoStore store, BlockCatalog catalog, ProfileStore profiles, UsageGuard guard) {
        this.store = store; this.catalog = catalog; this.profiles = profiles; this.guard = guard;
    }

    public CustomerShape shape(String userId, String sessionId) { return profiles.shape(userId, sessionId); }
    public List<BlockInfo> blocksForAi() { return catalog.forAi(); }

    /** AI 플랜을 검증해 저장한다. 남은 블록이 없으면 null(일반 안내로 처리). */
    public Plan build(String userId, String sessionId, AiPlan ai) {
        if (ai == null || ai.blocks() == null) return null;
        var state = profiles.state(userId, sessionId);
        var aiInput = profiles.shape(userId, sessionId);
        synchronized (state) {
            var unhandled = new LinkedHashSet<String>();
            for (var u : Objects.requireNonNullElse(ai.unhandled(), List.<String>of()))
                if (u != null && !u.isBlank()) unhandled.add(clip(u.trim(), MAX_UNHANDLED));
            var items = new ArrayList<Item>();
            for (var b : ai.blocks()) {
                var block = b == null ? null : catalog.find(b.blockId());
                if (block == null) continue; // 사전에 없는 블록
                String ref = block.cardTarget() ? Objects.toString(b.targetRef(), "").trim() : "";
                if (block.cardTarget() && state.card(ref) == null) continue; // 갖지 않은 카드
                String param = block.currencyParam() ? Objects.toString(b.param(), "").trim().toUpperCase(Locale.ROOT) : "";
                if (block.currencyParam() && !profiles.rates().containsKey(param)) {
                    unhandled.add(clip((param.isEmpty() ? "여행 나라" : param) + " 통화 환율·환전 안내(시연 지원 통화 아님)", MAX_UNHANDLED));
                    continue;
                }
                if (skip(block, state.card(ref), state)) continue;
                if (items.stream().anyMatch(i -> i.same(block.id(), ref, param))) continue;
                items.add(new Item(block, ref, param, clip(Objects.toString(b.reason(), "").trim(), MAX_REASON), false));
            }
            fixOrder(items, state);
            if (items.size() > MAX_BLOCKS) items.subList(MAX_BLOCKS, items.size()).clear();
            if (!unhandled.isEmpty() && items.stream().noneMatch(i -> i.block.id().equals(HANDOFF))) {
                if (items.size() >= MAX_BLOCKS) items.removeLast();
                items.add(new Item(catalog.find(HANDOFF), "", "", "블록으로 처리할 수 없는 일을 상담사에게 이어서 맡겨요.", false));
            }
            if (items.isEmpty()) return null;
            var plan = new Plan();
            plan.planId = UUID.randomUUID().toString();
            plan.events = Objects.requireNonNullElse(ai.events(), List.<String>of()).stream().filter(e -> e != null && !e.isBlank()).map(e -> clip(e.trim(), MAX_UNHANDLED)).limit(5).toList();
            plan.orderReason = clip(Objects.toString(ai.orderReason(), "").trim(), MAX_REASON);
            plan.unhandled = List.copyOf(unhandled);
            plan.aiInput = aiInput;
            plan.blocks = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                var it = items.get(i);
                var pb = new PlanBlock();
                pb.index = i; pb.blockId = it.block.id(); pb.name = it.block.name(); pb.type = it.block.type(); pb.menuId = it.block.menuId();
                pb.targetRef = it.ref; pb.param = it.param; pb.reason = it.reason; pb.adjusted = it.adjusted;
                pb.target = it.ref.isEmpty() ? "" : state.card(it.ref).display();
                plan.blocks.add(pb);
            }
            refresh(plan, state);
            state.plans.put(plan.planId, plan);
            while (state.plans.size() > MAX_PLANS) state.plans.remove(state.plans.keySet().iterator().next());
            return plan;
        }
    }

    /** 블록 하나를 실행한다. 선행 블록이 끝나지 않았으면 409, 끝난 블록은 같은 결과를 돌려준다. */
    public Plan execute(String userId, String sessionId, String planId, Integer index, Map<String, String> options) {
        var state = profiles.state(userId, sessionId);
        guard.plan(sessionId);
        synchronized (state) {
            var plan = planId == null ? null : state.plans.get(planId);
            if (plan == null) throw new ApiException(404, "PLAN_NOT_FOUND", "플랜을 찾지 못했어요. 다시 물어봐 주세요.");
            if (index == null || index < 0 || index >= plan.blocks.size()) throw ApiException.invalid("블록 번호를 확인해 주세요.");
            var pb = plan.blocks.get(index);
            if (!"PENDING".equals(pb.status)) return plan;
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
                    var chosen = Objects.requireNonNullElse(options, Map.<String, String>of());
                    for (var ap : card.autoPayments) ap.status = "CANCEL".equals(chosen.get(ap.id)) ? "CANCELLED" : "SWITCHED";
                }
                case "overseas-payment-check" -> card.overseasPayment = true;
                default -> { }
            }
            // CHECK·안내 LINK·HANDOFF는 화면을 열면 끝난다. 신청 LINK와 ACTION은 가상 상태로 완료를 계산한다(refresh).
            if (!"ACTION".equals(pb.type) && !COMPLETED_BY_STATE.contains(pb.blockId)) pb.status = "CHECK".equals(pb.type) ? "DONE" : "VISITED";
            refresh(plan, state);
            return plan;
        }
    }

    /** 화면에서 가상 상태를 바꾼 뒤 열린 플랜들의 블록 상태를 다시 계산한다. */
    public void refreshAll(CustomerState state) {
        synchronized (state) { for (var p : state.plans.values()) refresh(p, state); }
    }
    /** 화면 위 진행 띠용: 플랜별 완료 수. */
    public Map<String, Map<String, Object>> progressSummary(CustomerState state) {
        synchronized (state) {
            var out = new LinkedHashMap<String, Map<String, Object>>();
            for (var p : state.plans.values()) {
                long done = p.blocks.stream().filter(b -> !"PENDING".equals(b.status)).count();
                out.put(p.planId, Map.of("done", done, "total", p.blocks.size(), "events", p.events));
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
            for (var b : last.blocks) {
                if (b.blockId.equals(HANDOFF)) continue;
                String card = restored ? b.target : b.targetRef;
                String who = b.targetRef.isEmpty() ? (b.param.isEmpty() ? "" : " · " + b.param) : " · " + card;
                lines.add(("PENDING".equals(b.status) ? "[남음] " : "[완료] ") + b.name + who);
            }
            for (var u : last.unhandled) lines.add("[상담 필요] " + u);
            return lines;
        }
    }

    /** AI가 쓴 글의 카드 ref(card-1 등)를 상담사가 읽을 카드 이름으로 바꾼다. */
    public String restore(String userId, String sessionId, String text) {
        if (text == null) return null;
        var state = profiles.state(userId, sessionId);
        String out = text;
        for (var c : state.cards) out = out.replace(c.ref, c.display());
        return out;
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
                    if (j < 0) { items.add(i, new Item(reqBlock, ref, param, "먼저 해야 하는 업무라 함께 넣었어요.", true)); changed = true; break outer; }
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
            case "HAS_TRAVEL_CARD" -> state.hasTravelCard;
            default -> false;
        };
    }
    /** 가상 상태로 완료가 정해지는 신청 LINK 블록. */
    static final Set<String> COMPLETED_BY_STATE = Set.of("travel-card-apply", "fx-exchange-apply");
    /** SPEC R5 블록 완료 조건. null이면 상태로 정하지 않는 블록(열어 봄으로 완료). */
    private static Boolean completed(Plan.PlanBlock b, CardState card, CustomerState state) {
        return switch (b.blockId) {
            case "card-lost-report" -> "STOPPED".equals(card.status);
            case "simple-pay-unlink" -> !card.simplePay;
            case "card-reissue" -> card.reissued;
            case "auto-payment-switch" -> card.autoPayments.stream().noneMatch(a -> "ACTIVE".equals(a.status));
            case "overseas-payment-check" -> card.overseasPayment;
            case "travel-card-apply" -> state.travelCardApplication != null;
            case "fx-exchange-apply" -> state.exchangeRequests.stream().anyMatch(r -> r.currency.equals(b.param));
            default -> null;
        };
    }
    private void refresh(Plan plan, CustomerState state) {
        var won = NumberFormat.getIntegerInstance(Locale.KOREA);
        for (var b : plan.blocks) {
            var card = state.card(b.targetRef);
            var done = completed(b, card, state);
            if (done != null) b.status = done ? "DONE" : ("ACTION".equals(b.type) ? "PENDING" : b.status);
            b.detail = switch (b.blockId) {
                case "card-lost-report" -> "STOPPED".equals(card.status) ? "사용 정지됨(가상)" : "사용 중";
                case "simple-pay-unlink" -> card.simplePay ? card.simplePayName + "에 등록되어 있어요" : "간편결제 해제됨(가상)";
                case "card-reissue" -> card.reissued ? "재발급 신청됨(가상)" : "아직 재발급 전이에요";
                case "auto-payment-switch" -> {
                    long switched = card.autoPayments.stream().filter(a -> "SWITCHED".equals(a.status)).count();
                    long cancelled = card.autoPayments.stream().filter(a -> "CANCELLED".equals(a.status)).count();
                    yield switched + cancelled == 0 ? "자동결제 " + card.autoPayments.size() + "건"
                        : "새 카드로 변경 " + switched + "건 · 해지 " + cancelled + "건(가상)";
                }
                case "overseas-payment-check" -> "해외결제 " + (card.overseasPayment ? "켜짐(가상)" : "꺼짐");
                case "fx-rate-check" -> {
                    var r = profiles.rates().get(b.param);
                    yield r.unit() + " " + b.param + "(" + r.name() + ") = " + won.format(r.krw()) + "원 · 시연용 가상 환율";
                }
                case "travel-card-check" -> state.hasTravelCard ? "보유 중: " + state.travelCard().display() : "보유한 트래블카드가 없어요";
                case "travel-card-apply" -> state.travelCardApplication != null ? "신청 완료 · " + state.travelCardApplication.status() : "트래블카드 신청 화면으로 이동해요";
                case "fx-exchange-apply" -> {
                    var mine = state.exchangeRequests.stream().filter(r -> r.currency.equals(b.param)).toList();
                    yield mine.isEmpty() ? b.param + " 환전 신청 화면으로 이동해요"
                        : b.param + " 환전 신청 " + mine.size() + "건 · " + won.format(mine.getLast().amount) + " " + b.param + " = " + won.format(mine.getLast().krw) + "원(가상)";
                }
                case "travel-insurance-guide" -> "가입 안내 화면으로 이동해요(상품 추천 없음)";
                case HANDOFF -> plan.unhandled.isEmpty() ? "지금까지 내용을 상담사에게 넘겨요" : "남은 일: " + String.join(", ", plan.unhandled);
                default -> "";
            };
            if (b.blockId.equals("auto-payment-switch")) {
                var list = new ArrayList<Map<String, Object>>();
                for (var ap : card.autoPayments)
                    list.add(Map.of("id", ap.id, "category", ap.category, "merchant", ap.merchant, "amount", ap.amount, "billingDay", ap.billingDay, "status", ap.status));
                b.items = list;
            }
        }
    }
    private static String clip(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }
    /** 받침이 있으면 "을", 없으면 "를". */
    static String object(String word) {
        char c = word.charAt(word.length() - 1);
        return c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) % 28 != 0 ? "을" : "를";
    }
}
