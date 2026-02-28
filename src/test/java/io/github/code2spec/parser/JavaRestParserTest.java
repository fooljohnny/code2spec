package io.github.code2spec.parser;

import io.github.code2spec.core.model.SpecResult;
import io.github.code2spec.llm.NoOpLlmEnhancer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaRestParserTest {

    @Test
    void parseJaxRsResource() throws Exception {
        Path jaxrsDir = Path.of("samples/demo-api/src/main/java/com/example/api/jaxrs").toAbsolutePath();
        JavaRestParser parser = new JavaRestParser(new NoOpLlmEnhancer());
        SpecResult result = parser.parse(jaxrsDir);

        assertFalse(result.getEndpoints().isEmpty(), "Should find JAX-RS endpoints");
        assertTrue(result.getEndpoints().stream().anyMatch(e -> e.getUri().contains("/products")),
                "Should have /products endpoints");
    }

    @Test
    void parseTest1StyleServiceCombJaxRs() throws Exception {
        Path test1Dir = Path.of("samples/demo-api/src/main/java/com/example/api/test1").toAbsolutePath();
        JavaRestParser parser = new JavaRestParser(new NoOpLlmEnhancer());
        SpecResult result = parser.parse(test1Dir);

        assertTrue(result.getEndpoints().stream().anyMatch(e ->
                e.getUri().endsWith("/file/download") && "POST".equals(e.getHttpMethod())));
        assertTrue(result.getEndpoints().stream().anyMatch(e ->
                e.getUri().endsWith("/file/upload") && "POST".equals(e.getHttpMethod())));
        assertTrue(result.getErrorCodes().stream().anyMatch(ec -> "CommonException".equals(ec.getCode())),
                "Should have CommonException, not 'class }'");
    }
}
