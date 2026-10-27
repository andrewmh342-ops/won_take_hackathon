package com.wontake.assist;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.wontake.chat.ChatService;
import java.util.List;
import java.util.Map;

/** R5 플랜 실행과 시연 고객 전환. */
@RestController
@RequestMapping("/api")
public class PlanController {
    public record ExecuteInput(String userId, String sessionId, String planId, Integer index, Map<String, String> options) {}
    public record ProfileInput(String userId, String sessionId, String profileId) {}
    public record ConfirmInput(String userId, String sessionId, String planId, List<String> cardRefs) {}
    public record AnswerInput(String userId, String sessionId, String planId, Integer index, String value) {}
    public record PlanRef(String userId, String sessionId, String planId) {}
    private final PlanService plans;
    private final ProfileStore profiles;
    private final DemoActionService demo;
    private final ChatService chat;
    public PlanController(PlanService plans, ProfileStore profiles, DemoActionService demo, ChatService chat) {
        this.plans = plans; this.profiles = profiles; this.demo = demo; this.chat = chat;
    }

    /** 작업 23: 고객이 잃어버린 카드를 직접 확인한다. 카드 블록을 다시 구성하고 AI가 다시 판단한다. */
    @PostMapping("/plan/confirm-cards") public Plan confirm(@RequestBody ConfirmInput body) {
        return chat.confirmCards(body.userId(), body.sessionId(), body.planId(), body.cardRefs());
    }

    /** 작업 29: 정보 확인 블록(출국일·나라)에 답한다. 저장 후 AI가 다시 판단한다. */
    @PostMapping("/plan/answer") public Plan answer(@RequestBody AnswerInput body) {
        return chat.answer(body.userId(), body.sessionId(), body.planId(), body.index(), body.value());
    }

    /** 작업 32: 원테이크 화면을 다시 열 때 현재 플랜. */
    @GetMapping("/plan") public Plan plan(@RequestParam String userId, @RequestParam String sessionId, @RequestParam String planId) {
        return plans.get(userId, sessionId, planId);
    }
    /** 작업 32: [추가로 할 일 찾기] — 바뀐 상태로 AI가 다시 판단한다. */
    @PostMapping("/plan/review") public Plan review(@RequestBody PlanRef body) {
        return chat.review(body.userId(), body.sessionId(), body.planId());
    }

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
