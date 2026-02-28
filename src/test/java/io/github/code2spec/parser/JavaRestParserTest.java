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
}
