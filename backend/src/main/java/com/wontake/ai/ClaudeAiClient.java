package com.wontake.ai;

import com.wontake.api.ApiException;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClaudeAiClient implements AiClient {
    /** Sonnet 5.5는 기본으로 사고(thinking)하고 그 토큰도 max_tokens에 들어간다. 답이 잘리지 않게 여유를 둔다. */
    static final int MAX_TOKENS = 4096;
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = new JsonMapper();
    private final URI endpoint;
    private final String key, model, workspace, version, system, behaviorSystem, handoffSystem, planSystem, fillSystem;
    private final Duration timeout;
    private final int maxCalls;
    private final boolean fallbacks;
    private final AtomicInteger calls = new AtomicInteger();
    public ClaudeAiClient(URI endpoint, String key, String model, String workspace, String version, Duration timeout) {
        this(endpoint,key,model,workspace,version,timeout,Integer.MAX_VALUE,false);
    }
    /** maxCalls: 서버를 켠 뒤 Claude를 부를 수 있는 총 횟수(크레딧 보호). fallbacks: 안전 거절 시 서버 측 대체 모델 재시도. */
    public ClaudeAiClient(URI endpoint, String key, String model, String workspace, String version, Duration timeout, int maxCalls, boolean fallbacks) {
        this.endpoint=endpoint; this.key=key; this.model=model; this.workspace=workspace; this.version=version; this.timeout=timeout;
        this.maxCalls=maxCalls; this.fallbacks=fallbacks;
        system=readPrompt("chat-system.txt");
        behaviorSystem=readPrompt("behavior-system.txt");
        handoffSystem=readPrompt("handoff-system.txt");
        planSystem=readPrompt("plan-system.txt");
        fillSystem=readPrompt("fill-system.txt");
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
                "steps", Map.of("type", "array", "items", Map.of("type", "string")),
                "handoff", Map.of("type", "boolean"),
                "scope", Map.of("type", "string", "enum", Answer.CHAT_SCOPES));
            var schema = Map.of("type", "object", "properties", properties, "required", List.of("scope", "reply", "normalized", "menuIds", "steps", "handoff"), "additionalProperties", false);
            var value=call(context,schema,system,"low");
            // schema가 scope·handoff를 요구하지만, 빠진 응답은 범위 안·강조 없음으로 본다.
            boolean hasHandoff=value.has("handoff"), hasScope=value.has("scope");
            if (!value.isObject() || value.size()!=4+(hasHandoff?1:0)+(hasScope?1:0) || !value.path("reply").isTextual() || !value.path("normalized").isTextual()
                || !value.path("menuIds").isArray() || !value.path("steps").isArray() || hasHandoff && !value.path("handoff").isBoolean()
                || hasScope && !Answer.CHAT_SCOPES.contains(value.path("scope").asText())) throw invalid();
            var menuIds=new ArrayList<String>(); var steps=new ArrayList<String>();
            for (var node : value.path("menuIds")) { if (!node.isTextual()) throw invalid(); menuIds.add(node.asText()); }
            for (var node : value.path("steps")) { if (!node.isTextual()) throw invalid(); steps.add(node.asText()); }
            return new Answer(value.path("reply").asText(), value.path("normalized").asText(), menuIds, steps, value.path("handoff").asBoolean(false),
                hasScope ? value.path("scope").asText() : Answer.IN_SCOPE);
    }
    @Override public ProactiveSuggestion suggest(BehaviorContext context) {
        var properties=Map.of("shouldOffer",Map.of("type","boolean"),"question",Map.of("type","string"),
            "menuIds",Map.of("type","array","items",Map.of("type","string","enum",context.menus().stream().map(m->m.id()).toList())));
        var schema=Map.of("type","object","properties",properties,"required",List.of("shouldOffer","question","menuIds"),"additionalProperties",false);
        var value=call(context,schema,behaviorSystem,"low");
        if(!value.isObject() || value.size()!=3 || !value.path("shouldOffer").isBoolean() || !value.path("question").isTextual() || !value.path("menuIds").isArray()) throw invalid();
        for(var node:value.path("menuIds")) if(!node.isTextual()) throw invalid();
        return json.treeToValue(value,ProactiveSuggestion.class);
    }
    @Override public HandoffSummary summarize(HandoffContext context) {
        var fields=List.of("purpose","stuckPoint","triedActions","unresolved");
        var properties=new LinkedHashMap<String,Object>();
        for(var field:fields) properties.put(field,Map.of("type","string"));
        var schema=Map.of("type","object","properties",properties,"required",fields,"additionalProperties",false);
        var value=call(context,schema,handoffSystem,"medium");
        if(!value.isObject() || value.size()!=4) throw invalid();
        for(var field:fields) if(!value.path(field).isTextual()) throw invalid();
        return json.treeToValue(value,HandoffSummary.class);
    }
    @Override public PlanDraft plan(PlanContext context) {
        var task=Map.of("type","object","properties",Map.of("target",Map.of("type","string"),
            "amount",Map.of("type",List.of("integer","null")),"memo",Map.of("type","string")),
            "required",List.of("target","amount","memo"),"additionalProperties",false);
        var schema=Map.of("type","object","properties",Map.of("scope",Map.of("type","string","enum",PlanDraft.PLAN_SCOPES),
            "tasks",Map.of("type","array","items",task)),"required",List.of("scope","tasks"),"additionalProperties",false);
        var value=call(context,schema,planSystem,"low");
        if(!value.isObject() || value.size()!=2 || !PlanDraft.PLAN_SCOPES.contains(value.path("scope").asText())
            || !value.path("tasks").isArray()) throw invalid();
        var tasks=new ArrayList<TaskDraft>();
        for(var node:value.path("tasks")) {
            // 금액은 long만 받는다. "50만원" 같은 글자나 소수, long을 넘는 수는 오류로 본다.
            var amount=node.path("amount");
            if(!node.isObject() || node.size()!=3 || !node.path("target").isTextual() || !node.path("memo").isTextual()
                || !(amount.isNull() || amount.isIntegralNumber() && amount.canConvertToLong())) throw invalid();
            tasks.add(new TaskDraft(node.path("target").asText(),amount.isNull()?null:amount.longValue(),node.path("memo").asText()));
        }
        return new PlanDraft(value.path("scope").asText(),tasks);
    }
    /** target은 물어본 받는 곳 중에서만 고르게 스키마로 막는다. 같은 곳이 두 번 오는 것은 PlanService가 막는다. */
    @Override public List<AmountDraft> fill(FillContext context) {
        var item=Map.of("type","object","properties",Map.of("target",Map.of("type","string","enum",context.targets()),
            "amount",Map.of("type",List.of("integer","null"))),"required",List.of("target","amount"),"additionalProperties",false);
        var schema=Map.of("type","object","properties",Map.of("amounts",Map.of("type","array","items",item)),
            "required",List.of("amounts"),"additionalProperties",false);
        var value=call(context,schema,fillSystem,"low");
        if(!value.isObject() || value.size()!=1 || !value.path("amounts").isArray()) throw invalid();
        var amounts=new ArrayList<AmountDraft>();
        for(var node:value.path("amounts")) {
            var amount=node.path("amount");
            if(!node.isObject() || node.size()!=2 || !node.path("target").isTextual()
                || !(amount.isNull() || amount.isIntegralNumber() && amount.canConvertToLong())) throw invalid();
            amounts.add(new AmountDraft(node.path("target").asText(),amount.isNull()?null:amount.longValue()));
        }
        return amounts;
    }
    private tools.jackson.databind.JsonNode call(Object context,Map<String,?> schema,String prompt,String effort) {
        // 서버 전체 호출 상한: 넘으면 요청을 보내지 않는다.
        if (calls.incrementAndGet() > maxCalls) throw new ApiException(503, "AI_BUDGET_EXCEEDED", "오늘 준비된 AI 사용량을 모두 썼어요. 상담사에게 연결해 주세요.");
        try {
            var body = new LinkedHashMap<String,Object>();
            body.put("model", model); body.put("max_tokens", MAX_TOKENS); body.put("system", prompt);
            body.put("messages", List.of(Map.of("role", "user", "content", json.writeValueAsString(context))));
            body.put("output_config", Map.of("effort", effort, "format", Map.of("type", "json_schema", "schema", schema)));
            if (fallbacks) body.put("fallbacks", "default");
            var builder = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json").header("x-api-key", key).header("anthropic-version", version);
            if (fallbacks) builder.header("anthropic-beta", FALLBACK_BETA);
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
            String stop = root.path("stop_reason").asText();
            if ("refusal".equals(stop)) throw new ApiException(502, "AI_REFUSED", "이 질문은 안내할 수 없어요.");
            if (!"end_turn".equals(stop)) throw invalid();
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
