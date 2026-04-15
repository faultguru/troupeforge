package com.troupeforge.infra.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.troupeforge.core.llm.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ClaudeLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmProvider.class);

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_RETRIES = 3;
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 500, 502, 503);
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final List<String> credentialPaths;
    private final HttpClient httpClient;

    public ClaudeLlmProvider(ObjectMapper objectMapper, ClaudeProviderConfig config) {
        this(objectMapper, config.baseUrl(), config.credentialPaths());
    }

    public ClaudeLlmProvider(ObjectMapper objectMapper, String baseUrl, List<String> credentialPaths) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.credentialPaths = credentialPaths;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public boolean supports(String model) {
        return model != null && model.toLowerCase(Locale.ROOT).contains("claude");
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        try {
            log.info("LLM request sent: model={}, messageCount={}, toolCount={}",
                    request.model(),
                    request.messages() != null ? request.messages().size() : 0,
                    request.tools() != null ? request.tools().size() : 0);

            Credential credential = resolveCredential();
            String requestBody = buildRequestBody(request);

            if (log.isDebugEnabled()) {
                log.debug("LLM API request:\n{}", prettyPrint(requestBody));
            }

            long startNanos = System.nanoTime();

            String apiUrl;
            HttpRequest.Builder httpBuilder = HttpRequest.newBuilder()
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofMinutes(5));

            apiUrl = baseUrl + "/v1/messages";
            httpBuilder.uri(URI.create(apiUrl));
            httpBuilder.header("x-api-key", credential.token());

            HttpRequest httpRequest = httpBuilder.build();

            HttpResponse<String> httpResponse = null;
            for (int attempt = 0; ; attempt++) {
                httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (httpResponse.statusCode() == 200) {
                    break;
                }

                int statusCode = httpResponse.statusCode();
                if (RETRYABLE_STATUS_CODES.contains(statusCode) && attempt < MAX_RETRIES) {
                    long delay = Math.min(
                            INITIAL_BACKOFF_MS * (1L << attempt) + ThreadLocalRandom.current().nextLong(500),
                            MAX_BACKOFF_MS);
                    log.warn("LLM request retry {}/{}: status={}, retrying in {}ms",
                            attempt + 1, MAX_RETRIES, statusCode, delay);
                    Thread.sleep(delay);
                    continue;
                }

                log.warn("LLM non-200 response: status={}, body={}", statusCode, httpResponse.body());
                throw new LlmApiException(statusCode, httpResponse.body(),
                        RETRYABLE_STATUS_CODES.contains(statusCode));
            }

            long elapsedNanos = System.nanoTime() - startNanos;
            Duration latency = Duration.ofNanos(elapsedNanos);

            if (log.isDebugEnabled()) {
                log.debug("LLM API response:\n{}", prettyPrint(httpResponse.body()));
            }

            LlmResponse llmResponse = parseResponse(httpResponse.body(), latency);
            log.info("LLM response received: status=200, latencyMs={}, inputTokens={}, outputTokens={}, finishReason={}",
                    latency.toMillis(),
                    llmResponse.usage() != null ? llmResponse.usage().inputTokens() : 0,
                    llmResponse.usage() != null ? llmResponse.usage().outputTokens() : 0,
                    llmResponse.finishReason());

            return llmResponse;
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call Anthropic API: {}", e.getMessage(), e);
            throw new LlmApiException("Failed to call Anthropic API", e);
        }
    }

    @Override
    public CostEstimate estimateCost(String model, TokenUsage usage) {
        // Per-million-token pricing for Anthropic models (as of 2025)
        BigDecimal inputRate;
        BigDecimal outputRate;

        String modelLower = model != null ? model.toLowerCase(Locale.ROOT) : "";
        if (modelLower.contains("opus")) {
            inputRate = new BigDecimal("15.00");   // $15/MTok input
            outputRate = new BigDecimal("75.00");   // $75/MTok output
        } else if (modelLower.contains("sonnet")) {
            inputRate = new BigDecimal("3.00");    // $3/MTok input
            outputRate = new BigDecimal("15.00");   // $15/MTok output
        } else if (modelLower.contains("haiku")) {
            inputRate = new BigDecimal("0.25");    // $0.25/MTok input
            outputRate = new BigDecimal("1.25");    // $1.25/MTok output
        } else {
            // Unknown model - use Sonnet pricing as default
            inputRate = new BigDecimal("3.00");
            outputRate = new BigDecimal("15.00");
        }

        BigDecimal million = new BigDecimal("1000000");
        BigDecimal inputCost = inputRate.multiply(BigDecimal.valueOf(usage.inputTokens())).divide(million, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal outputCost = outputRate.multiply(BigDecimal.valueOf(usage.outputTokens())).divide(million, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal totalCost = inputCost.add(outputCost);

        return new CostEstimate(model, usage, inputCost, outputCost, totalCost, "USD");
    }

    @Override
    public Flux<LlmStreamEvent> stream(LlmRequest request) {
        return Flux.create(sink -> {
            try {
                Credential credential = resolveCredential();
                String requestBody = buildStreamRequestBody(request);

                long startNanos = System.nanoTime();

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/messages"))
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .header("x-api-key", credential.token())
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofMinutes(5))
                        .build();

                HttpResponse<java.io.InputStream> httpResponse = httpClient.send(httpRequest,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (httpResponse.statusCode() != 200) {
                    String body = new String(httpResponse.body().readAllBytes(), StandardCharsets.UTF_8);
                    sink.error(new LlmApiException(httpResponse.statusCode(), body,
                            RETRYABLE_STATUS_CODES.contains(httpResponse.statusCode())));
                    return;
                }

                processStreamResponse(sink, httpResponse, startNanos);

            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    private void processStreamResponse(FluxSink<LlmStreamEvent> sink,
                                         HttpResponse<java.io.InputStream> httpResponse,
                                         long startNanos) {
        StringBuilder contentBuilder = new StringBuilder();
        Map<Integer, StreamingToolCall> toolCallBuilders = new LinkedHashMap<>();
        String model = "";
        String stopReason = "";
        int inputTokens = 0, outputTokens = 0, cacheReadTokens = 0, cacheCreationTokens = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(httpResponse.body(), StandardCharsets.UTF_8))) {
            String line;
            String currentEvent = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event: ")) {
                    currentEvent = line.substring(7).trim();
                } else if (line.startsWith("data: ") && currentEvent != null) {
                    String data = line.substring(6);
                    JsonNode eventData = objectMapper.readTree(data);

                    switch (currentEvent) {
                        case "message_start" -> {
                            JsonNode msg = eventData.path("message");
                            model = msg.path("model").asText("");
                            JsonNode usageNode = msg.path("usage");
                            inputTokens = usageNode.path("input_tokens").asInt(0);
                            cacheReadTokens = usageNode.path("cache_read_input_tokens").asInt(0);
                            cacheCreationTokens = usageNode.path("cache_creation_input_tokens").asInt(0);
                        }
                        case "content_block_start" -> {
                            JsonNode block = eventData.path("content_block");
                            int index = eventData.path("index").asInt(0);
                            String type = block.path("type").asText();
                            if ("tool_use".equals(type)) {
                                toolCallBuilders.put(index, new StreamingToolCall(
                                        block.path("id").asText(),
                                        block.path("name").asText(),
                                        new StringBuilder()));
                            }
                        }
                        case "content_block_delta" -> {
                            JsonNode delta = eventData.path("delta");
                            String deltaType = delta.path("type").asText();
                            int index = eventData.path("index").asInt(0);
                            if ("text_delta".equals(deltaType)) {
                                String text = delta.path("text").asText("");
                                contentBuilder.append(text);
                                sink.next(new LlmStreamEvent.ContentDelta(text));
                            } else if ("input_json_delta".equals(deltaType)) {
                                String chunk = delta.path("partial_json").asText("");
                                StreamingToolCall tc = toolCallBuilders.get(index);
                                if (tc != null) {
                                    tc.argsBuilder.append(chunk);
                                    sink.next(new LlmStreamEvent.ToolCallDelta(tc.id, tc.name, chunk));
                                }
                            }
                        }
                        case "message_delta" -> {
                            stopReason = eventData.path("delta").path("stop_reason").asText("");
                            JsonNode usageNode = eventData.path("usage");
                            outputTokens = usageNode.path("output_tokens").asInt(0);
                        }
                        case "message_stop" -> {
                            // Build final response
                            Duration latency = Duration.ofNanos(System.nanoTime() - startNanos);
                            List<ToolCall> toolCalls = new ArrayList<>();
                            for (StreamingToolCall tc : toolCallBuilders.values()) {
                                Map<String, Object> args = tc.argsBuilder.length() > 0
                                        ? objectMapper.readValue(tc.argsBuilder.toString(),
                                        new TypeReference<Map<String, Object>>() {})
                                        : Map.of();
                                toolCalls.add(new ToolCall(tc.id, tc.name, args));
                            }

                            FinishReason finishReason = switch (stopReason) {
                                case "end_turn" -> FinishReason.STOP;
                                case "tool_use" -> FinishReason.TOOL_USE;
                                case "max_tokens" -> FinishReason.MAX_TOKENS;
                                default -> FinishReason.ERROR;
                            };

                            TokenUsage usage = new TokenUsage(inputTokens, outputTokens,
                                    inputTokens + outputTokens, cacheReadTokens, cacheCreationTokens);
                            LlmResponse response = new LlmResponse(contentBuilder.toString(),
                                    finishReason, usage, toolCalls, model, latency);
                            sink.next(new LlmStreamEvent.Complete(response));
                            sink.complete();
                        }
                        case "error" -> {
                            String errorMsg = eventData.path("error").path("message").asText("Unknown streaming error");
                            sink.error(new LlmApiException(errorMsg, null));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Stream processing error: {}", e.getMessage(), e);
            sink.error(e);
        }
    }

    private record StreamingToolCall(String id, String name, StringBuilder argsBuilder) {}

    private String buildStreamRequestBody(LlmRequest request) throws Exception {
        ObjectNode body = objectMapper.readValue(buildRequestBody(request), ObjectNode.class);
        body.put("stream", true);
        return objectMapper.writeValueAsString(body);
    }

    // ---- credential loading ----

    private record Credential(String token, AuthType type) {}

    private enum AuthType { API_KEY, OAUTH }

    private Credential resolveCredential() {
        // 1. Check ANTHROPIC_API_KEY env var
        String envKey = System.getenv("ANTHROPIC_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            log.info("Using API key from ANTHROPIC_API_KEY environment variable");
            return new Credential(envKey, AuthType.API_KEY);
        }

        // 2. Check credential files for OAuth token
        for (String credentialPath : credentialPaths) {
            Path path = resolvePath(credentialPath);
            log.debug("Trying credential path: {}", path);
            if (Files.exists(path)) {
                try {
                    String json = Files.readString(path);
                    JsonNode root = objectMapper.readTree(json);
                    JsonNode oauth = root.path("claudeAiOauth");
                    if (!oauth.isMissingNode()) {
                        JsonNode token = oauth.path("accessToken");
                        if (!token.isMissingNode() && token.isTextual()) {
                            log.info("Using OAuth token from {}", path);
                            return new Credential(token.asText(), AuthType.OAUTH);
                        }
                    }
                } catch (IOException e) {
                    // try next path
                }
            }
        }
        throw new LlmApiException(
                "No credentials found. Set ANTHROPIC_API_KEY env var or provide OAuth credentials. Searched paths: " + credentialPaths,
                null);
    }

    private static Path resolvePath(String pathStr) {
        if (pathStr.startsWith("~/") || pathStr.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home")).resolve(pathStr.substring(2));
        }
        return Path.of(pathStr);
    }

    // ---- request building ----

    private String buildRequestBody(LlmRequest request) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());

        if (request.temperature() != 0.0) {
            body.put("temperature", request.temperature());
        }

        // Extract system messages
        String systemText = extractSystemText(request.messages());
        if (systemText != null && !systemText.isEmpty()) {
            body.put("system", systemText);
        }

        // Convert messages (non-SYSTEM)
        ArrayNode messagesArray = body.putArray("messages");
        for (LlmMessage message : request.messages()) {
            if (message.role() == MessageRole.SYSTEM) {
                continue;
            }
            messagesArray.add(convertMessage(message));
        }

        // Convert tools
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (ToolDefinition tool : request.tools()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema",
                        objectMapper.valueToTree(tool.inputSchema()));
                toolsArray.add(toolNode);
            }
        }

        return objectMapper.writeValueAsString(body);
    }

    private String extractSystemText(List<LlmMessage> messages) {
        if (messages == null) return null;
        StringJoiner joiner = new StringJoiner("\n");
        boolean found = false;
        for (LlmMessage msg : messages) {
            if (msg.role() == MessageRole.SYSTEM) {
                for (MessageContent content : msg.content()) {
                    if (content instanceof MessageContent.Text text) {
                        joiner.add(text.text());
                        found = true;
                    }
                }
            }
        }
        return found ? joiner.toString() : null;
    }

    private ObjectNode convertMessage(LlmMessage message) {
        ObjectNode node = objectMapper.createObjectNode();

        // TOOL role messages map to "user" role with tool_result content blocks
        String apiRole = switch (message.role()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "user";
            case SYSTEM -> throw new IllegalStateException("SYSTEM messages should be filtered out");
        };
        node.put("role", apiRole);

        ArrayNode contentArray = node.putArray("content");
        for (MessageContent part : message.content()) {
            contentArray.add(convertContentBlock(part, message.role()));
        }

        return node;
    }

    private ObjectNode convertContentBlock(MessageContent part, MessageRole role) {
        ObjectNode block = objectMapper.createObjectNode();
        switch (part) {
            case MessageContent.Text text -> {
                block.put("type", "text");
                block.put("text", text.text());
            }
            case MessageContent.ToolUse toolUse -> {
                block.put("type", "tool_use");
                block.put("id", toolUse.id());
                block.put("name", toolUse.name());
                block.set("input", objectMapper.valueToTree(toolUse.arguments()));
            }
            case MessageContent.ToolResult toolResult -> {
                block.put("type", "tool_result");
                block.put("tool_use_id", toolResult.toolUseId());
                block.put("content", toolResult.content());
                if (toolResult.isError()) {
                    block.put("is_error", true);
                }
            }
        }
        return block;
    }

    // ---- response parsing ----

    private LlmResponse parseResponse(String responseBody, Duration latency) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // Parse content blocks
        StringBuilder textContent = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        JsonNode contentArray = root.path("content");
        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    if (!textContent.isEmpty()) {
                        textContent.append("\n");
                    }
                    textContent.append(block.path("text").asText());
                } else if ("tool_use".equals(type)) {
                    String id = block.path("id").asText();
                    String name = block.path("name").asText();
                    Map<String, Object> arguments = objectMapper.convertValue(
                            block.path("input"),
                            new TypeReference<Map<String, Object>>() {});
                    toolCalls.add(new ToolCall(id, name, arguments));
                }
            }
        }

        // Parse stop_reason
        String stopReason = root.path("stop_reason").asText("");
        FinishReason finishReason = switch (stopReason) {
            case "end_turn" -> FinishReason.STOP;
            case "tool_use" -> FinishReason.TOOL_USE;
            case "max_tokens" -> FinishReason.MAX_TOKENS;
            default -> FinishReason.ERROR;
        };

        // Parse usage
        JsonNode usageNode = root.path("usage");
        int inputTokens = usageNode.path("input_tokens").asInt(0);
        int outputTokens = usageNode.path("output_tokens").asInt(0);
        int cacheReadTokens = usageNode.path("cache_read_input_tokens").asInt(0);
        int cacheCreationTokens = usageNode.path("cache_creation_input_tokens").asInt(0);
        TokenUsage usage = new TokenUsage(
                inputTokens,
                outputTokens,
                inputTokens + outputTokens,
                cacheReadTokens,
                cacheCreationTokens
        );

        // Parse model
        String model = root.path("model").asText("");

        return new LlmResponse(
                textContent.toString(),
                finishReason,
                usage,
                toolCalls,
                model,
                latency
        );
    }

    private String prettyPrint(String json) {
        try {
            String pretty = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objectMapper.readTree(json));
            return pretty.replace("\\n", "\n");
        } catch (Exception e) {
            return json;
        }
    }
}
