package io.github.code2spec;

import io.github.code2spec.core.model.SpecResult;
import io.github.code2spec.export.MarkdownExporter;
import io.github.code2spec.export.OpenApiExporter;
import io.github.code2spec.export.RagKnowledgeExporter;
import io.github.code2spec.llm.LlmConfig;
import io.github.code2spec.llm.LlmEnhancer;
import io.github.code2spec.llm.NoOpLlmEnhancer;
import io.github.code2spec.llm.OpenAiLlmEnhancer;
import io.github.code2spec.parser.JavaRestParser;
import io.github.code2spec.parser.OpenApiFileParser;

import java.nio.file.Path;

/**
 * Main pipeline: parse Java + OpenAPI → merge → enhance → export.
 */
public class Pipeline {
    private final Path sourceRoot;
    private final Path outputDir;
    private final LlmConfig llmConfig;

    public Pipeline(Path sourceRoot, Path outputDir, LlmConfig llmConfig) {
        this.sourceRoot = sourceRoot;
        this.outputDir = outputDir;
        this.llmConfig = llmConfig;
    }

    public void run() throws Exception {
        ProgressReporter progress = new ProgressReporter();
        LlmEnhancer enhancer = createEnhancer(progress);
        int callChainDepth = llmConfig != null ? llmConfig.getCallChainDepth() : 3;
        int callChainMaxChars = llmConfig != null ? llmConfig.getCallChainMaxChars() : 12000;
        JavaRestParser javaParser = new JavaRestParser(enhancer, progress, callChainDepth, callChainMaxChars);
        OpenApiFileParser openApiParser = new OpenApiFileParser(progress);

        SpecResult javaResult = javaParser.parse(sourceRoot);
        SpecResult openApiResult = openApiParser.parse(sourceRoot);

        progress.onMergeAndExport();

        SpecResult result = new SpecMerger().merge(javaResult, openApiResult);

        outputDir.toFile().mkdirs();

        new OpenApiExporter().exportToFile(result, outputDir.resolve("openapi.json"));
        new MarkdownExporter().export(result, outputDir.resolve("api-docs.md"));
        new RagKnowledgeExporter().export(result, outputDir.resolve("rag"));

        System.out.println("Generated:");
        System.out.println("  - " + outputDir.resolve("openapi.json"));
        System.out.println("  - " + outputDir.resolve("api-docs.md"));
        System.out.println("  - " + outputDir.resolve("rag") + "/");
        System.out.println();

        progress.printSummary();
    }

    private LlmEnhancer createEnhancer(ProgressReporter progress) {
        if (llmConfig != null && llmConfig.isEnabled() && llmConfig.getApiKey() != null && !llmConfig.getApiKey().isBlank()) {
            return new OpenAiLlmEnhancer(llmConfig, progress);
        }
        return new NoOpLlmEnhancer();
    }
}
