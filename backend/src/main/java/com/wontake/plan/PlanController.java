package com.wontake.plan;

import org.springframework.web.bind.annotation.*;

/** 작업 카드 승인. 목록은 채팅(/api/chat)에서만 만들고, 여기서는 저장된 목록을 승인만 한다. */
@RestController("transferPlanController")
@RequestMapping("/api/plan")
public class PlanController {
    public record ConfirmInput(String userId, String sessionId, String planId, String password) {}
    private final PlanService plans;
    public PlanController(PlanService plans) { this.plans = plans; }
    @PostMapping("/confirm") public Object confirm(@RequestBody ConfirmInput body) {
        return plans.confirm(body.userId(), body.sessionId(), body.planId(), body.password());
    }
}
