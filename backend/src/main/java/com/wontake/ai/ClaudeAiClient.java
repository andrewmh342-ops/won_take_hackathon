package com.wontake.ai;

import com.wontake.api.ApiException;
import com.wontake.assist.BlockCatalog;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClaudeAiClient implements AiClient {
    /** Sonnet 5.5는 기본으로 사고(thinking)하고 그 토큰도 max_tokens에 들어간다. 답이 잘리지 않게 여유를 둔다. */
    static final int MAX_TOKENS = 8000;
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
                "scope", Map.of("type", "string", "enum", Answer.CHAT_SCOPES),
                "needsConfirmation", Map.of("type", "boolean"),
                "plan", planSchema(context));
            var schema = Map.of("type", "object", "properties", properties, "required", List.of("scope", "reply", "normalized", "menuIds", "steps", "handoff", "plan", "needsConfirmation"), "additionalProperties", false);
            var value=call(context,schema,system,"medium"); // 목표·장애물을 엮는 판단(R5 목표 중심)
            // schema가 scope·handoff를 요구하지만, 빠진 응답은 범위 안·강조 없음으로 본다.
            boolean hasHandoff=value.has("handoff"), hasScope=value.has("scope"), hasPlan=value.has("plan"), hasConfirmation=value.has("needsConfirmation");
            if (!value.isObject() || value.size()!=4+(hasHandoff?1:0)+(hasScope?1:0)+(hasPlan?1:0)+(hasConfirmation?1:0) || !value.path("reply").isTextual() || !value.path("normalized").isTextual()
                || !value.path("menuIds").isArray() || !value.path("steps").isArray() || hasHandoff && !value.path("handoff").isBoolean()
                || hasConfirmation && !value.path("needsConfirmation").isBoolean()
                || hasScope && !Answer.CHAT_SCOPES.contains(value.path("scope").asText())) throw invalid();
            var menuIds=new ArrayList<String>(); var steps=new ArrayList<String>();
            for (var node : value.path("menuIds")) { if (!node.isTextual()) throw invalid(); menuIds.add(node.asText()); }
            for (var node : value.path("steps")) { if (!node.isTextual()) throw invalid(); steps.add(node.asText()); }
            return new Answer(value.path("reply").asText(), value.path("normalized").asText(), menuIds, steps, value.path("handoff").asBoolean(false),
                hasScope ? value.path("scope").asText() : Answer.IN_SCOPE, hasPlan ? parsePlan(value.path("plan")) : AiPlan.EMPTY,
                hasConfirmation ? value.path("needsConfirmation").asBoolean() : !menuIds.isEmpty());
    }
    private static final List<String> DEFAULT_BLOCK_IDS = new BlockCatalog().ids();
    /** R5 플랜 schema. blockId는 블록 사전 enum이라 사전 밖 블록을 지어낼 수 없다. 목표·장애물·제외 이유·필수/선택을 함께 받는다. */
    private static Map<String,Object> planSchema(Context context) {
        var ids = context.blocks() != null ? context.blocks().stream().map(BlockInfo::id).toList() : DEFAULT_BLOCK_IDS;
        var text = Map.of("type", "string");
        var block = Map.of("type", "object", "properties", Map.of("blockId", Map.of("type", "string", "enum", ids), "targetRef", text, "param", text, "reason", text,
                "priority", Map.of("type", "string", "enum", List.of("REQUIRED", "OPTIONAL"))),
            "required", List.of("blockId", "targetRef", "param", "reason", "priority"), "additionalProperties", false);
        var goal = Map.of("type", "object", "properties", Map.of("type", Map.of("type", "string", "enum", List.of("CARD_PROTECTION", "PAYMENT_ABROAD", "CASH_ABROAD", "OTHER")),
                "text", text, "required", Map.of("type", "boolean")),
            "required", List.of("type", "text", "required"), "additionalProperties", false);
        var obstacle = Map.of("type", "object", "properties", Map.of("text", text, "resolution", Map.of("type", "string", "enum", List.of("BLOCK", "QUESTION", "HANDOFF")),
                "blockIndexes", Map.of("type", "array", "items", Map.of("type", "integer"))),
            "required", List.of("text", "resolution", "blockIndexes"), "additionalProperties", false);
        var considered = Map.of("type", "object", "properties", Map.of("blockId", Map.of("type", "string", "enum", ids), "reason", text),
            "required", List.of("blockId", "reason"), "additionalProperties", false);
        var props = new LinkedHashMap<String, Object>();
        props.put("events", Map.of("type", "array", "items", text));
        props.put("goals", Map.of("type", "array", "items", goal));
        props.put("obstacles", Map.of("type", "array", "items", obstacle));
        props.put("blocks", Map.of("type", "array", "items", block));
        props.put("considered", Map.of("type", "array", "items", considered));
        props.put("unhandled", Map.of("type", "array", "items", text));
        props.put("orderReason", text);
        return Map.of("type", "object", "properties", props, "required", List.copyOf(props.keySet()), "additionalProperties", false);
    }
    private AiPlan parsePlan(tools.jackson.databind.JsonNode p) {
        if (!p.isObject() || !p.path("events").isArray() || !p.path("blocks").isArray() || !p.path("unhandled").isArray() || !p.path("orderReason").isTextual()) throw invalid();
        var events = new ArrayList<String>(); var unhandled = new ArrayList<String>(); var blocks = new ArrayList<AiBlock>();
        for (var n : p.path("events")) { if (!n.isTextual()) throw invalid(); events.add(n.asText()); }
        for (var n : p.path("unhandled")) { if (!n.isTextual()) throw invalid(); unhandled.add(n.asText()); }
        for (var n : p.path("blocks")) {
            if (!n.isObject() || !n.path("blockId").isTextual() || !n.path("targetRef").isTextual() || !n.path("param").isTextual() || !n.path("reason").isTextual()) throw invalid();
            String priority = n.path("priority").isTextual() ? n.path("priority").asText() : "REQUIRED"; // 표시가 없으면 필수
            blocks.add(new AiBlock(n.path("blockId").asText(), n.path("targetRef").asText(), n.path("param").asText(), n.path("reason").asText(), priority));
        }
        // 목표·장애물·제외 이유는 없어도 된다(이전 형식 호환). 있으면 모양을 검사한다.
        var goals = new ArrayList<AiGoal>(); var obstacles = new ArrayList<AiObstacle>(); var considered = new ArrayList<AiConsidered>();
        for (var n : p.path("goals")) {
            if (!n.path("type").isTextual() || !n.path("text").isTextual()) throw invalid();
            goals.add(new AiGoal(n.path("type").asText(), n.path("text").asText(), n.path("required").asBoolean(true)));
        }
        for (var n : p.path("obstacles")) {
            if (!n.path("text").isTextual()) throw invalid();
            var indexes = new ArrayList<Integer>();
            for (var i : n.path("blockIndexes")) if (i.isIntegralNumber()) indexes.add(i.asInt());
            obstacles.add(new AiObstacle(n.path("text").asText(), n.path("resolution").asText("BLOCK"), indexes));
        }
        for (var n : p.path("considered")) {
            if (!n.path("blockId").isTextual() || !n.path("reason").isTextual()) throw invalid();
            considered.add(new AiConsidered(n.path("blockId").asText(), n.path("reason").asText()));
        }
        return new AiPlan(events, blocks, unhandled, p.path("orderReason").asText(), goals, obstacles, considered);
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
            "amountRef",amountSchema(context.amountValues()),"memo",Map.of("type","string")),
            "required",List.of("target","amountRef","memo"),"additionalProperties",false);
        var schema=Map.of("type","object","properties",Map.of("scope",Map.of("type","string","enum",PlanDraft.PLAN_SCOPES),
            "tasks",Map.of("type","array","items",task)),"required",List.of("scope","tasks"),"additionalProperties",false);
        var value=call(Map.of("request",context.request(),"payees",context.payees()),schema,planSystem,"low");
        if(!value.isObject() || value.size()!=2 || !PlanDraft.PLAN_SCOPES.contains(value.path("scope").asText())
            || !value.path("tasks").isArray()) throw invalid();
        var tasks=new ArrayList<TaskDraft>();
        for(var node:value.path("tasks")) {
            // AI가 숫자를 새로 만들지 못하게 입력에 있는 참조만 복원한다.
            var amount=node.path("amountRef");
            if(!node.isObject() || node.size()!=3 || !node.path("target").isTextual() || !node.path("memo").isTextual()
                || !(amount.isNull() || amount.isTextual())) throw invalid();
            tasks.add(new TaskDraft(node.path("target").asText(),TransferAmounts.resolve(amount.isNull()?null:amount.asText(), context.amountValues()),node.path("memo").asText()));
        }
        return new PlanDraft(value.path("scope").asText(),tasks);
    }
    /** target은 물어본 받는 곳 중에서만 고르게 스키마로 막는다. 같은 곳이 두 번 오는 것은 PlanService가 막는다. */
    @Override public List<AmountDraft> fill(FillContext context) {
        var item=Map.of("type","object","properties",Map.of("target",Map.of("type","string","enum",context.targets()),
            "amountRef",amountSchema(context.amountValues())),"required",List.of("target","amountRef"),"additionalProperties",false);
        var schema=Map.of("type","object","properties",Map.of("amounts",Map.of("type","array","items",item)),
            "required",List.of("amounts"),"additionalProperties",false);
        var value=call(Map.of("targets",context.targets(),"message",context.message()),schema,fillSystem,"low");
        if(!value.isObject() || value.size()!=1 || !value.path("amounts").isArray()) throw invalid();
        var amounts=new ArrayList<AmountDraft>();
        for(var node:value.path("amounts")) {
            var amount=node.path("amountRef");
            if(!node.isObject() || node.size()!=2 || !node.path("target").isTextual()
                || !(amount.isNull() || amount.isTextual())) throw invalid();
            amounts.add(new AmountDraft(node.path("target").asText(),TransferAmounts.resolve(amount.isNull()?null:amount.asText(), context.amountValues())));
        }
        return amounts;
    }
    /** 입력에 있던 금액 참조나 null만 고르게 한다. Claude는 type 배열 ["string","null"]에 문자열 enum을 붙이면 400을 주고(2026-10-27 실측) 빈 enum도 받지 않으므로 anyOf로 나눈다. */
    private static Map<String, Object> amountSchema(Map<String, Long> values) {
        Map<String, Object> nullOnly = Map.of("type", "null");
        if (values.isEmpty()) return nullOnly;
        return Map.of("anyOf", List.of(Map.of("type", "string", "enum", List.copyOf(values.keySet())), nullOnly));
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
