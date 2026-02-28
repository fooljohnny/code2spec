package io.github.code2spec.parser;

import io.github.code2spec.core.model.SpecResult;
import io.github.code2spec.llm.NoOpLlmEnhancer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaRestParserTest {

    @Test
    void parseSpringMvcController() throws Exception {
        Path demoApiDir = Path.of("samples/demo-api/src/main/java/com/example/api").toAbsolutePath();
        JavaRestParser parser = new JavaRestParser(new NoOpLlmEnhancer());
        SpecResult result = parser.parse(demoApiDir);

        assertFalse(result.getEndpoints().isEmpty(), "Should find Spring MVC endpoints");
        assertTrue(result.getEndpoints().stream().anyMatch(e ->
                e.getUri().contains("/orders") && "POST".equals(e.getHttpMethod())));
        assertTrue(result.getEndpoints().stream().anyMatch(e ->
                e.getUri().contains("/orders") && "GET".equals(e.getHttpMethod())));
        assertTrue(result.getEndpoints().stream().anyMatch(e ->
                e.getUri().contains("/orders") && "DELETE".equals(e.getHttpMethod())));
    }

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
    void parseDemoJaxRsServiceCombStyle() throws Exception {
        Path demoJaxrsDir = Path.of("samples/demo-jaxrs/src/main/java/com/example/jaxrs").toAbsolutePath();
        JavaRestParser parser = new JavaRestParser(new NoOpLlmEnhancer());
        SpecResult result = parser.parse(demoJaxrsDir);

        assertTrue(result.getEndpoints().stream().anyMatch(e ->
                e.getUri().endsWith("/file/download") && "POST".equals(e.getHttpMethod())));
        assertTrue(result.getEndpoints().stream().anyMatch(e ->
                e.getUri().endsWith("/file/upload") && "POST".equals(e.getHttpMethod())));
        assertTrue(result.getErrorCodes().stream().anyMatch(ec -> "CommonException".equals(ec.getCode())),
                "Should have CommonException, not 'class }'");
    }
}
