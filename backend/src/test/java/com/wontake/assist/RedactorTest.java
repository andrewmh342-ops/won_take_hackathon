package com.wontake.assist;

import com.wontake.ai.AiClient;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 작업 25: AI에 가는 모든 입력을 같은 규칙으로 가명화한다(SPEC R5 보완 규칙 표 한 행 = 테스트 하나). */
class RedactorTest {
    private final DemoStore store = new DemoStore();
    private final ProfileStore profiles = new ProfileStore(store);
    private final Redactor redactor = new Redactor(profiles);
    private String r(String text) { return redactor.text("a", "sa", text); }

    @Test void 고객이_친_끝자리는_카드_ref로_바꾼다() {
        assertEquals("card-1 카드 잃어버렸어", r("4821 카드 잃어버렸어"));
    }

    @Test void 보유_카드_이름은_카드_ref로_바꾼다() {
        assertEquals("card-1 잃어버렸어", r("우리 신용카드 잃어버렸어"));
        assertEquals("card-2랑 card-3 둘 다", r("우리 트래블 체크카드랑 우리 체크카드 둘 다"));
        assertEquals("card-1 정지", r("우리 신용카드 끝자리 4821(가상) 정지"));
        assertEquals("card-1 정지", r("우리 신용카드 끝자리 4821 정지"));
        assertEquals("card-1 잃어버렸어", r("우리 신용카드 4821 잃어버렸어")); // 이름과 끝자리를 함께 말해도 ref 하나
    }

    @Test void 열자리_이상_번호는_번호로_바꾼다() {
        assertEquals("[번호] 카드요", r("1234-5678-9012-3456 카드요"));
        assertEquals("전화 [번호]", r("전화 010-1234-5678"));
        assertEquals("계좌 [번호]", r("계좌 1002 123 456789"));
    }

    @Test void 금액은_금액으로_바꾼다() {
        assertEquals("[금액] 나갔어", r("55,000원 나갔어"));
        assertEquals("[금액] 나갔어", r("55000 원 나갔어"));
        assertEquals("[금액]쯤", r("5만 원쯤"));
    }

    @Test void 구독_가맹점명은_구독_ref로_바꾼다() {
        assertEquals("ap-1 결제 옮겨 줘", r("넷플릭스 결제 옮겨 줘"));
        assertEquals("ap-2이랑 ap-4", r("유튜브 프리미엄이랑 멜론"));
    }

    @Test void 짧은_숫자는_그대로_둔다() {
        assertEquals("2026년 3시 30일 1장", r("2026년 3시 30일 1장"));
        assertEquals("출국 2026-11-03", r("출국 2026-11-03"));
    }

    @Test void 시연_고객이_바뀌면_그_고객의_카드로_바꾼다() {
        profiles.select("a", "sa", "B");
        assertEquals("card-1 잃어버렸어, 4821은 없어요", r("2593 잃어버렸어, 4821은 없어요"));
    }

    @Test void 이전_대화와_행동_기록과_말풍선_맥락도_같은_규칙() {
        var turns = redactor.turns("a", "sa", List.of(new AiClient.Turn("USER", "넷플릭스 55,000원")));
        assertEquals("ap-1 [금액]", turns.getFirst().message());
        var events = redactor.events("a", "sa", List.of(new DemoStore.Event("SEARCH", "menu", "search", "4821 분실", "t")));
        assertEquals("card-1 분실", events.getFirst().query());
        var help = redactor.help("a", "sa", new AiClient.HelpContext("h", "우리 체크카드 문제인가요?", List.of(), List.of(new DemoStore.Event("SEARCH", "menu", "search", "010-1234-5678", "t"))));
        assertEquals("card-3 문제인가요?", help.question());
        assertEquals("[번호]", help.recentEvents().getFirst().query());
        assertNull(redactor.help("a", "sa", null));
    }
}
