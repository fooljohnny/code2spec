package io.github.code2spec;

import io.github.code2spec.llm.LlmConfig;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
        name = "code2spec",
        mixinStandardHelpOptions = true,
        version = "Code2Spec 0.1.0",
        description = "将 Java 代码仓库转换为 REST API 说明文档（含 LLM 增强的业务语义与错误码说明）"
)
public class Code2SpecCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "项目目录（含 Java 源码和/或 OpenAPI/Swagger YAML 文件）")
    private Path sourceDir;

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出目录", defaultValue = "./output")
    private Path outputDir;

    @CommandLine.Option(names = {"--llm-api-key"}, description = "LLM API Key (OpenAI 兼容)")
    private String llmApiKey;

    @CommandLine.Option(names = {"--llm-api-base"}, description = "LLM API 基础 URL", defaultValue = "https://api.openai.com/v1")
    private String llmApiBase;

    @CommandLine.Option(names = {"--llm-model"}, description = "LLM 模型", defaultValue = "gpt-4o-mini")
    private String llmModel;

    @CommandLine.Option(names = {"--no-llm"}, description = "禁用 LLM 增强，仅使用规则提取")
    private boolean noLlm;

    @CommandLine.Option(names = {"--proxy"}, description = "HTTP 代理，格式: host:port 或 http://host:port（内部网络无法直连时使用代理）")
    private String proxy;

    @CommandLine.Option(names = {"--llm-delay-ms"}, description = "每次 LLM 请求前等待毫秒数，避免 429 限流（如 Groq 建议 2000）", defaultValue = "2000")
    private int llmDelayMs = 2000;

    @CommandLine.Option(names = {"--llm-retry-wait-ms"}, description = "遇到 429 限流时等待毫秒数后重试", defaultValue = "60000")
    private int llmRetryWaitMs = 60000;

    @CommandLine.Option(names = {"--llm-call-chain-depth"}, description = "调用链递归收集深度（接口方法->被调方法->...，0=仅接口方法）", defaultValue = "3")
    private int llmCallChainDepth = 3;

    @CommandLine.Option(names = {"--llm-method-body-max-chars"}, description = "接口方法体最大字符数", defaultValue = "2000")
    private int llmMethodBodyMaxChars = 2000;

    @CommandLine.Option(names = {"--llm-call-chain-max-chars"}, description = "调用链总最大字符数", defaultValue = "12000")
    private int llmCallChainMaxChars = 12000;

    @CommandLine.Option(names = {"--llm-concurrency"}, description = "LLM 请求并发数（提高可加速，但可能触发 429 限流）", defaultValue = "3")
    private int llmConcurrency = 3;

    @CommandLine.Option(names = {"--llm-read-timeout"}, description = "LLM 单次请求读超时秒数（复杂 prompt 可适当增大）", defaultValue = "120")
    private int llmReadTimeoutSeconds = 120;

    @Override
    public void run() {
        LlmConfig llmConfig = new LlmConfig();
        llmConfig.setEnabled(!noLlm);
        llmConfig.setProxy(proxy);
        llmConfig.setLlmDelayMs(llmDelayMs);
        llmConfig.setLlmRetryWaitMs(llmRetryWaitMs);
        llmConfig.setCallChainDepth(llmCallChainDepth);
        llmConfig.setMethodBodyMaxChars(llmMethodBodyMaxChars);
        llmConfig.setCallChainMaxChars(llmCallChainMaxChars);
        llmConfig.setLlmConcurrency(llmConcurrency);
        llmConfig.setLlmReadTimeoutSeconds(llmReadTimeoutSeconds);
        if (!noLlm && llmApiKey != null && !llmApiKey.isBlank()) {
            llmConfig.setApiKey(llmApiKey);
            llmConfig.setApiBaseUrl(llmApiBase);
            llmConfig.setModel(llmModel);
        } else if (!noLlm) {
            String envKey = System.getenv("OPENAI_API_KEY");
            if (envKey != null && !envKey.isBlank()) {
                llmConfig.setApiKey(envKey);
                llmConfig.setApiBaseUrl(llmApiBase);
                llmConfig.setModel(llmModel);
            } else {
                System.out.println("提示: 未配置 LLM API Key，将仅使用规则提取。可通过 --llm-api-key 或环境变量 OPENAI_API_KEY 配置。");
                llmConfig.setEnabled(false);
            }
        }

        try {
            new Pipeline(sourceDir.toAbsolutePath(), outputDir.toAbsolutePath(), llmConfig).run();
        } catch (Exception e) {
            throw new RuntimeException("执行失败: " + e.getMessage(), e);
        }
    }
}
