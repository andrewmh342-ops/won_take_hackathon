package com.wontake.handoff;

import com.wontake.ai.AiClient;
import com.wontake.api.ApiException;
import com.wontake.behavior.BehaviorService;
import com.wontake.catalog.MenuCatalog;
import com.wontake.chat.ChatService;
import com.wontake.session.DemoStore;
import com.wontake.session.UsageGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

/** 상담 전달 초안. AI가 네 항목을 정리하고, 확인되지 않거나 실패한 항목은 "확인 안 됨"으로 둔다. */
@Service
public class HandoffService {
    static final String UNKNOWN = "확인 안 됨";
    private static final List<String> FIELDS = List.of("purpose", "stuckPoint", "triedActions", "unresolved");
    private final DemoStore store;
    private final MenuCatalog catalog;
    private final AiClient ai;
    private final BehaviorService behavior;
    private final UsageGuard guard;
    public HandoffService(DemoStore store, MenuCatalog catalog, AiClient ai) { this(store,catalog,ai,null,new UsageGuard()); }
    @Autowired public HandoffService(DemoStore store, MenuCatalog catalog, AiClient ai, BehaviorService behavior, UsageGuard guard) {
        this.store=store; this.catalog=catalog; this.ai=ai; this.behavior=behavior; this.guard=guard;
    }

    public Map<String,Object> draft(String userId, String sessionId) {
        var session=store.session(userId,sessionId);
        // 요약 중에도 세션 잠금을 잡지 않는다. 스냅샷을 먼저 복사해 AI에 넘긴다.
        var messages=session.messages();
        boolean consent=Boolean.TRUE.equals(store.consent(userId));
        var timeline=consent?session.events():List.<DemoStore.Event>of();
        // 범위 밖 문답은 AI 요약 입력에서 뺀다(상담사 화면의 대화 원문에는 그대로 남는다).
        var turns=ChatService.history(messages);
        var help=behavior==null||!consent?null:behavior.latest(userId,sessionId);
        var draft=new LinkedHashMap<String,Object>();
        try {
            // 정리할 기록이 없으면 AI를 부르지 않는다. 세션당 요약 호출 수도 제한한다(크레딧 보호).
            if(turns.isEmpty() && timeline.isEmpty()) throw new ApiException(200,"NOTHING_TO_SUMMARIZE","정리할 대화가 없어요.");
            if(!guard.summary(sessionId)) throw new ApiException(429,"SUMMARY_LIMIT","요약 횟수를 모두 썼어요.");
            var s=ai.summarize(new AiClient.HandoffContext(turns,timeline,consent,help,catalog.all()));
            var values=s==null?Arrays.<String>asList(null,null,null,null):Arrays.asList(s.purpose(),s.stuckPoint(),s.triedActions(),s.unresolved());
            for(int i=0;i<FIELDS.size();i++) draft.put(FIELDS.get(i),clean(values.get(i)));
            // FAKE 모드 요약은 기록을 규칙으로 옮긴 것이라 AI 요약으로 표시하지 않는다.
            draft.put("source","FAKE".equals(ai.mode())?"RECORDED":"AI");
        } catch(ApiException e) {
            // AI 요약 실패해도 클릭 기록과 대화 원문은 그대로 전달할 수 있다(SPEC R3).
            for(var field:FIELDS) draft.put(field,UNKNOWN);
            draft.put("source","RECORDED"); draft.put("aiError",e.code);
        }
        draft.put("mode",ai.mode());
        draft.put("clickConsent",consent); draft.put("clickTimeline",timeline);
        draft.put("conversations",messages);
        draft.put("draftId",UUID.randomUUID().toString());
        return draft;
    }

    private static String clean(String value) {
        if(value==null || value.isBlank()) return UNKNOWN;
        var text=value.trim();
        return text.length()>200?text.substring(0,200):text;
    }
}
