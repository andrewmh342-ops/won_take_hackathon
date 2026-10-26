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
}
