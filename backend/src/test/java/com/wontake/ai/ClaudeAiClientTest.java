package com.wontake.ai;

import com.sun.net.httpserver.HttpServer;
import com.wontake.api.ApiException;
import com.wontake.catalog.MenuCatalog;
import com.wontake.session.DemoStore;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ClaudeAiClientTest {
    HttpServer server;
    String response;
    int status;
    long delayMillis;
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> key = new AtomicReference<>();
    AtomicReference<String> beta = new AtomicReference<>();
    @BeforeEach void setup() throws Exception {
        status=200;
        response=new JsonMapper().writeValueAsString(Map.of("stop_reason","end_turn","content",List.of(Map.of("type","text","text",
            new JsonMapper().writeValueAsString(Map.of("reply","환전 내역에서 확인하세요.","normalized","","menuIds",List.of("exchange-history"),"steps",List.of()))))));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/messages", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            beta.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            if (delayMillis > 0) {
                try { Thread.sleep(delayMillis); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
    }
    @AfterEach void close() { server.stop(0); }
    ClaudeAiClient client() { return new ClaudeAiClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/messages"), "test-key", "test-model", "", "2023-06-01", Duration.ofSeconds(2)); }
    AiClient.Context context() { return new AiClient.Context("내역", "TEXT", List.of(new AiClient.Turn("USER", "환전하고 싶어요"), new AiClient.Turn("ASSISTANT", "환전 신청 메뉴를 확인하세요")),
        List.of(new DemoStore.Event("SCREEN_ENTER", "exchange", "exchange-apply", null, "2026-10-27T00:00:00Z")), new MenuCatalog().all()); }

    @Test void sendsActualContextAndSchemaAndParsesResponse() {
        var answer=client().answer(context());
        assertEquals(List.of("exchange-history"),answer.menuIds());
        var json=new JsonMapper(); var request=json.readTree(body.get());
        assertEquals("test-key",key.get());
        assertEquals("test-model",request.path("model").asText());
        assertEquals("json_schema",request.at("/output_config/format/type").asText());
        var input=json.readTree(request.at("/messages/0/content").asText());
        assertEquals("내역",input.path("question").asText());
        assertEquals(2,input.path("history").size());
        assertEquals("exchange",input.at("/recentEvents/0/screenId").asText());
        assertEquals(21,input.path("menus").size());
        assertTrue(request.path("system").asText().contains("확인되지" ) || request.path("system").asText().contains("만들어내지"));
    }
    @Test void authFailureDoesNotBecomeFakeAnswerOrLeakProviderBody() {
        status=401; response="test-key private details";
        var e=assertThrows(ApiException.class,()->client().answer(context()));
        assertEquals("AI_AUTH_ERROR",e.code); assertFalse(e.getMessage().contains("test-key"));
    }
    @Test void creditOrConfigurationFailureIsExplicit() {
        status=400; var e=assertThrows(ApiException.class,()->client().answer(context()));
        assertEquals("AI_CONFIGURATION_ERROR",e.code);
    }
    @Test void rateLimitIsExplicit() {
        status=429; assertEquals("AI_RATE_LIMIT",assertThrows(ApiException.class,()->client().answer(context())).code);
    }
    @Test void truncatedAndRefusedOutputsAreRejected() {
        response=response.replace("end_turn","max_tokens");
        assertEquals("AI_INVALID_RESPONSE",assertThrows(ApiException.class,()->client().answer(context())).code);
        response="{\"stop_reason\":\"refusal\",\"content\":[]}";
        assertThrows(ApiException.class,()->client().answer(context()));
    }
    @Test void malformedOutputIsRejected() {
        response="{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"not JSON\"}]}";
        assertEquals("AI_INVALID_RESPONSE",assertThrows(ApiException.class,()->client().answer(context())).code);
    }
    @Test void timeoutDoesNotReturnFakeAnswer() {
        delayMillis=500;
        var client=new ClaudeAiClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/messages"),
            "test-key","test-model","","2023-06-01",Duration.ofMillis(100));
        assertEquals("AI_TIMEOUT",assertThrows(ApiException.class,()->client.answer(context())).code);
    }
    @Test void unknownModelIsExplicit() {
        status=404;
        assertEquals("AI_MODEL_ERROR",assertThrows(ApiException.class,()->client().answer(context())).code);
    }
    @Test void answerSchemaAsksHandoffJudgementAndMenusCarryGuides() {
        var mapper=new JsonMapper();
        response=mapper.writeValueAsString(Map.of("stop_reason","end_turn","content",List.of(Map.of("type","text","text",
            mapper.writeValueAsString(Map.of("reply","상담사에게 이어서 물어보실 수 있어요.","normalized","","menuIds",List.of("auth-center"),"steps",List.of(),"handoff",true))))));
        assertTrue(client().answer(context()).handoff());
        var request=mapper.readTree(body.get());
        assertTrue(request.at("/output_config/format/schema/properties/handoff").isObject());
        var menu=mapper.readTree(request.at("/messages/0/content").asText()).at("/menus/0");
        assertTrue(menu.path("guide").size()>0);
        assertTrue(menu.path("commonIssues").size()>0);
    }
    @Test void chatAsksScopeAndLeavesRoomForThinking() {
        var mapper=new JsonMapper();
        response=mapper.writeValueAsString(Map.of("stop_reason","end_turn","content",List.of(Map.of("type","text","text",
            mapper.writeValueAsString(Map.of("reply","","normalized","","menuIds",List.of(),"steps",List.of(),"handoff",false,"scope","OUT_OF_SCOPE"))))));
        assertEquals("OUT_OF_SCOPE",client().answer(context()).scope());
        var request=mapper.readTree(body.get());
        var scope=request.at("/output_config/format/schema/properties/scope/enum");
        assertEquals(2,scope.size());
        assertEquals("low",request.at("/output_config/effort").asText());
        assertTrue(request.path("max_tokens").asInt()>=4000);
        assertTrue(request.path("system").asText().contains("금융앱"));
        assertFalse(request.has("temperature"));
    }
    @Test void refusalIsReportedSeparately() {
        response="{\"stop_reason\":\"refusal\",\"content\":[]}";
        assertEquals("AI_REFUSED",assertThrows(ApiException.class,()->client().answer(context())).code);
    }
    @Test void serverWideCallBudgetStopsBeforeSending() {
        var limited=new ClaudeAiClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/messages"),
            "test-key","test-model","","2023-06-01",Duration.ofSeconds(2),1,false);
        limited.answer(context());
        body.set(null);
        assertEquals("AI_BUDGET_EXCEEDED",assertThrows(ApiException.class,()->limited.answer(context())).code);
        assertNull(body.get());
    }
    @Test void fallbacksOptInAddsBetaHeader() {
        var withFallback=new ClaudeAiClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/messages"),
            "test-key","test-model","","2023-06-01",Duration.ofSeconds(2),10,true);
        withFallback.answer(context());
        assertEquals("server-side-fallback-2026-07-01",beta.get());
        assertEquals("default",new JsonMapper().readTree(body.get()).path("fallbacks").asText());
    }
    @Test void summarizeUsesHandoffPromptAndFourFieldSchema() {
        var mapper=new JsonMapper();
        response=mapper.writeValueAsString(Map.of("stop_reason","end_turn","content",List.of(Map.of("type","text","text",
            mapper.writeValueAsString(Map.of("purpose","이체한도 확인","stuckPoint","","triedActions","이체 화면 방문","unresolved","한도 변경 방법"))))));
        var ctx=new AiClient.HandoffContext(context().history(),context().recentEvents(),true,null,context().menus());
        var result=client().summarize(ctx);
        assertEquals("이체한도 확인",result.purpose());
        var request=mapper.readTree(body.get());
        assertTrue(request.path("system").asText().contains("상담사"));
        var schema=request.at("/output_config/format/schema");
        assertEquals(4,schema.path("properties").size());
        assertFalse(schema.path("additionalProperties").asBoolean());
        var input=mapper.readTree(request.at("/messages/0/content").asText());
        assertEquals("환전하고 싶어요",input.at("/conversation/0/message").asText());
        assertEquals("exchange",input.at("/clickTimeline/0/screenId").asText());
        response="{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"{\\\"purpose\\\":1}\"}]}";
        assertEquals("AI_INVALID_RESPONSE",assertThrows(ApiException.class,()->client().summarize(ctx)).code);
    }
    @Test void proactiveRequestContainsVisitOrderAndUsesItsOwnPromptAndSchema() {
        var mapper=new JsonMapper();
        response=mapper.writeValueAsString(Map.of("stop_reason","end_turn","content",List.of(Map.of("type","text","text",
            mapper.writeValueAsString(Map.of("shouldOffer",true,"question","이체 메뉴를 찾는 데 도움이 필요하세요?","menuIds",List.of("instant-transfer")))))));
        var visits=List.of(new AiClient.Visit("instant-transfer","이체/출금","2026-10-27T00:00:00Z","2026-10-27T00:00:01Z",1000),
            new AiClient.Visit("overseas-remittance","이체/출금","2026-10-27T00:00:02Z","2026-10-27T00:00:03Z",1000));
        var result=client().suggest(new AiClient.BehaviorContext(visits,context().recentEvents(),context().history(),context().menus()));
        assertTrue(result.shouldOffer());
        var request=mapper.readTree(body.get());
        assertTrue(request.path("system").asText().contains("먼저 도움"));
        assertTrue(request.at("/output_config/format/schema/properties/shouldOffer").isObject());
        var input=mapper.readTree(request.at("/messages/0/content").asText());
        assertEquals("instant-transfer",input.at("/visits/0/menuId").asText());
        assertEquals("overseas-remittance",input.at("/visits/1/menuId").asText());
        assertEquals(1000,input.at("/visits/0/dwellMillis").asLong());
    }
}
