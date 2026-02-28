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

    @Override
    public void run() {
        LlmConfig llmConfig = new LlmConfig();
        llmConfig.setEnabled(!noLlm);
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
