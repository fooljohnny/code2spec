package io.github.code2spec.llm;

/**
 * Configuration for LLM API.
 */
public class LlmConfig {
    private String apiBaseUrl = "https://api.openai.com/v1";
    private String apiKey;
    private String model = "gpt-4o-mini";
    private boolean enabled = true;
    private int maxTokens = 1024;

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
