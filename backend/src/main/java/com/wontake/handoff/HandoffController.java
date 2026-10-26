package com.wontake.handoff;

import com.wontake.api.ApiException;
import com.wontake.session.DemoStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 기존 시연 화면 호환. 실제 상담 시스템 전송이나 AI 상담 요약은 수행하지 않는다. */
@RestController
@RequestMapping("/api/handoff")
public class HandoffController {
    public record Input(String userId, String sessionId, Map<String,Object> summary) {}
    private final DemoStore store;
    private final Map<String, Map<String,Object>> drafts = new ConcurrentHashMap<>();
    private final Map<String, Map<String,Object>> tickets = new LinkedHashMap<>();
    private final Map<String,String> receipts = new HashMap<>();
    public HandoffController(DemoStore store) { this.store=store; }
    @PostMapping("/summary") public Object summary(@RequestBody Input body) {
        var session=store.session(body.userId(),body.sessionId());
        synchronized(session) {
            if(drafts.size()>1000) throw new ApiException(503,"DEMO_CAPACITY","시연 서버를 재시작해 주세요.");
            var draft=new LinkedHashMap<String,Object>();
            draft.put("purpose",session.messages().stream().filter(m->m.role().equals("USER")).reduce((a,b)->b).map(m->"고객 마지막 질문: "+m.message()).orElse("확인 안 됨"));
            for(var field:List.of("stuckPoint","triedActions","unresolved")) draft.put(field,"확인 안 됨");
            boolean consent=Boolean.TRUE.equals(store.consent(body.userId()));
            draft.put("clickConsent",consent); draft.put("clickTimeline",consent?session.events():List.of());
            draft.put("conversations",session.messages()); draft.put("source","RECORDED");
            draft.put("draftId",UUID.randomUUID().toString());
            drafts.put(body.sessionId(),draft);
            return draft;
        }
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public synchronized Object submit(@RequestBody Input body) {
        store.session(body.userId(),body.sessionId());
        var draft=drafts.get(body.sessionId());
        if(body.summary()==null || draft==null || !Objects.equals(draft.get("draftId"),body.summary().get("draftId")))
            throw ApiException.invalid("전달할 요약을 먼저 확인해 주세요.");
        String draftId=(String)draft.get("draftId");
        if(receipts.containsKey(draftId)) return receipt(tickets.get(receipts.get(draftId)));
        if(tickets.size()>=1000) throw new ApiException(503,"DEMO_CAPACITY","시연 서버를 재시작해 주세요.");
        var ticket=new LinkedHashMap<>(draft);
        // 요약 확인 뒤 동의를 철회했으면 과거 스냅샷도 전달하지 않는다.
        if(!Boolean.TRUE.equals(store.consent(body.userId()))) { ticket.put("clickConsent",false); ticket.put("clickTimeline",List.of()); }
        String id="T-"+UUID.randomUUID(); ticket.put("ticketId",id); ticket.put("createdAt",Instant.now().toString());
        tickets.put(id,ticket); receipts.put(draftId,id); return receipt(ticket);
    }
    private Object receipt(Map<String,Object> ticket) { return Map.of("ticketId",ticket.get("ticketId"),"createdAt",ticket.get("createdAt")); }
    @GetMapping("/tickets") public synchronized Object tickets() { return List.copyOf(tickets.values()); }
}
