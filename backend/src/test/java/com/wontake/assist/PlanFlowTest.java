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
        var plan=reply.path("plan");
        String planId=plan.path("planId").asText();
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
}
