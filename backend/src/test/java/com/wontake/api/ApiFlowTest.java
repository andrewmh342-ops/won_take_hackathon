package com.wontake.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties="app.ai.provider=fake")
class ApiFlowTest {
    @Value("${local.server.port}") int port;
    JsonMapper json=new JsonMapper();
    HttpResponse<String> call(String path, String method, Object data) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Content-Type","application/json");
        b.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
        return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void fullContextChatFlowAndHistory() throws Exception {
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
        for(var who:List.of(a,b)) assertEquals(204,call("/consent","PUT",Map.of("userId",who,"agreed",true)).statusCode());
        assertEquals(200,call("/events","POST",Map.of("userId",a,"sessionId",a,"type","SCREEN_ENTER","screenId","transfer")).statusCode());
        assertEquals(200,call("/events","POST",Map.of("userId",b,"sessionId",b,"type","SCREEN_ENTER","screenId","exchange")).statusCode());
        var ra=call("/chat","POST",Map.of("userId",a,"sessionId",a,"message","내역","inputType","TEXT"));
        var rb=call("/chat","POST",Map.of("userId",b,"sessionId",b,"message","내역","inputType","VOICE"));
        assertEquals(200,ra.statusCode()); assertEquals(200,rb.statusCode());
        assertEquals("transfer-history",json.readTree(ra.body()).path("menuId").asText());
        assertEquals("exchange-history",json.readTree(rb.body()).path("menuId").asText());
        assertEquals("FAKE",json.readTree(rb.body()).path("mode").asText());
        assertEquals(2,json.readTree(call("/chat/history?userId="+a+"&sessionId="+a,"GET",null).body()).size());
    }
    @Test void noConsentDoesNotInfluenceAnswerAndRevocationClearsIt() throws Exception {
        String a=UUID.randomUUID().toString();
        var event=Map.of("userId",a,"sessionId",a,"type","SCREEN_ENTER","screenId","exchange");
        call("/events","POST",event);
        var question=Map.of("userId",a,"sessionId",a,"message","내역","inputType","TEXT");
        assertEquals(2,json.readTree(call("/chat","POST",question).body()).path("menuIds").size());
        call("/consent","PUT",Map.of("userId",a,"agreed",true)); call("/events","POST",event);
        call("/consent","PUT",Map.of("userId",a,"agreed",false));
        assertEquals(2,json.readTree(call("/chat","POST",question).body()).path("menuIds").size());
    }
    @Test void validatesRequestsAndDoesNotExposeSecrets() throws Exception {
        assertEquals(400,call("/chat","POST",Map.of("message","hello")).statusCode());
        assertEquals(400,call("/consent","PUT",Map.of("userId","x")).statusCode());
        assertEquals(400,call("/events","POST",Map.of("userId","x","sessionId","x","type","CLICK","screenId","login")).statusCode());
        var health=call("/health","GET",null); assertEquals(200,health.statusCode());
        assertEquals("FAKE",json.readTree(health.body()).path("aiMode").asText());
        assertFalse(health.body().contains("api-key"));
        assertEquals(21,json.readTree(call("/menus","GET",null).body()).size());
    }
    @Test void handoffRequiresConfirmedDraftAndKeepsServerSnapshot() throws Exception {
        String a=UUID.randomUUID().toString();
        call("/consent","PUT",Map.of("userId",a,"agreed",true));
        call("/events","POST",Map.of("userId",a,"sessionId",a,"type","SCREEN_ENTER","screenId","exchange"));
        call("/chat","POST",Map.of("userId",a,"sessionId",a,"message","내역","inputType","TEXT"));
        assertEquals(400,call("/handoff","POST",Map.of("userId",a,"sessionId",a,"summary",Map.of("purpose","위조"))).statusCode());
        var draft=json.readTree(call("/handoff/summary","POST",Map.of("userId",a,"sessionId",a)).body());
        assertEquals("RECORDED",draft.path("source").asText());
        call("/consent","PUT",Map.of("userId",a,"agreed",false));
        var body=Map.of("userId",a,"sessionId",a,"summary",Map.of("draftId",draft.path("draftId").asText(),"purpose","위조"));
        var first=call("/handoff","POST",body); var second=call("/handoff","POST",body);
        assertEquals(201,first.statusCode()); assertEquals(first.body(),second.body());
        var tickets=json.readTree(call("/handoff/tickets","GET",null).body());
        var id=json.readTree(first.body()).path("ticketId").asText();
        boolean found=false;
        for(var ticket:tickets) if(id.equals(ticket.path("ticketId").asText())) {
            found=true; assertEquals(0,ticket.path("clickTimeline").size());
            assertFalse(ticket.path("clickConsent").asBoolean());
            assertNotEquals("위조",ticket.path("purpose").asText());
        }
        assertTrue(found);
    }
}
