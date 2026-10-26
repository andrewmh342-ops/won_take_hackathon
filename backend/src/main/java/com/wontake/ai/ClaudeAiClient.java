package com.wontake.ai;

import com.wontake.api.ApiException;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class ClaudeAiClient implements AiClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = new JsonMapper();
    private final URI endpoint;
    private final String key, model, workspace, version, system, behaviorSystem;
    private final Duration timeout;
    public ClaudeAiClient(URI endpoint, String key, String model, String workspace, String version, Duration timeout) {
        this.endpoint=endpoint; this.key=key; this.model=model; this.workspace=workspace; this.version=version; this.timeout=timeout;
        system=readPrompt("chat-system.txt");
        behaviorSystem=readPrompt("behavior-system.txt");
    }
    private String readPrompt(String name) {
        try (var stream = getClass().getResourceAsStream("/ai/"+name)) {
            if (stream==null) throw new IllegalStateException("AI 지침 파일 없음");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("AI 지침을 읽지 못했습니다.", e); }
    }
    @Override public String mode() { return "CLAUDE"; }
    @Override public Answer answer(Context context) {
            var properties = Map.of(
                "reply", Map.of("type", "string"),
                "normalized", Map.of("type", "string"),
                "menuIds", Map.of("type", "array", "items", Map.of("type", "string", "enum", context.menus().stream().map(m -> m.id()).toList())),
                "steps", Map.of("type", "array", "items", Map.of("type", "string")));
            var schema = Map.of("type", "object", "properties", properties, "required", List.of("reply", "normalized", "menuIds", "steps"), "additionalProperties", false);
            var value=call(context,schema,system);
            if (!value.isObject() || value.size()!=4 || !value.path("reply").isTextual() || !value.path("normalized").isTextual()
                || !value.path("menuIds").isArray() || !value.path("steps").isArray()) throw invalid();
            for (var node : value.path("menuIds")) if (!node.isTextual()) throw invalid();
            for (var node : value.path("steps")) if (!node.isTextual()) throw invalid();
            return json.treeToValue(value, Answer.class);
    }
    @Override public ProactiveSuggestion suggest(BehaviorContext context) {
        var properties=Map.of("shouldOffer",Map.of("type","boolean"),"question",Map.of("type","string"),
            "menuIds",Map.of("type","array","items",Map.of("type","string","enum",context.menus().stream().map(m->m.id()).toList())));
        var schema=Map.of("type","object","properties",properties,"required",List.of("shouldOffer","question","menuIds"),"additionalProperties",false);
        var value=call(context,schema,behaviorSystem);
        if(!value.isObject() || value.size()!=3 || !value.path("shouldOffer").isBoolean() || !value.path("question").isTextual() || !value.path("menuIds").isArray()) throw invalid();
        for(var node:value.path("menuIds")) if(!node.isTextual()) throw invalid();
        return json.treeToValue(value,ProactiveSuggestion.class);
    }
    private tools.jackson.databind.JsonNode call(Object context,Map<String,?> schema,String prompt) {
        try {
            var body = Map.of("model", model, "max_tokens", 1200, "system", prompt,
                "messages", List.of(Map.of("role", "user", "content", json.writeValueAsString(context))),
                "output_config", Map.of("format", Map.of("type", "json_schema", "schema", schema)));
            var builder = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json").header("x-api-key", key).header("anthropic-version", version);
            if (!workspace.isBlank()) builder.header("anthropic-workspace-id", workspace);
            var request = builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            // 외부 응답 본문에는 요청 정보가 포함될 수 있으므로 사용자·로그에 그대로 노출하지 않는다.
            if (status==401 || status==403) throw new ApiException(502, "AI_AUTH_ERROR", "Claude API 키와 워크스페이스 권한을 확인해 주세요.");
            if (status==429) throw new ApiException(503, "AI_RATE_LIMIT", "Claude 호출 한도에 도달했어요. 잠시 후 다시 시도해 주세요.");
            if (status==400 || status==402) throw new ApiException(502, "AI_CONFIGURATION_ERROR", "Claude 크레딧·모델·API 설정을 확인해 주세요.");
            if (status==404) throw new ApiException(502, "AI_MODEL_ERROR", "Claude 모델 ID와 사용 권한을 확인해 주세요.");
            if (status!=200) throw new ApiException(503, "AI_UNAVAILABLE", "Claude 서비스에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
            var root = json.readTree(response.body());
            if (!"end_turn".equals(root.path("stop_reason").asText())) throw invalid();
            var text = new StringBuilder();
            for (var block : root.path("content")) if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText());
            if (text.isEmpty() || text.length()>12000) throw invalid();
            return json.readTree(text.toString());
        } catch (ApiException e) { throw e; }
        catch (HttpTimeoutException e) { throw new ApiException(504, "AI_TIMEOUT", "AI 응답 시간이 길어지고 있어요. 다시 시도해 주세요."); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(503, "AI_INTERRUPTED", "AI 요청이 중단됐어요. 다시 시도해 주세요."); }
        catch (java.io.IOException e) { throw new ApiException(503, "AI_CONNECTION_ERROR", "Claude에 연결하지 못했어요. 네트워크를 확인해 주세요."); }
        catch (RuntimeException e) { throw invalid(); }
    }
    private ApiException invalid() { return new ApiException(502, "AI_INVALID_RESPONSE", "AI 응답을 확인하지 못했어요. 다시 시도해 주세요."); }
}
