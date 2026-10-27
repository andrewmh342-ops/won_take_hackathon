package com.wontake.behavior;

import com.wontake.ai.AiClient;
import com.wontake.catalog.MenuCatalog;
import com.wontake.session.DemoStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 규칙은 분석 계기만 감지한다. 도움 제안 여부·역질문은 AiClient가 결정한다. */
@Service
public class BehaviorService {
    public record Bubble(String contextId, String screenId, String category, String question, List<String> menuIds, String mode) {}
    private record Active(String menuId, String category, long entered) {}
    private static class Analysis {
        final String id=UUID.randomUUID().toString();
        final String screenId;
        final long created;
        final AiClient.BehaviorContext input;
        boolean started;
        Bubble bubble;
        Analysis(String screenId,long created,AiClient.BehaviorContext input) { this.screenId=screenId; this.created=created; this.input=input; }
    }
    private static class State {
        final DemoStore.Session session;
        Active active;
        final List<AiClient.Visit> visits=new ArrayList<>();
        long lastAnalysis=Long.MIN_VALUE/2;
        Analysis pending;
        State(DemoStore.Session session) { this.session=session; }
    }
    private final DemoStore store;
    private final MenuCatalog catalog;
    private final AiClient ai;
    private final Clock clock;
    private final Map<String,State> states=new ConcurrentHashMap<>();
    @Autowired public BehaviorService(DemoStore store,MenuCatalog catalog,AiClient ai) { this(store,catalog,ai,Clock.systemUTC()); }
    public BehaviorService(DemoStore store,MenuCatalog catalog,AiClient ai,Clock clock) { this.store=store; this.catalog=catalog; this.ai=ai; this.clock=clock; }
    /** 작업 25: 말풍선 제안 입력(검색어·이전 대화)도 가명화한다. 없으면(단독 테스트) 그대로 보낸다. */
    private com.wontake.assist.Redactor redactor;
    @Autowired(required=false) public void setRedactor(com.wontake.assist.Redactor redactor) { this.redactor=redactor; }

    public String record(String userId,String sessionId,String type,String screenId,String elementId,String query) {
        if(!store.event(userId,sessionId,type,screenId,elementId,query) || !type.equals("SCREEN_ENTER") || store.never(userId)) return null;
        var session=store.session(userId,sessionId);
        // 세션과 행동 상태의 잠금을 동시에 잡지 않는다. AI 요청 중에도 이벤트 기록은 계속 가능하다.
        var events=session.events();
        var recent=events.subList(Math.max(0,events.size()-20),events.size());
        var history=com.wontake.chat.ChatService.history(session.messages()); // 범위 밖 문답은 AI 입력에서 뺀다
        states.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue().session.touched>8*3600_000L);
        var state=states.compute(sessionId,(key,old)->old==null || old.session!=session?new State(session):old);
        long now=clock.millis();
        synchronized(state) {
            if(!Boolean.TRUE.equals(store.consent(userId))) return null;
            Active previous=state.active;
            state.active=null;
            if(elementId!=null && catalog.contains(elementId)) {
                var item=catalog.find(elementId);
                if(item.screenId().equals(screenId)) state.active=new Active(item.id(),item.category(),now);
            }
            state.visits.removeIf(v->now-Instant.parse(v.enteredAt()).toEpochMilli()>60000);
            if(previous==null || state.active!=null && previous.menuId.equals(state.active.menuId)) return null;
            long dwell=now-previous.entered;
            if(dwell<0 || dwell>10000) { state.visits.clear(); return null; }
            state.visits.add(new AiClient.Visit(previous.menuId,previous.category,Instant.ofEpochMilli(previous.entered).toString(),Instant.ofEpochMilli(now).toString(),dwell));
            while(state.visits.size()>20) state.visits.removeFirst();
            // 서로 다른 업무를 섞어 탐색한 경우 이전 카테고리의 짧은 방문을 합산하지 않는다.
            int start=state.visits.size()-1;
            while(start>0 && state.visits.get(start-1).category().equals(previous.category)) start--;
            var sameCategory=List.copyOf(state.visits.subList(start,state.visits.size()));
            if(sameCategory.stream().map(AiClient.Visit::menuId).distinct().count()<2 || now-state.lastAnalysis<60000) return null;
            state.lastAnalysis=now;
            state.visits.clear();
            state.pending=new Analysis(screenId,now,new AiClient.BehaviorContext(sameCategory,List.copyOf(recent),history,catalog.all()));
            return state.pending.id;
        }
    }

    public Bubble analyze(String userId,String sessionId,String contextId) {
        var state=state(userId,sessionId);
        if(state==null || !Boolean.TRUE.equals(store.consent(userId)) || store.never(userId)) return null;
        final Analysis analysis;
        synchronized(state) {
            analysis=state.pending;
            if(!matches(analysis,contextId)) return null;
            if(analysis.started) return analysis.bubble;
            analysis.started=true;
        }
        AiClient.ProactiveSuggestion answer;
        try { answer=ai.suggest(redactor==null?analysis.input:redactor.behavior(userId,sessionId,analysis.input)); }
        catch(RuntimeException e) { return null; } // R1 실패 시 팝업을 만들지 않는다. 가짜 제안으로 바꾸지 않는다.
        synchronized(state) {
            if(state.pending!=analysis || !Boolean.TRUE.equals(store.consent(userId)) || store.never(userId) || !valid(answer)) return null;
            analysis.bubble=new Bubble(analysis.id,analysis.screenId,analysis.input.visits().getLast().category(),answer.question(),List.copyOf(answer.menuIds()),ai.mode());
            return analysis.bubble;
        }
    }

    public AiClient.HelpContext context(String userId,String sessionId,String contextId) {
        if(contextId==null || !Boolean.TRUE.equals(store.consent(userId))) return null;
        var state=state(userId,sessionId);
        if(state==null) return null;
        synchronized(state) {
            var a=state.pending;
            if(!matches(a,contextId) || a.bubble==null) return null;
            return new AiClient.HelpContext(a.id,a.bubble.question(),a.input.visits(),a.input.recentEvents());
        }
    }
    /** 상담 요약용: 아직 유효한 마지막 도움 제안의 맥락. 없으면 null. */
    public AiClient.HelpContext latest(String userId,String sessionId) {
        var state=state(userId,sessionId);
        if(state==null) return null;
        synchronized(state) { return state.pending==null?null:context(userId,sessionId,state.pending.id); }
    }
    public void reset(String userId) {
        states.values().stream().filter(s->s.session.userId.equals(userId)).forEach(s->{
            synchronized(s) { s.active=null; s.visits.clear(); s.pending=null; }
        });
    }
    private State state(String userId,String sessionId) {
        var session=store.session(userId,sessionId);
        var state=states.get(sessionId);
        return state!=null && state.session==session?state:null;
    }
    private boolean matches(Analysis a,String id) { return a!=null && a.id.equals(id) && clock.millis()-a.created<=600000; }
    private boolean valid(AiClient.ProactiveSuggestion a) {
        return a!=null && a.shouldOffer() && a.question()!=null && !a.question().isBlank() && a.question().length()<=140
            && a.menuIds()!=null && !a.menuIds().isEmpty() && a.menuIds().size()<=2
            && a.menuIds().stream().allMatch(id->id!=null && catalog.contains(id))
            && a.menuIds().stream().distinct().count()==a.menuIds().size();
    }
}
