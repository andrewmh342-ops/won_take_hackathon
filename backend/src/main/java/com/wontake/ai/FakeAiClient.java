package com.wontake.ai;

import com.wontake.api.ApiException;
import java.util.*;
import java.util.regex.Pattern;

/** 테스트·키 없는 연결 확인용 규칙. 실제 AI 추론으로 시연하지 않는다. */
public class FakeAiClient implements AiClient {
    private static final List<String> FINANCE_WORDS = List.of("앱", "은행", "계좌", "통장", "이체", "송금", "출금", "입금", "한도", "잔액", "내역",
        "인증", "비밀번호", "로그인", "기기", "증명", "서류", "환전", "환율", "외화", "카드", "대출", "예금", "적금", "수수료", "상담", "돈", "오류", "안 돼", "안돼");
    private static final Map<String, List<String>> LIKELY = Map.of(
        "이체/출금", List.of("이체 한도는 얼마예요?", "해외로 돈을 보내려면 어떻게 해요?", "예약 이체는 어떻게 해요?"),
        "계좌조회/관리", List.of("내 계좌 잔액을 보고 싶어요", "최근 거래내역은 어디서 봐요?", "계좌 비밀번호를 바꾸고 싶어요"),
        "카드", List.of("카드를 잃어버렸어요", "해외에서 카드를 쓰려면요?", "자동결제를 다른 카드로 옮기고 싶어요"),
        "환전", List.of("엔화 환율은 얼마예요?", "환전은 어디서 받아요?", "여행자 보험은 어떻게 가입해요?"),
        "인증/보안", List.of("인증서를 새로 받고 싶어요", "비밀번호를 잊어버렸어요", "새 휴대폰에서 로그인하려면요?"),
        "증명서/서류", List.of("잔액증명서를 떼고 싶어요", "거래내역서는 어디서 받아요?", "증명서를 파일로 받을 수 있나요?"));
    @Override public ProactiveSuggestion suggest(BehaviorContext context) {
        String category=context.visits().getLast().category();
        String question=category.equals("이체/출금") ? "이체나 송금 메뉴를 찾는 데 도움이 필요하세요?" : category+" 메뉴를 찾는 데 도움이 필요하세요?";
        // 작업 39: 연결 테스트용 예상 질문 3개(분류별 고정). 실제 생성은 CLAUDE 모드에서 한다.
        var questions=LIKELY.getOrDefault(category,List.of(category+" 메뉴는 어디 있어요?",category+"에서 막혔어요",category+" 하는 방법 알려 주세요"));
        return new ProactiveSuggestion(true,question,context.visits().stream().map(Visit::menuId).distinct().limit(2).toList(),questions);
    }
    /** 연결 테스트용 결정적 플랜. 실제 순서 판단은 CLAUDE 모드에서 한다. 보유 여부 걸러내기는 서버(PlanService)가 한다. */
    private static AiPlan plan(Context c) {
        if (c.customer() == null) return AiPlan.EMPTY;
        String q = c.question();
        var blocks = new ArrayList<AiBlock>(); var events = new ArrayList<String>(); var unhandled = new ArrayList<String>();
        var goals = new ArrayList<AiGoal>(); var obstacles = new ArrayList<AiObstacle>(); var considered = new ArrayList<AiConsidered>();
        var cards = c.customer().cards();
        boolean lost = List.of("잃어버", "잃었", "분실", "도난").stream().anyMatch(q::contains);
        var countries = new LinkedHashMap<String, String>();
        countries.put("일본", "JPY"); countries.put("미국", "USD"); countries.put("유럽", "EUR"); countries.put("프랑스", "EUR");
        countries.put("중국", "CNY"); countries.put("몽골", "MNT");
        boolean travel = List.of("여행", "출국").stream().anyMatch(q::contains) || countries.keySet().stream().anyMatch(q::contains);
        var confirmed = c.planState() == null ? null : c.planState().confirmedLostCards();
        List<CardShape> lostCards = List.of();
        if (confirmed != null) lostCards = cards.stream().filter(k -> confirmed.contains(k.ref())).toList();
        else if (lost) {
            // 가명화된 질문에 카드 ref가 있으면(고객이 카드 이름·끝자리를 말함) 그 카드다
            var named = cards.stream().filter(k -> java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(k.ref()) + "(?!\\d)").matcher(q).find()).toList();
            if (!named.isEmpty()) lostCards = named;
            else if (q.contains("트래블")) lostCards = cards.stream().filter(CardShape::travel).toList();
            else if (List.of("한 장", "한장", "1장").stream().anyMatch(q::contains)) lostCards = cards.subList(0, Math.min(1, cards.size()));
            else if (List.of("두 장", "두장", "2장").stream().anyMatch(q::contains)) lostCards = cards.subList(0, Math.min(2, cards.size()));
            else lostCards = cards;
        }
        var lostRefs = lostCards.stream().map(CardShape::ref).toList();
        if (!lostCards.isEmpty()) {
            events.add("카드 분실");
            goals.add(new AiGoal("CARD_PROTECTION", "(모의) 잃어버린 카드 부정 사용 막기", true));
            for (var card : lostCards) blocks.add(new AiBlock("card-lost-report", card.ref(), "", "(모의) 부정 사용을 막기 위해 먼저 정지해요."));
            for (var card : lostCards)
                if (card.simplePay()) blocks.add(new AiBlock("simple-pay-unlink", card.ref(), "", "(모의) 휴대폰 결제도 막아요."));
                else considered.add(new AiConsidered("simple-pay-unlink", "(모의) 간편결제에 등록되지 않은 카드예요."));
            for (var card : lostCards)
                if (!card.autoPayments().isEmpty()) blocks.add(new AiBlock("auto-payment-switch", card.ref(), "", "(모의) 정기결제가 끊기지 않게 옮겨요."));
                else considered.add(new AiConsidered("auto-payment-switch", "(모의) 이 카드에 걸린 자동결제가 없어요."));
            for (var card : lostCards) blocks.add(new AiBlock("card-reissue", card.ref(), "", "(모의) 새 카드를 신청해요. 받기까지 3~5영업일이 걸려요."));
            if (q.contains("신분증")) unhandled.add("신분증 분실 신고");
            if (q.contains("OTP")) unhandled.add("OTP 재발급");
        }
        // 작업 37: 이전 플랜에서 여행 나라를 알게 된 고객이 카드를 잃어버리면, 여행 준비도 이어서 본다(맥락 기억).
        var trip = c.customer().trip();
        boolean knownTrip = trip != null && trip.country() != null;
        if (travel || (!lostCards.isEmpty() && knownTrip)) {
            String asked = countries.entrySet().stream().filter(e -> q.contains(e.getKey())).map(Map.Entry::getValue).findFirst().orElse("");
            String currency = !asked.isEmpty() ? asked : knownTrip ? countries.getOrDefault(trip.country(), "") : "";
            var cash = c.customer().cash() == null ? List.<String>of() : c.customer().cash();
            events.add("해외여행 준비");
            goals.add(new AiGoal("PAYMENT_ABROAD", "(모의) 여행지에서 쓸 결제 수단 확보", true));
            boolean travelCardLost = lostCards.stream().anyMatch(CardShape::travel), noOtherCard = false;
            // 출국일을 모르면 묻는다. 받기까지 걸리는 업무(재발급·환전 수령)를 판단하는 데 필요하다(보호 조치가 있으면 서버가 그 뒤로 옮긴다).
            if (trip == null || trip.daysUntilDeparture() == null)
                blocks.add(new AiBlock("ask-info", "", "DEPARTURE_DATE", "(모의) 출국일에 맞춰 준비할 일을 정해요."));
            if (currency.isEmpty() && !knownTrip)
                blocks.add(new AiBlock("ask-info", "", "COUNTRY", "(모의) 여행 나라에 따라 통화가 달라요."));
            // 해외에서 쓸 카드는 한 장이면 된다: 잃어버리지 않은 사용 가능 카드 중 이미 켜진 카드가 없을 때만, 트래블카드 우선으로 한 장을 켠다.
            var keep = cards.stream().filter(k -> !lostRefs.contains(k.ref()) && k.usable()).toList();
            boolean payable = keep.stream().anyMatch(CardShape::overseasPayment);
            var pick = keep.stream().filter(CardShape::travel).findFirst().orElse(keep.isEmpty() ? null : keep.getFirst());
            if (!lostCards.isEmpty()) {
                obstacles.add(new AiObstacle("(모의) 카드 분실로 여행지에서 쓸 결제 수단이 줄었어요.", "BLOCK", List.of()));
                if (!payable && pick != null)
                    blocks.add(new AiBlock("overseas-payment-check", pick.ref(), "", "(모의) 남은 카드로 해외에서 결제할 수 있게 켜요."));
                if (travelCardLost) considered.add(new AiConsidered("travel-card-apply", "(모의) 잃어버린 트래블카드를 재발급하므로 새로 신청하지 않았어요."));
                // 잃어버리지 않은 사용 가능 카드가 없으면 해외 결제 수단이 남지 않는다: 현금(환전)을 필수로, 수령 일정은 상담으로
                noOtherCard = keep.isEmpty();
                if (noOtherCard) {
                    obstacles.add(new AiObstacle("(모의) 해외에서 쓸 수 있는 다른 카드가 없어요.", "HANDOFF", List.of()));
                    unhandled.add("출국 전 해외 결제 수단 확보(새 카드 수령 일정 확인)");
                }
            } else {
                blocks.add(new AiBlock("travel-card-check", "", "", "(모의) 해외에서 쓸 카드를 확인해요."));
                blocks.add(new AiBlock("travel-card-apply", "", "", "(모의) 트래블카드가 없으면 신청해요."));
                if (!payable && pick != null)
                    blocks.add(new AiBlock("overseas-payment-check", pick.ref(), "", "(모의) 해외에서 결제되게 해요."));
            }
            // 이미 환전을 신청한 통화는 다시 넣지 않는다(이전 플랜에서 한 일).
            if (!currency.isEmpty() && !cash.contains(currency)) {
                blocks.add(new AiBlock("fx-rate-check", "", currency, "(모의) 여행지 환율을 확인해요."));
                if (noOtherCard) {
                    blocks.add(new AiBlock("fx-exchange-apply", "", currency, "(모의) 쓸 수 있는 카드가 없어 현지 현금이 꼭 필요해요."));
                    goals.add(new AiGoal("CASH_ABROAD", "(모의) 카드 대신 쓸 현지 현금 준비", true));
                } else {
                    blocks.add(new AiBlock("fx-exchange-apply", "", currency, "(모의) 현금이 필요하면 환전해요.", "OPTIONAL"));
                    goals.add(new AiGoal("CASH_ABROAD", "(모의) 필요하면 현지 현금 준비", false));
                }
            }
            if (travel) blocks.add(new AiBlock("travel-insurance-guide", "", "", "(모의) 여행 준비 전체를 원할 때 보험 가입 방법을 볼 수 있어요.", "OPTIONAL"));
        }
        return new AiPlan(events, blocks, unhandled, blocks.isEmpty() ? "" : "(모의) 보호 조치 먼저, 그다음 남은 문제를 해결하는 순서예요.",
            goals, obstacles, considered);
    }
    @Override public HandoffSummary summarize(HandoffContext c) {
        String purpose=c.conversation().stream().filter(t->t.role().equals("USER")).reduce((a,b)->b)
            .map(t->"(모의) 고객 마지막 질문: "+t.message()).orElse("");
        var tried=new LinkedHashSet<String>();
        for(var e:c.clickTimeline()) for(var m:c.menus()) if(m.id().equals(e.elementId())) tried.add(m.name()+" 방문");
        for(var t:c.conversation()) if(t.role().equals("ASSISTANT"))
            for(var m:c.menus()) if(t.message().contains(m.name())) tried.add(m.name()+" 안내받음");
        for(var line:c.planProgress()) if(line.startsWith("[완료] ")) tried.add(line.substring(5));
        // 작업 38: [고객 정보]는 고객이 화면에서 답한 사실이라 목적에 붙이고, 남은 문제로 보지 않는다.
        var info=c.planProgress().stream().filter(l->l.startsWith("[고객 정보] ")).map(l->l.substring("[고객 정보] ".length())).toList();
        if(!info.isEmpty()) purpose=(purpose.isEmpty()?"(모의) ":purpose+" · ")+String.join(", ",info);
        var left=c.planProgress().stream().filter(l->!l.startsWith("[완료] ") && !l.startsWith("[고객 정보] ")).toList();
        return new HandoffSummary(purpose,"",tried.isEmpty()?"":"(모의) "+String.join(", ",tried),left.isEmpty()?"":"(모의) "+String.join(", ",left));
    }
    private static final Pattern WHO = Pattern.compile("(\\S+?)(?:한테|에게)"), SEND = Pattern.compile("\\[번호\\]\\s*[만천억]?원|\\[금액\\]|보내|이체|송금|넣어");
    /** 연결 테스트용: 쉼표로 나눈 조각마다 받는 곳 별칭(없으면 "~한테")과 "N만원"/"N원"을 찾는다. */
    @Override public PlanDraft plan(PlanContext c) {
        var tasks = new ArrayList<TaskDraft>();
        for (var part : c.request().split("[,，]")) {
            String target = null;
            for (var p : c.payees()) for (var alias : p.aliases()) if (target == null && part.contains(alias)) target = p.name();
            if (target == null) { var who = WHO.matcher(part); if (who.find()) target = who.group(1); }
            if (target != null) tasks.add(new TaskDraft(target, TransferAmounts.find(part, c.amountValues()), ""));
        }
        if (tasks.isEmpty() && FINANCE_WORDS.stream().noneMatch(c.request()::contains)) return new PlanDraft(Answer.OUT_OF_SCOPE, List.of());
        return new PlanDraft(Answer.IN_SCOPE, tasks);
    }
    /** 연결 테스트용: 빈 곳이 하나면 말한 금액을 그곳에 쓰고, 여럿이면 이름을 말한 조각의 금액만 쓴다. */
    @Override public List<AmountDraft> fill(FillContext c) {
        var amounts = new ArrayList<AmountDraft>();
        for (var target : c.targets()) {
            Long amount = null;
            for (var part : c.message().split("[,，]"))
                if (amount == null && (c.targets().size() == 1 || part.contains(target))) amount = TransferAmounts.find(part, c.amountValues());
            amounts.add(new AmountDraft(target, amount));
        }
        return amounts;
    }
    @Override public Answer answer(Context c) {
        String q = c.question().replace("이채", "이체");
        var plan = plan(c);
        if (!plan.blocks().isEmpty())
            return new Answer("연결 테스트용 모의 플랜입니다. 해야 할 일을 순서대로 정리했어요.", "VOICE".equals(c.inputType()) ? q : "",
                List.of(), List.of(), false, Answer.IN_SCOPE, plan);
        // 연결 테스트용 판정: "누구한테/에게"와 돈을 보내는 말이 함께 있으면 이체 요청으로 본다("상담사에게 연결해 줘"는 아니다).
        // 실제 판정은 CLAUDE 모드에서 지침으로 한다. 프론트 mock.js의 같은 판정과 함께 고친다.
        if (WHO.matcher(q).find() && SEND.matcher(q).find()) return new Answer("연결 테스트용 모의 응답입니다. 계좌이체 메뉴를 확인해 보세요.", "", List.of("instant-transfer"), List.of(), false, Answer.TRANSFER_PLAN);
        List<String> ids = new ArrayList<>();
        if(q.contains("방금 제안") && c.helpContext()!=null) ids=c.helpContext().visits().stream().map(Visit::menuId).distinct().limit(2).toList();
        else if (q.contains("내역")) {
            String domain = "";
            for (var t : c.history()) {
                if (t.role().equals("USER")) {
                    if (t.message().contains("환전")) domain="exchange";
                    else if (t.message().contains("이체")) domain="transfer";
                }
            }
            var contextEvents = c.recentEvents();
            if(c.helpContext()!=null && contextEvents.stream().noneMatch(e->Set.of("exchange","transfer","account").contains(e.screenId())))
                contextEvents=c.helpContext().recentEvents();
            for (var event : contextEvents) {
                if (event.screenId().equals("exchange")) domain="exchange";
                else if (event.screenId().equals("transfer")) domain="transfer";
                else if (event.screenId().equals("account")) domain="account";
            }
            if (q.contains("환전")) domain="exchange";
            else if (q.contains("이체")) domain="transfer";
            else if (q.contains("거래") || q.contains("입출금")) domain="account";
            ids = switch(domain) {
                case "exchange" -> List.of("exchange-history");
                case "transfer" -> List.of("transfer-history");
                case "account" -> List.of("transaction-history");
                default -> List.of("transfer-history", "exchange-history");
            };
        } else if (q.contains("한도")) ids=List.of("transfer-limit-manage");
        else {
            for (var m : c.menus()) if (q.contains(m.name())) { ids=List.of(m.id()); break; }
            if (ids.isEmpty()) {
                if (q.contains("환전")) ids=List.of("exchange-apply");
                else if (q.contains("이체") || q.contains("송금")) ids=List.of("instant-transfer");
                else if (q.contains("계좌") || q.contains("잔액")) ids=List.of("all-accounts");
                else if (q.contains("증명")) ids=List.of("balance-certificate");
                else if (q.contains("인증")) ids=List.of("auth-center");
            }
        }
        // 연결 테스트용 범위 판정: 메뉴도 금융앱 이용 낱말도 없으면 범위 밖. 실제 판정은 CLAUDE 모드에서 지침으로 한다.
        if (ids.isEmpty() && FINANCE_WORDS.stream().noneMatch(q::contains))
            return new Answer("", "", List.of(), List.of(), false, Answer.OUT_OF_SCOPE);
        final var selected=ids;
        String names = String.join(", ", c.menus().stream().filter(m -> selected.contains(m.id())).map(m -> m.name()).toList());
        return new Answer(ids.isEmpty() ? "연결 테스트용 모의 응답입니다. 관련 메뉴를 찾지 못했어요." :
            "연결 테스트용 모의 응답입니다. " + names + " 메뉴를 확인해 보세요.",
            "VOICE".equals(c.inputType()) ? c.question() : "", ids, List.of(), q.contains("상담"), Answer.IN_SCOPE, ids.size() > 1);
    }
}
