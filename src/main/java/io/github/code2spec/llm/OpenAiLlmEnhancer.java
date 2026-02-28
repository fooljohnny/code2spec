package io.github.code2spec.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.code2spec.ProgressReporter;
import io.github.code2spec.core.model.BusinessSemantic;
import io.github.code2spec.core.model.ErrorCode;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible LLM enhancer for business semantics and error codes.
 */
public class OpenAiLlmEnhancer implements LlmEnhancer {
    private final OpenAiClient client;
    private final LlmConfig config;
    private final ProgressReporter progressReporter;
    private final Gson gson = new Gson();

    public OpenAiLlmEnhancer(LlmConfig config) {
        this(config, null);
    }

    public OpenAiLlmEnhancer(LlmConfig config, ProgressReporter progressReporter) {
        this.config = config;
        this.progressReporter = progressReporter;
        this.client = new OpenAiClient(config);
    }

    @Override
    public BusinessSemantic enhanceEndpoint(EndpointContext ctx) {
        if (!isEnabled()) return null;

        long t0 = System.currentTimeMillis();
        String prompt = buildEndpointPrompt(ctx);
        List<OpenAiClient.ChatMessage> messages = List.of(
                new OpenAiClient.ChatMessage("system", getEndpointSystemPrompt()),
                new OpenAiClient.ChatMessage("user", prompt)
        );
        if (progressReporter != null) progressReporter.verboseTiming("构建 prompt", System.currentTimeMillis() - t0);

        try {
            long t1 = System.currentTimeMillis();
            delayBeforeLlmRequest();
            if (progressReporter != null) progressReporter.verboseTiming("请求前延迟", System.currentTimeMillis() - t1);

            long t2 = System.currentTimeMillis();
            OpenAiClient.ChatResult result = client.chat(messages);
            if (progressReporter != null) progressReporter.verboseTiming("LLM 请求", System.currentTimeMillis() - t2);

            if (progressReporter != null && (result.promptTokens > 0 || result.completionTokens > 0)) {
                progressReporter.addTokens(result.promptTokens, result.completionTokens);
            }

            long t3 = System.currentTimeMillis();
            BusinessSemantic semantic = parseBusinessSemantic(result.content);
            if (progressReporter != null) progressReporter.verboseTiming("解析响应", System.currentTimeMillis() - t3);
            return semantic;
        } catch (Exception e) {
            // Log and return null on failure - fallback to rule-only output
            return null;
        }
    }

    @Override
    public void enhanceErrorCode(ErrorCode errorCode, ErrorCodeContext ctx) {
        if (!isEnabled()) return;

        long t0 = System.currentTimeMillis();
        String prompt = buildErrorCodePrompt(ctx);
        List<OpenAiClient.ChatMessage> messages = List.of(
                new OpenAiClient.ChatMessage("system", getErrorCodeSystemPrompt()),
                new OpenAiClient.ChatMessage("user", prompt)
        );
        if (progressReporter != null) progressReporter.verboseTiming("构建 prompt", System.currentTimeMillis() - t0);

        try {
            long t1 = System.currentTimeMillis();
            delayBeforeLlmRequest();
            if (progressReporter != null) progressReporter.verboseTiming("请求前延迟", System.currentTimeMillis() - t1);

            long t2 = System.currentTimeMillis();
            OpenAiClient.ChatResult result = client.chat(messages);
            if (progressReporter != null) progressReporter.verboseTiming("LLM 请求", System.currentTimeMillis() - t2);

            if (progressReporter != null && (result.promptTokens > 0 || result.completionTokens > 0)) {
                progressReporter.addTokens(result.promptTokens, result.completionTokens);
            }

            long t3 = System.currentTimeMillis();
            parseAndApplyErrorCodeEnhancement(result.content, errorCode);
            if (progressReporter != null) progressReporter.verboseTiming("解析响应", System.currentTimeMillis() - t3);
        } catch (Exception e) {
            // Log and skip enhancement on failure
        }
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled() && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    private String getEndpointSystemPrompt() {
        return """
            你是一个 REST API 文档专家。根据提供的 Java 接口代码上下文，生成结构化的业务语义描述。
            输出必须是合法的 JSON，且只包含以下字段（均为字符串，可为空）：
            - function: 功能概述（一句话说明接口做什么）
            - scenario: 业务场景（典型使用场景，如：用户登录、订单创建）
            - implementationNotes: 实现要点（关键逻辑、校验规则、依赖服务）
            - cautions: 注意事项（调用方需注意的事项）
            不要输出任何其他文字，只输出 JSON。
            """;
    }

    private String buildEndpointPrompt(EndpointContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("接口信息：\n");
        sb.append("- URI: ").append(ctx.getHttpMethod()).append(" ").append(ctx.getUri()).append("\n");
        sb.append("- 方法名: ").append(ctx.getMethodName()).append("\n");
        if (ctx.getJavadoc() != null && !ctx.getJavadoc().isBlank()) {
            sb.append("- Javadoc: ").append(ctx.getJavadoc()).append("\n");
        }
        if (ctx.getParameterTypes() != null && !ctx.getParameterTypes().isEmpty()) {
            sb.append("- 参数类型: ").append(String.join(", ", ctx.getParameterTypes())).append("\n");
        }
        if (ctx.getReturnType() != null) {
            sb.append("- 返回类型: ").append(ctx.getReturnType()).append("\n");
        }
        int methodBodyMax = config.getMethodBodyMaxChars();
        if (ctx.getMethodBodySnippet() != null && !ctx.getMethodBodySnippet().isBlank()) {
            sb.append("- 方法体片段:\n```\n").append(truncate(ctx.getMethodBodySnippet(), methodBodyMax)).append("\n```\n");
        }
        int callChainMax = config.getCallChainMaxChars();
        if (callChainMax > 0 && ctx.getCalledMethodNames() != null && !ctx.getCalledMethodNames().isEmpty()) {
            sb.append("- 调用的方法: ").append(String.join(", ", ctx.getCalledMethodNames())).append("\n");
        }
        if (callChainMax > 0 && ctx.getCallChainSnippet() != null && !ctx.getCallChainSnippet().isBlank()) {
            sb.append("- 完整调用链代码:\n```\n").append(truncate(ctx.getCallChainSnippet(), callChainMax)).append("\n```\n");
        }
        sb.append("\n请生成 JSON 格式的业务语义描述。");
        return sb.toString();
    }

    private BusinessSemantic parseBusinessSemantic(String response) {
        try {
            String json = extractJson(response);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            BusinessSemantic s = new BusinessSemantic();
            s.setFunction(getString(obj, "function"));
            s.setScenario(getString(obj, "scenario"));
            s.setImplementationNotes(getString(obj, "implementationNotes"));
            s.setCautions(getString(obj, "cautions"));
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private String getErrorCodeSystemPrompt() {
        return """
            你是一个 REST API 错误处理专家。根据提供的错误码和代码上下文，生成根因描述和处理建议。
            输出必须是合法的 JSON，且只包含以下字段（均为字符串，可为空）：
            - rootCause: 根因描述（导致该错误的典型原因，从业务层面说明）
            - handlingSuggestion: 处理建议（API 调用方应如何应对：重试、参数修正、联系支持等）
            - prevention: 预防建议（如何避免触发该错误）
            不要输出任何其他文字，只输出 JSON。
            """;
    }

    private String buildErrorCodePrompt(ErrorCodeContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("错误码信息：\n");
        sb.append("- code: ").append(ctx.getCode()).append("\n");
        sb.append("- message: ").append(ctx.getMessage()).append("\n");
        sb.append("- HTTP Status: ").append(ctx.getHttpStatus()).append("\n");
        sb.append("- 异常类型: ").append(ctx.getExceptionType()).append("\n");
        if (ctx.getExceptionHandlerSnippet() != null && !ctx.getExceptionHandlerSnippet().isBlank()) {
            sb.append("- 异常处理逻辑:\n```\n").append(truncate(ctx.getExceptionHandlerSnippet(), 800)).append("\n```\n");
        }
        if (ctx.getThrowLocationSnippet() != null && !ctx.getThrowLocationSnippet().isBlank()) {
            sb.append("- 抛出位置上下文:\n```\n").append(truncate(ctx.getThrowLocationSnippet(), 800)).append("\n```\n");
        }
        sb.append("\n请生成 JSON 格式的根因描述和处理建议。");
        return sb.toString();
    }

    private void parseAndApplyErrorCodeEnhancement(String response, ErrorCode errorCode) {
        try {
            String json = extractJson(response);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            errorCode.setRootCause(getString(obj, "rootCause"));
            errorCode.setHandlingSuggestion(getString(obj, "handlingSuggestion"));
            errorCode.setPrevention(getString(obj, "prevention"));
        } catch (Exception ignored) {
        }
    }

    private String getString(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        var el = obj.get(key);
        return el == null || el.isJsonNull() ? null : el.getAsString();
    }

    private String extractJson(String text) {
        text = text.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private void delayBeforeLlmRequest() {
        int ms = config.getLlmDelayMs();
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting before LLM request", e);
            }
        }
    }
}
