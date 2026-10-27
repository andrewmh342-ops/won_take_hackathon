package com.wontake.assist;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties="app.ai.provider=fake")
class PlanFlowTest {
    @Value("${local.server.port}") int port;
    JsonMapper json=new JsonMapper();
    HttpResponse<String> call(String path, String method, Object data) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Content-Type","application/json");
        b.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
        return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
    }

    @Test void 분실_플랜을_만들고_선행_순서대로_실행한다() throws Exception {
        String a=UUID.randomUUID().toString();
        var reply=json.readTree(call("/chat","POST",Map.of("userId",a,"sessionId",a,"message","카드 한 장 잃어버렸어","inputType","TEXT")).body());
        assertEquals("PLAN",reply.path("type").asText());
        var first=reply.path("plan");
        String planId=first.path("planId").asText();
        // 작업 23: 분실 카드를 확인하기 전에는 카드 블록을 실행하지 않는다
        assertTrue(first.path("needsCardConfirm").asBoolean());
        assertEquals(409,call("/plan/execute","POST",Map.of("userId",a,"sessionId",a,"planId",planId,"index",0)).statusCode());
        var confirm=call("/plan/confirm-cards","POST",Map.of("userId",a,"sessionId",a,"planId",planId,"cardRefs",List.of("card-1")));
        assertEquals(200,confirm.statusCode());
        var plan=json.readTree(confirm.body());
        assertFalse(plan.path("needsCardConfirm").asBoolean());
        int lost=-1, reissue=-1;
        for(var block:plan.path("blocks")) {
            if(block.path("blockId").asText().equals("card-lost-report") && lost<0) lost=block.path("index").asInt();
            if(block.path("blockId").asText().equals("card-reissue") && reissue<0) reissue=block.path("index").asInt();
        }
        assertTrue(reissue>lost && lost>=0);
        var early=call("/plan/execute","POST",Map.of("userId",a,"sessionId",a,"planId",planId,"index",reissue));
        assertEquals(409,early.statusCode());
        assertEquals("PLAN_PREREQUISITE",json.readTree(early.body()).path("code").asText());
        var done=call("/plan/execute","POST",Map.of("userId",a,"sessionId",a,"planId",planId,"index",lost));
        assertEquals(200,done.statusCode());
        assertEquals("DONE",json.readTree(done.body()).path("blocks").get(lost).path("status").asText());
        var history=json.readTree(call("/chat/history?userId="+a+"&sessionId="+a,"GET",null).body());
        assertEquals("DONE",history.get(1).path("answer").path("plan").path("blocks").get(lost).path("status").asText());
    }

    @Test void 화면_상태를_읽고_처리하면_플랜에_반영된다() throws Exception {
        String a=UUID.randomUUID().toString();
        var plan=json.readTree(call("/chat","POST",Map.of("userId",a,"sessionId",a,"message","카드 한 장 잃어버렸어","inputType","TEXT")).body()).path("plan");
        var state=json.readTree(call("/demo/state?userId="+a+"&sessionId="+a,"GET",null).body());
        assertEquals(3,state.path("cards").size());
        assertEquals(5,state.path("cards").get(0).path("subscriptions").size()+state.path("cards").get(2).path("subscriptions").size());
        assertEquals(409,call("/demo/action","POST",Map.of("userId",a,"sessionId",a,"action","CARD_REISSUE","ref","card-1")).statusCode());
        var after=json.readTree(call("/demo/action","POST",Map.of("userId",a,"sessionId",a,"action","CARD_STOP","ref","card-1")).body());
        assertEquals("STOPPED",after.path("cards").get(0).path("status").asText());
        assertEquals(1,after.path("plans").path(plan.path("planId").asText()).path("done").asInt());
        var history=json.readTree(call("/chat/history?userId="+a+"&sessionId="+a,"GET",null).body());
        assertEquals("DONE",history.get(1).path("answer").path("plan").path("blocks").get(0).path("status").asText());
    }

    @Test void 시연_고객을_바꾸고_다른_세션_플랜은_막는다() throws Exception {
        String a=UUID.randomUUID().toString(), b=UUID.randomUUID().toString();
        assertEquals(204,call("/demo/profile","PUT",Map.of("userId",a,"sessionId",a,"profileId","B")).statusCode());
        assertEquals(400,call("/demo/profile","PUT",Map.of("userId",a,"sessionId",a,"profileId","Z")).statusCode());
        var plan=json.readTree(call("/chat","POST",Map.of("userId",a,"sessionId",a,"message","일본 여행 준비","inputType","TEXT")).body()).path("plan");
        assertTrue(plan.toString().contains("travel-card-apply"));
        assertFalse(plan.path("aiInput").toString().contains("2593"));
        var other=call("/plan/execute","POST",Map.of("userId",b,"sessionId",b,"planId",plan.path("planId").asText(),"index",0));
        assertEquals(404,other.statusCode());
    }

    // 작업 29: 정보 확인 블록에 답하면 저장하고 AI가 다시 판단한다
    @Test void 출국일을_답하면_다시_판단한다() throws Exception {
        String a=UUID.randomUUID().toString();
        call("/demo/profile","PUT",Map.of("userId",a,"sessionId",a,"profileId","B"));
        var plan=json.readTree(call("/chat","POST",Map.of("userId",a,"sessionId",a,"message","일본 여행 준비","inputType","TEXT")).body()).path("plan");
        int ask=-1;
        for(var block:plan.path("blocks")) if(block.path("blockId").asText().equals("ask-info") && block.path("param").asText().equals("DEPARTURE_DATE")) ask=block.path("index").asInt();
        assertTrue(ask>=0);
        String planId=plan.path("planId").asText();
        String today=json.readTree(call("/demo/state?userId="+a+"&sessionId="+a,"GET",null).body()).path("today").asText();
        var past=call("/plan/answer","POST",Map.of("userId",a,"sessionId",a,"planId",planId,"index",ask,"value",java.time.LocalDate.parse(today).minusDays(1).toString()));
        assertEquals(400,past.statusCode());
        var ok=call("/plan/answer","POST",Map.of("userId",a,"sessionId",a,"planId",planId,"index",ask,"value",java.time.LocalDate.parse(today).plusDays(10).toString()));
        assertEquals(200,ok.statusCode());
        var after=json.readTree(ok.body());
        assertEquals(10,after.path("aiInput").path("customer").path("trip").path("daysUntilDeparture").asInt());
        for(var block:after.path("blocks")) if(block.path("blockId").asText().equals("ask-info")) assertNotEquals("PENDING",block.path("status").asText());
    }

    // 작업 32: 원테이크 화면을 다시 열 때 플랜 조회, [추가로 할 일 찾기]
    @Test void 플랜을_다시_불러오고_추가로_할_일을_찾는다() throws Exception {
        String a=UUID.randomUUID().toString(), b=UUID.randomUUID().toString();
        var plan=json.readTree(call("/chat","POST",Map.of("userId",a,"sessionId",a,"message","다음 달 일본 여행 가는데 준비할 거 알려줘","inputType","TEXT")).body()).path("plan");
        String planId=plan.path("planId").asText();
        int enter=call("/events","POST",Map.of("userId",a,"sessionId",a,"type","SCREEN_ENTER","screenId","plan")).statusCode();
        assertTrue(enter>=200 && enter<300, "원테이크 화면 진입 기록 "+enter);
        var got=call("/plan?userId="+a+"&sessionId="+a+"&planId="+planId,"GET",null);
        assertEquals(200,got.statusCode());
        assertEquals(planId,json.readTree(got.body()).path("planId").asText());
        assertEquals(404,call("/plan?userId="+b+"&sessionId="+b+"&planId="+planId,"GET",null).statusCode());
        var review=call("/plan/review","POST",Map.of("userId",a,"sessionId",a,"planId",planId));
        assertEquals(200,review.statusCode());
        assertEquals(planId,json.readTree(review.body()).path("planId").asText());
        assertEquals(404,call("/plan/review","POST",Map.of("userId",b,"sessionId",b,"planId",planId)).statusCode());
    }
}
