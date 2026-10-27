package com.wontake.assist;

import com.wontake.ai.AiClient;
import com.wontake.assist.ProfileStore.CustomerState;
import com.wontake.session.DemoStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 작업 25: AI에 가는 모든 입력(질문·이전 대화·행동 기록·말풍선 맥락·상담 요약)을 같은 규칙으로 가명화한다.
 * 보유 카드 이름·끝자리 → 카드 ref, 구독 가맹점명 → 구독 ref, 10자리 이상 번호 → [번호], 금액 → [금액].
 * 화면·상담사 원문·대화 기록은 바꾸지 않는다(AI로 보내는 사본만 바꾼다).
 */
@Component
public class Redactor {
    /** 공백·하이픈을 포함해 숫자 10자리 이상(카드·계좌·전화번호). 날짜(8자리)·시각 같은 짧은 숫자는 그대로 둔다. */
    static final Pattern LONG_NUMBER = Pattern.compile("(?<!\\d)\\d(?:[ -]?\\d){9,}(?!\\d)");
    /** 숫자 + (만·천·억) + 원. "5만 원", "55,000원", "55000 원". */
    public static final Pattern AMOUNT = Pattern.compile("(?<![\\d,])[-+]?\\d[\\d,]*(?:\\.\\d+)?(?:\\s*[만천억]\\s*(?:\\d[\\d,]*(?:\\.\\d+)?)?)*\\s*원");
    static final Pattern REPEATED_REF = Pattern.compile("\\b(card-\\d+)(?:\\s+\\1)+(?!\\d)");
    private final ProfileStore profiles;

    @Autowired public Redactor(ProfileStore profiles) { this.profiles = profiles; }

    public String text(String userId, String sessionId, String text) {
        var state = profiles.state(userId, sessionId);
        synchronized (state) { return redact(state, text); }
    }
    public List<AiClient.Turn> turns(String userId, String sessionId, List<AiClient.Turn> turns) {
        return turns.stream().map(t -> new AiClient.Turn(t.role(), text(userId, sessionId, t.message()))).toList();
    }
    public List<DemoStore.Event> events(String userId, String sessionId, List<DemoStore.Event> events) {
        return events.stream().map(e -> new DemoStore.Event(e.type(), e.screenId(), e.elementId(), text(userId, sessionId, e.query()), e.timestamp())).toList();
    }
    public AiClient.HelpContext help(String userId, String sessionId, AiClient.HelpContext help) {
        if (help == null) return null;
        return new AiClient.HelpContext(help.id(), text(userId, sessionId, help.question()), help.visits(), events(userId, sessionId, help.recentEvents()));
    }
    public AiClient.BehaviorContext behavior(String userId, String sessionId, AiClient.BehaviorContext c) {
        return new AiClient.BehaviorContext(c.visits(), events(userId, sessionId, c.recentEvents()), turns(userId, sessionId, c.history()), c.menus());
    }

    static String redact(CustomerState state, String text) {
        if (text == null || text.isEmpty()) return text;
        String s = LONG_NUMBER.matcher(text).replaceAll("[번호]");
        s = AMOUNT.matcher(s).replaceAll("[금액]");
        // 긴 이름부터 바꾼다("우리 신용카드 끝자리 4821(가상)" → "우리 신용카드" 순).
        var names = new LinkedHashMap<String, String>();
        for (var card : state.cards) {
            names.put(card.display(), card.ref);
            names.put(card.name + " 끝자리 " + card.last4, card.ref);
            names.put(card.name, card.ref);
            for (var ap : card.autoPayments) names.putIfAbsent(ap.merchant, ap.id);
        }
        var ordered = new ArrayList<>(names.entrySet());
        ordered.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
        for (var e : ordered) s = s.replace(e.getKey(), e.getValue());
        for (var card : state.cards)
            s = Pattern.compile("(?<!\\d)" + Pattern.quote(card.last4) + "(?!\\d)").matcher(s).replaceAll(Matcher.quoteReplacement(card.ref));
        // "우리 신용카드 4821"처럼 이름과 끝자리를 함께 말하면 같은 ref가 겹친다 → 하나로
        return REPEATED_REF.matcher(s).replaceAll("$1");
    }
}
