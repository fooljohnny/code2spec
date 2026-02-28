package io.github.code2spec.parser;

import io.github.code2spec.core.model.SpecResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiFileParserTest {

    @Test
    void parseOpenApiYaml() throws Exception {
        Path demoApiDir = Path.of("samples/demo-api").toAbsolutePath();
        OpenApiFileParser parser = new OpenApiFileParser();
        SpecResult result = parser.parse(demoApiDir);

        assertFalse(result.getEndpoints().isEmpty(), "Should find endpoints from openapi.yaml");
        assertTrue(result.getEndpoints().stream().anyMatch(e ->
                e.getUri().contains("/orders") && "POST".equals(e.getHttpMethod())));
    }
}
