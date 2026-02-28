package io.github.code2spec;

import io.github.code2spec.core.model.SpecResult;
import io.github.code2spec.export.MarkdownExporter;
import io.github.code2spec.export.OpenApiExporter;
import io.github.code2spec.export.RagKnowledgeExporter;
import io.github.code2spec.llm.NoOpLlmEnhancer;
import io.github.code2spec.parser.JavaRestParser;
import io.github.code2spec.parser.OpenApiFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {

    @Test
    void runPipelineOnDemoApi(@TempDir Path outputDir) throws Exception {
        Path sourceRoot = Path.of("samples/demo-api").toAbsolutePath();
        JavaRestParser javaParser = new JavaRestParser(new NoOpLlmEnhancer());
        OpenApiFileParser openApiParser = new OpenApiFileParser();

        SpecResult javaResult = javaParser.parse(sourceRoot);
        SpecResult openApiResult = openApiParser.parse(sourceRoot);
        SpecResult result = new SpecMerger().merge(javaResult, openApiResult);

        assertFalse(result.getEndpoints().isEmpty(), "Should have merged endpoints");
        assertTrue(result.getEndpoints().stream().anyMatch(e -> e.getUri().contains("/orders")));
        assertTrue(result.getEndpoints().stream().anyMatch(e -> e.getUri().contains("/products")));

        new OpenApiExporter().exportToFile(result, outputDir.resolve("openapi.json"));
        new MarkdownExporter().export(result, outputDir.resolve("api-docs.md"));
        new RagKnowledgeExporter().export(result, outputDir.resolve("rag"));

        assertTrue(Files.exists(outputDir.resolve("openapi.json")));
        assertTrue(Files.exists(outputDir.resolve("api-docs.md")));
        assertTrue(Files.exists(outputDir.resolve("rag/_index.json")));
    }

    @Test
    void runPipelineOnDemoJaxrs(@TempDir Path outputDir) throws Exception {
        Path sourceRoot = Path.of("samples/demo-jaxrs").toAbsolutePath();
        JavaRestParser javaParser = new JavaRestParser(new NoOpLlmEnhancer());
        OpenApiFileParser openApiParser = new OpenApiFileParser();

        SpecResult javaResult = javaParser.parse(sourceRoot);
        SpecResult openApiResult = openApiParser.parse(sourceRoot);
        SpecResult result = new SpecMerger().merge(javaResult, openApiResult);

        assertFalse(result.getEndpoints().isEmpty());
        assertTrue(result.getEndpoints().stream().anyMatch(e -> e.getUri().contains("/file/download")));
        assertTrue(result.getErrorCodes().stream().anyMatch(ec -> "CommonException".equals(ec.getCode())));

        new OpenApiExporter().exportToFile(result, outputDir.resolve("openapi.json"));
        assertTrue(Files.exists(outputDir.resolve("openapi.json")));
    }
}
