package com.wontake.assist;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** R5 플랜 실행과 시연 고객 전환. */
@RestController
@RequestMapping("/api")
public class PlanController {
    public record ExecuteInput(String userId, String sessionId, String planId, Integer index, Map<String, String> options) {}
    public record ProfileInput(String userId, String sessionId, String profileId) {}
    private final PlanService plans;
    private final ProfileStore profiles;
    private final DemoActionService demo;
    public PlanController(PlanService plans, ProfileStore profiles, DemoActionService demo) { this.plans = plans; this.profiles = profiles; this.demo = demo; }

    /** R5 업무 화면용 복원 상태(고객 본인 화면). AI 입력에는 쓰지 않는다. */
    @GetMapping("/demo/state") public Map<String, Object> state(@RequestParam String userId, @RequestParam String sessionId) {
        return demo.state(userId, sessionId);
    }
    @PostMapping("/demo/action") public Map<String, Object> action(@RequestBody Map<String, Object> body) {
        return demo.act(String.valueOf(body.get("userId")), String.valueOf(body.get("sessionId")), body);
    }

    @PostMapping("/plan/execute") public Plan execute(@RequestBody ExecuteInput body) {
        return plans.execute(body.userId(), body.sessionId(), body.planId(), body.index(), body.options());
    }
    @PutMapping("/demo/profile") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void profile(@RequestBody ProfileInput body) { profiles.select(body.userId(), body.sessionId(), body.profileId()); }
}
