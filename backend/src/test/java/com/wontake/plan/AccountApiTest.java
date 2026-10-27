package com.wontake.plan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 실제 AI를 호출하지 않고 채팅 승인과 계좌 조회 사이의 고객별 잔액 계약을 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.ai.provider=fake")
class AccountApiTest {
    @Value("${local.server.port}") int port;
    private final JsonMapper json = new JsonMapper();
    private HttpResponse<String> get(String query) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + "/api/accounts" + query)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode accounts(String user) throws Exception {
        var response = get("?userId=" + user);
        assertEquals(200, response.statusCode());
        return json.readTree(response.body());
    }
    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + "/api" + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void approve(String user) throws Exception {
        var chat = post("/chat", Map.of("userId", user, "sessionId", user, "message", "엄마한테 30만원 보내줘", "inputType", "TEXT"));
        assertEquals(200, chat.statusCode());
        String planId = json.readTree(chat.body()).at("/plan/planId").asText();
        assertFalse(planId.isBlank());
        assertEquals(200, post("/plan/confirm", Map.of("userId", user, "sessionId", user,
            "planId", planId, "password", "123456")).statusCode());
    }
    @Test void 처음_조회하면_가상_계좌와_초기_잔액을_받는다() throws Exception {
        var body = accounts(UUID.randomUUID().toString());
        assertEquals(2, body.path("accounts").size());
        assertEquals("WON 생활통장", body.at("/accounts/0/name").asText());
        assertEquals("DEMO-ACCOUNT-01", body.at("/accounts/0/number").asText());
        assertTrue(body.at("/accounts/0/withdrawal").asBoolean());
        assertEquals(2_450_000L, body.at("/accounts/0/balance").asLong());
        assertEquals(5_800_000L, body.at("/accounts/1/balance").asLong());
        assertEquals(8_250_000L, body.path("totalBalance").asLong());
    }
    @Test void 승인한_뒤_조회하면_생활통장과_전체_잔액이_감소한다() throws Exception {
        String user = UUID.randomUUID().toString();
        approve(user);
        var body = accounts(user);
        assertEquals(2_150_000L, body.at("/accounts/0/balance").asLong());
        assertEquals(7_950_000L, body.path("totalBalance").asLong());
    }
    @Test void 한_고객의_승인은_다른_고객의_조회_잔액을_바꾸지_않는다() throws Exception {
        approve(UUID.randomUUID().toString());
        var other = accounts(UUID.randomUUID().toString());
        assertEquals(2_450_000L, other.at("/accounts/0/balance").asLong());
        assertEquals(8_250_000L, other.path("totalBalance").asLong());
    }
    @Test void 복합_승인에서_적금이_증가하고_전체_잔액은_외부송금만큼_감소한다() throws Exception {
        String user = UUID.randomUUID().toString();
        var chat = post("/chat", Map.of("userId", user, "sessionId", user, "message", "월세 65만원 내고, 50만원은 적금에 넣고, 엄마한테 30만원 보내줘", "inputType", "TEXT"));
        assertEquals(200, chat.statusCode());
        String planId = json.readTree(chat.body()).at("/plan/planId").asText();
        var input = Map.of("userId", user, "sessionId", user, "planId", planId, "password", "123456");
        assertEquals(200, post("/plan/confirm", input).statusCode());
        var body = accounts(user);
        assertEquals(1_000_000L, body.at("/accounts/0/balance").asLong());
        assertEquals(6_300_000L, body.at("/accounts/1/balance").asLong());
        assertEquals(7_300_000L, body.path("totalBalance").asLong());
        assertEquals(409, post("/plan/confirm", input).statusCode());
        assertEquals(body, accounts(user));
        assertEquals(5_800_000L, accounts(UUID.randomUUID().toString()).at("/accounts/1/balance").asLong());
    }
    @Test void 고객_ID가_없거나_형식이_틀리면_400이다() throws Exception {
        for (String query : List.of("", "?userId=", "?userId=bad%21"))
            assertEquals(400, get(query).statusCode(), query);
    }
}
