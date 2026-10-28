package com.wontake.behavior;

import com.wontake.ai.*;
import com.wontake.catalog.MenuCatalog;
import com.wontake.session.DemoStore;
import com.wontake.chat.ChatService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BehaviorServiceTest {
    static class TestClock extends Clock {
        long now=1_000_000;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
    final TestClock clock=new TestClock();
    final DemoStore store=new DemoStore();
    final MenuCatalog catalog=new MenuCatalog();
    final AtomicInteger calls=new AtomicInteger();
    final AiClient ai=new FakeAiClient() {
        @Override public ProactiveSuggestion suggest(BehaviorContext c) {
            calls.incrementAndGet();
            assertEquals(List.of("instant-transfer","overseas-remittance"), c.visits().stream().map(Visit::menuId).toList());
            return new ProactiveSuggestion(true,"이체나 송금 메뉴를 찾고 계신가요?",List.of("instant-transfer","overseas-remittance"));
        }
    };
    final BehaviorService behavior=new BehaviorService(store,catalog,ai,clock);
    String enter(String menuId) {
        var item=catalog.find(menuId);
        return behavior.record("u","s","SCREEN_ENTER",item.screenId(),menuId,null);
    }
    String exit() { return behavior.record("u","s","SCREEN_ENTER","menu",null,null); }
    String flow(long dwell) {
        enter("instant-transfer"); clock.now+=dwell; assertNull(exit());
        enter("overseas-remittance"); clock.now+=dwell; return exit();
    }
    @Test void differentMenusInSameCategoryTriggerAiOnExitNotClickCounts() {
        store.consent("u",true);
        String id=flow(1000); assertNotNull(id); assertEquals(0,calls.get());
        var bubble=behavior.analyze("u","s",id);
        assertNotNull(bubble); assertEquals(1,calls.get());
        assertEquals("이체나 송금 메뉴를 찾고 계신가요?",bubble.question());
        assertNotNull(behavior.analyze("u","s",id)); assertEquals(1,calls.get());
        var context=behavior.context("u","s",id);
        assertEquals(2,context.visits().size());
        assertTrue(context.recentEvents().stream().anyMatch(e->"overseas-remittance".equals(e.elementId())));
    }
    @Test void tenSecondBoundaryIsIncludedButLongerVisitsDoNotTrigger() {
        store.consent("u",true); assertNotNull(flow(10000));
        behavior.reset("u"); clock.now+=60001;
        assertNull(flow(10001));
    }
    @Test void sameMenuRepeatedAndMixedCategoriesDoNotTrigger() {
        store.consent("u",true);
        for(int i=0;i<4;i++) { enter("instant-transfer"); clock.now+=1000; assertNull(exit()); }
        behavior.reset("u");
        enter("instant-transfer"); clock.now+=1000; exit();
        enter("exchange-apply"); clock.now+=1000; assertNull(exit());
    }
    @Test void olderThanSixtySecondsDoesNotTrigger() {
        store.consent("u",true);
        enter("instant-transfer"); clock.now+=1000; exit(); clock.now+=60001;
        enter("overseas-remittance"); clock.now+=1000; assertNull(exit());
    }
    @Test void consentIsRequiredAndRevocationDropsSnapshot() {
        assertNull(flow(1000)); assertEquals(0,calls.get());
        store.consent("u",true); String id=flow(1000);
        store.consent("u",false); behavior.reset("u");
        assertNull(behavior.analyze("u","s",id)); assertNull(behavior.context("u","s",id));
    }
    String enterInPlan(String menuId) {
        var item=catalog.find(menuId);
        return behavior.record("u","s","SCREEN_ENTER",item.screenId(),menuId,null,true);
    }
    @Test void planInProgressSkipsAnalysisButKeepsEventsAndLeavesNoCooldown() {
        store.consent("u",true);
        // 플랜 진행 중: 카드 업무 화면을 빠르게 오가도 분석하지 않는다(AI 호출 없음)
        assertNull(enterInPlan("card-loss-report")); clock.now+=1000;
        assertNull(enterInPlan("simple-pay")); clock.now+=1000;
        assertNull(enterInPlan("card-reissue")); clock.now+=1000;
        assertNull(behavior.record("u","s","SCREEN_ENTER","plan",null,null,true));
        assertEquals(0,calls.get());
        assertTrue(store.session("u","s").events().size()>=4, "상담 요약용 행동 기록은 그대로 남는다");
        // 플랜을 끝낸 직후(60초 안)에도 같은 카드 분류를 헤매면 바로 분석한다: 플랜 중 방문은 합치지도, 대기 시간을 쓰지도 않는다
        assertNull(enter("card-loss-report")); clock.now+=1000;
        assertNull(enter("simple-pay")); clock.now+=1000;
        assertNotNull(exit());
    }
    @Test void cooldownIsPerCategorySoAnotherCategoryCanBeAnalyzedWithinSixtySeconds() {
        store.consent("u",true); assertNotNull(flow(1000)); // 이체/출금
        enter("exchange-apply"); clock.now+=1000; assertNull(exit());
        enter("exchange-rate"); clock.now+=1000; assertNotNull(exit()); // 환전: 60초 안이어도 다른 분류라 분석
        assertNull(flow(1000)); // 이체/출금은 아직 60초 안이라 분석 안 함
        clock.now+=60001;
        assertNotNull(flow(1000)); // 60초가 지나면 다시 분석
    }
    @Test void cooldownAndNeverPreventRepeatedAnalyses() {
        store.consent("u",true); assertNotNull(flow(1000));
        assertNull(flow(1000));
        store.close("u","s","menu","NEVER"); behavior.reset("u"); clock.now+=60001;
        assertNull(flow(1000));
    }
    @Test void invalidAiMenuNeverBecomesInventedBubble() {
        store.consent("u",true);
        AiClient invalid=new FakeAiClient() {
            public ProactiveSuggestion suggest(BehaviorContext c) { return new ProactiveSuggestion(true,"도와드릴까요?",List.of("fake-menu")); }
        };
        var service=new BehaviorService(store,catalog,invalid,clock);
        service.record("u","s","SCREEN_ENTER","transfer","instant-transfer",null); clock.now+=1;
        service.record("u","s","SCREEN_ENTER","menu",null,null);
        service.record("u","s","SCREEN_ENTER","transfer","overseas-remittance",null); clock.now+=1;
        String id=service.record("u","s","SCREEN_ENTER","menu",null,null);
        assertNull(service.analyze("u","s",id));
    }
    @Test void popupSnapshotReachesTextAndVoiceEvenAfterTwentyMoreClicks() {
        store.consent("u",true); String id=flow(1000); behavior.analyze("u","s",id);
        for(int i=0;i<25;i++) behavior.record("u","s","CLICK","chat","voice-start",null);
        AiClient inspect=context->{
            assertEquals(id,context.helpContext().id());
            assertEquals(List.of("instant-transfer","overseas-remittance"),context.helpContext().visits().stream().map(AiClient.Visit::menuId).toList());
            assertEquals("한도 알려줘",context.question());
            return new AiClient.Answer("이체한도 조회/변경 메뉴를 확인해 보세요.","한도를 알려 주세요",List.of("transfer-limit-manage"),List.of());
        };
        var chat=new ChatService(store,catalog,inspect,behavior);
        assertEquals("transfer-limit-manage",chat.chat("u","s","한도 알려줘","TEXT",id).menuId());
        assertEquals("transfer-limit-manage",chat.chat("u","s","한도 알려줘","VOICE",id).menuId());
        store.consent("u",false); behavior.reset("u");
        assertNull(behavior.context("u","s",id));
    }
    @Test void sixtySecondWindowIncludesBoundaryAndExcludesOlderStart() {
        store.consent("u",true);
        enter("instant-transfer"); clock.now+=1; exit(); clock.now+=59998;
        enter("overseas-remittance"); clock.now+=1; assertNotNull(exit());
        behavior.reset("u"); clock.now+=60001;
        enter("instant-transfer"); clock.now+=1; exit(); clock.now+=59999;
        enter("overseas-remittance"); clock.now+=1; assertNull(exit());
    }
    @Test void aiCanDeclineAndAnalysisFailureDoesNotCreateFakePopup() {
        store.consent("u",true);
        var decline=new BehaviorService(store,catalog,c->null,clock);
        decline.record("u","s","SCREEN_ENTER","transfer","instant-transfer",null); clock.now++;
        decline.record("u","s","SCREEN_ENTER","menu",null,null);
        decline.record("u","s","SCREEN_ENTER","transfer","overseas-remittance",null); clock.now++;
        String id=decline.record("u","s","SCREEN_ENTER","menu",null,null);
        assertNull(decline.analyze("u","s",id));
        assertNull(decline.context("u","s",id));
    }
    @Test void providerFailureDoesNotFallbackToCategoryTemplate() {
        store.consent("u",true);
        var failed=new BehaviorService(store,catalog,new FakeAiClient() {
            @Override public ProactiveSuggestion suggest(BehaviorContext c) { throw new RuntimeException("provider unavailable"); }
        },clock);
        failed.record("u","s","SCREEN_ENTER","transfer","instant-transfer",null); clock.now++;
        failed.record("u","s","SCREEN_ENTER","menu",null,null);
        failed.record("u","s","SCREEN_ENTER","transfer","overseas-remittance",null); clock.now++;
        String id=failed.record("u","s","SCREEN_ENTER","menu",null,null);
        assertNull(failed.analyze("u","s",id));
        assertNull(failed.context("u","s",id));
    }

    // 작업 39: 역질문과 함께 예상 질문 최대 3개
    BehaviorService with(List<String> questions) {
        return new BehaviorService(store, catalog, new FakeAiClient() {
            @Override public ProactiveSuggestion suggest(BehaviorContext c) {
                return new ProactiveSuggestion(true, "이체나 송금 메뉴를 찾고 계신가요?", List.of("instant-transfer"), questions);
            }
        }, clock);
    }
    Behavior2 flowOn(BehaviorService service) {
        var a = catalog.find("instant-transfer"); var b = catalog.find("overseas-remittance");
        service.record("u", "s", "SCREEN_ENTER", a.screenId(), a.id(), null); clock.now += 1000;
        service.record("u", "s", "SCREEN_ENTER", "menu", null, null);
        service.record("u", "s", "SCREEN_ENTER", b.screenId(), b.id(), null); clock.now += 1000;
        return new Behavior2(service, service.record("u", "s", "SCREEN_ENTER", "menu", null, null));
    }
    record Behavior2(BehaviorService service, String id) {}

    @Test void 예상_질문은_앞의_3개만_쓴다() {
        store.consent("u", true);
        var f = flowOn(with(List.of("한도는 얼마예요?", "해외로 보내려면요?", "예약은 어떻게 해요?", "수수료가 있나요?")));
        var bubble = f.service().analyze("u", "s", f.id());
        assertEquals(List.of("한도는 얼마예요?", "해외로 보내려면요?", "예약은 어떻게 해요?"), bubble.questions());
    }

    @Test void 빈_질문과_60자_초과와_중복은_버린다() {
        store.consent("u", true);
        var f = flowOn(with(Arrays.asList("  ", "가".repeat(61), " 한도는 얼마예요? ", "한도는 얼마예요?", null, "해외로 보내려면요?")));
        assertEquals(List.of("한도는 얼마예요?", "해외로 보내려면요?"), f.service().analyze("u", "s", f.id()).questions());
    }

    @Test void 예상_질문이_없어도_역질문_말풍선은_뜬다() {
        store.consent("u", true);
        var f = flowOn(with(List.of()));
        var bubble = f.service().analyze("u", "s", f.id());
        assertNotNull(bubble);
        assertEquals(List.of(), bubble.questions());
    }

    @Test void 모의_AI도_방문한_분류의_예상_질문_3개를_만든다() {
        store.consent("u", true);
        var f = flowOn(new BehaviorService(store, catalog, new FakeAiClient(), clock));
        var questions = f.service().analyze("u", "s", f.id()).questions();
        assertEquals(3, questions.size());
        assertTrue(questions.stream().allMatch(q -> !q.isBlank() && q.length() <= 60));
    }
}
