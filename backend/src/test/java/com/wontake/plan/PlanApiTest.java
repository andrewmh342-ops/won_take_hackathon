package com.wontake.plan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties="app.ai.provider=fake")
class PlanApiTest {
    @Value("${local.server.port}") int port;
    JsonMapper json=new JsonMapper();
    HttpResponse<String> post(String path, Object data) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
        return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    /** 작업 목록은 따로 된 API 없이 AI 채팅(/api/chat)에서 만든다(2026-10-27 사용자 결정). */
    HttpResponse<String> chat(String session, String message) throws Exception {
        return post("/chat",Map.of("userId",session,"sessionId",session,"message",message,"inputType","TEXT"));
    }
    @Test void 한_문장으로_작업_카드_3개와_예상_잔액을_받는다() throws Exception {
        String a=UUID.randomUUID().toString();
        var r=chat(a,"월급 들어왔으니 월세 65만원 내고, 50만원은 적금에 넣고, 엄마한테 30만원 보내줘");
        assertEquals(200,r.statusCode());
        var reply=json.readTree(r.body());
        assertEquals("PLAN",reply.path("type").asText());
        var body=reply.path("plan");
        assertEquals(3,body.path("tasks").size());
        assertEquals(1_000_000L,body.path("expectedBalance").asLong());
        assertTrue(body.path("canConfirm").asBoolean());
        assertEquals("FAKE",body.path("mode").asText());
    }
    @Test void 월세_금액을_말하지_않으면_승인하지_않고_금액을_묻는다() throws Exception {
        String a=UUID.randomUUID().toString();
        var body=json.readTree(chat(a,"월세 내고, 엄마한테 30만원 보내줘").body()).path("plan");
        assertEquals("NEEDS_AMOUNT",body.at("/tasks/0/status").asText());
        assertFalse(body.path("canConfirm").asBoolean());
        assertEquals(PlanService.NEEDS_AMOUNT_MESSAGE,body.path("message").asText());
    }
    @Test void long을_넘는_금액은_AI_응답_오류() throws Exception {
        String a=UUID.randomUUID().toString();
        for(var message:List.of("엄마한테 99999999999999999999원","엄마한테 922337203685478만원")) {
            var r=chat(a,message);
            assertEquals(502,r.statusCode(),message);
            assertEquals("AI_INVALID_RESPONSE",json.readTree(r.body()).path("code").asText());
        }
    }
    /** SPEC 3장: 받는 사람이 없는 이체 요청은 지금처럼 메뉴만 안내한다. 작업 목록은 받는 사람을 말했을 때만 만든다. */
    @Test void 받는_사람_없이_이체해_달라고_하면_메뉴만_안내한다() throws Exception {
        String a=UUID.randomUUID().toString();
        var r=post("/chat",Map.of("userId",a,"sessionId",a,"message","5만원 이체해 줘","inputType","TEXT"));
        assertEquals(200,r.statusCode());
        var body=json.readTree(r.body());
        assertEquals("ANSWER",body.path("type").asText());
        assertEquals("instant-transfer",body.path("menuId").asText());
        assertTrue(body.path("plan").isNull());
    }
    /** 가짜 AI도 "누구에게"만으로 이체라고 보지 않는다. 돈을 보내는 말이 함께 있어야 한다. */
    @Test void 상담사에게_연결해_달라는_말은_이체_카드가_아니다() throws Exception {
        for(var message:List.of("상담사에게 연결해 줘","상담원에게 연결해 줘")) {
            String a=UUID.randomUUID().toString();
            var body=json.readTree(chat(a,message).body());
            assertNotEquals("PLAN",body.path("type").asText(),message);
            assertTrue(body.path("plan").isNull(),message);
        }
    }
    @Test void plan_API는_따로_두지_않는다() throws Exception {
        String a=UUID.randomUUID().toString();
        assertEquals(404,post("/plan",Map.of("userId",a,"sessionId",a,"message","엄마한테 1만원")).statusCode());
    }
}
