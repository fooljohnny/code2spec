package io.github.code2spec.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for OpenAI-compatible chat API.
 */
public class OpenAiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_RETRIES_ON_429 = 3;
    private final OkHttpClient httpClient;
    private final String apiBaseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final int retryWaitMs;
    private final Gson gson = new Gson();

    public OpenAiClient(LlmConfig config) {
        this.apiBaseUrl = config.getApiBaseUrl().replaceAll("/$", "");
        this.apiKey = config.getApiKey();
        this.model = config.getModel();
        this.maxTokens = config.getMaxTokens();
        this.retryWaitMs = config.getLlmRetryWaitMs();

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);
        Proxy proxy = parseProxy(config.getProxy());
        if (proxy != null) {
            builder.proxy(proxy);
        }
        this.httpClient = builder.build();
    }

    private static Proxy parseProxy(String proxyStr) {
        if (proxyStr == null || proxyStr.isBlank()) return null;
        String s = proxyStr.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            s = s.replaceFirst("^https?://", "");
        }
        int colon = s.lastIndexOf(':');
        if (colon <= 0 || colon == s.length() - 1) return null;
        String host = s.substring(0, colon).trim();
        int port;
        try {
            port = Integer.parseInt(s.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }

    public String chat(List<ChatMessage> messages) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.add("messages", gson.toJsonTree(messages));

        RequestBody requestBody = RequestBody.create(gson.toJson(body), JSON);
        Request request = new Request.Builder()
                .url(apiBaseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        for (int attempt = 0; attempt <= MAX_RETRIES_ON_429; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 429 && attempt < MAX_RETRIES_ON_429) {
                    response.close();
                    Thread.sleep(retryWaitMs);
                    continue;
                }
                if (!response.isSuccessful()) {
                    throw new IOException("LLM API error: " + response.code() + " " + response.body().string());
                }
                String responseBody = response.body().string();
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                return json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for retry", e);
            }
        }
        throw new IOException("LLM API rate limited (429) after " + (MAX_RETRIES_ON_429 + 1) + " attempts");
    }

    public static class ChatMessage {
        public String role;
        public String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
