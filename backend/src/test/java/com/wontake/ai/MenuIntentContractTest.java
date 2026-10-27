package com.wontake.ai;

import com.sun.net.httpserver.HttpServer;
import com.wontake.api.ApiException;
import com.wontake.catalog.MenuCatalog;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** 로컬 HTTP 대역으로 지침·구조화 출력·파싱 계약을 검사한다. 유료 AI 호출 없음. */
class MenuIntentContractTest {
    private HttpServer server;
    private final JsonMapper json = new JsonMapper();
    private final AtomicReference<String> request = new AtomicReference<>();
    private Map<String, Object> answer;
    @BeforeEach void setup() throws Exception {
        answer = new HashMap<>(Map.of("scope", "IN_SCOPE", "reply", "이체내역조회를 열어 드릴게요.", "normalized", "",
            "menuIds", List.of("transfer-history"), "steps", List.of(), "handoff", false, "needsConfirmation", false));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/messages", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = json.writeValueAsBytes(Map.of("stop_reason", "end_turn", "content",
                List.of(Map.of("type", "text", "text", json.writeValueAsString(answer)))));
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
    @AfterEach void close() { server.stop(0); }
    private AiClient.Answer call() {
        var client = new ClaudeAiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/messages"),
            "test-key", "test-model", "", "2023-06-01", Duration.ofSeconds(2));
        return client.answer(new AiClient.Context("최근이체내역 보고싶어", "TEXT", List.of(), List.of(), new MenuCatalog().all()));
    }
    @Test void 유사_표현의_의미_판단과_확인_여부를_구조화해서_요청한다() {
        assertFalse(call().needsConfirmation());
        var body = json.readTree(request.get());
        assertEquals("boolean", body.at("/output_config/format/schema/properties/needsConfirmation/type").asText());
        assertTrue(body.at("/output_config/format/schema/required").toString().contains("needsConfirmation"));
        var prompt = body.path("system").asText();
        assertTrue(prompt.contains("최근이체내역 보고싶어"));
        assertTrue(prompt.contains("최근 거래내역 보고싶어"));
        assertTrue(prompt.contains("정식 명칭"));
        answer.put("needsConfirmation", true);
        assertTrue(call().needsConfirmation());
    }
    @Test void 확인_여부가_누락된_구형_응답은_자동_이동을_허용하지_않는다() {
        answer.remove("needsConfirmation");
        assertTrue(call().needsConfirmation());
    }
    @Test void 확인_여부가_문자열이면_거부한다() {
        answer.put("needsConfirmation", "false");
        assertEquals("AI_INVALID_RESPONSE", assertThrows(ApiException.class, this::call).code);
    }
}
