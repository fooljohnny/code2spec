package io.github.code2spec.parser;

import io.github.code2spec.core.model.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses OpenAPI/Swagger YAML/JSON files into SpecResult.
 */
public class OpenApiFileParser {

    private static final List<String> OPENAPI_FILE_NAMES = List.of(
            "openapi.yaml", "openapi.yml", "openapi.json",
            "swagger.yaml", "swagger.yml", "swagger.json"
    );

    public SpecResult parse(Path sourceRoot) throws Exception {
        SpecResult result = new SpecResult();
        List<Path> openApiFiles = collectOpenApiFiles(sourceRoot);

        for (Path file : openApiFiles) {
            try {
                String content = Files.readString(file);
                SwaggerParseResult parseResult = new OpenAPIV3Parser().readContents(content, null, null);
                OpenAPI openApi = parseResult.getOpenAPI();
                if (openApi != null) {
                    mergeIntoResult(openApi, result);
                }
            } catch (Exception e) {
                // Skip invalid files, continue with others
            }
        }
        return result;
    }

    private List<Path> collectOpenApiFiles(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        if (!root.toFile().exists()) return files;

        try (var stream = Files.walk(root, 10)) {
            stream.filter(p -> p.toFile().isFile())
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return OPENAPI_FILE_NAMES.contains(name)
                                || ((name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json"))
                                && (name.contains("openapi") || name.contains("swagger")));
                    })
                    .forEach(files::add);
        }

        // Prefer standard names, sort so openapi.yaml comes before api-spec.yaml
        files.sort((a, b) -> {
            String na = a.getFileName().toString().toLowerCase();
            String nb = b.getFileName().toString().toLowerCase();
            int pa = OPENAPI_FILE_NAMES.indexOf(na);
            int pb = OPENAPI_FILE_NAMES.indexOf(nb);
            if (pa >= 0 && pb >= 0) return Integer.compare(pa, pb);
            if (pa >= 0) return -1;
            if (pb >= 0) return 1;
            return na.compareTo(nb);
        });
        return files;
    }

    private void mergeIntoResult(OpenAPI openApi, SpecResult result) {
        String basePath = extractBasePath(openApi);
        Map<String, PathItem> paths = openApi.getPaths();
        if (paths == null) return;

        for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
            String path = normalizePath(basePath + entry.getKey());
            PathItem item = entry.getValue();

            for (String method : List.of("get", "post", "put", "delete", "patch")) {
                Operation op = getOperation(item, method);
                if (op != null) {
                    Endpoint ep = toEndpoint(path, method.toUpperCase(), op);
                    mergeEndpoint(result, ep);
                }
            }
        }

        extractErrorCodesFromResponses(openApi, result);
    }

    private String extractBasePath(OpenAPI openApi) {
        if (openApi.getServers() != null && !openApi.getServers().isEmpty()) {
            String url = openApi.getServers().get(0).getUrl();
            if (url != null && url.contains("/")) {
                try {
                    int idx = url.indexOf("/", 8); // skip https://
                    if (idx > 0) return url.substring(idx);
                } catch (Exception ignored) {
                }
            }
        }
        return "";
    }

    private String normalizePath(String p) {
        if (p == null || p.isBlank()) return "/";
        p = p.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private Operation getOperation(PathItem item, String method) {
        return switch (method) {
            case "get" -> item.getGet();
            case "post" -> item.getPost();
            case "put" -> item.getPut();
            case "delete" -> item.getDelete();
            case "patch" -> item.getPatch();
            default -> null;
        };
    }

    private Endpoint toEndpoint(String path, String httpMethod, Operation op) {
        Endpoint ep = new Endpoint();
        ep.setUri(path);
        ep.setHttpMethod(httpMethod);
        ep.setOperationId(op.getOperationId() != null ? op.getOperationId() : httpMethod.toLowerCase() + path.replace("/", "_"));
        ep.setSummary(op.getSummary());
        ep.setDescription(op.getDescription());

        if (op.getParameters() != null) {
            for (Parameter p : op.getParameters()) {
                io.github.code2spec.core.model.Parameter param = new io.github.code2spec.core.model.Parameter();
                param.setName(p.getName());
                param.setIn(p.getIn() != null ? p.getIn() : "query");
                param.setRequired(Boolean.TRUE.equals(p.getRequired()));
                param.setDescription(p.getDescription());
                if (p.getSchema() != null) param.setType(schemaType(p.getSchema()));
                ep.getParameters().add(param);
            }
        }

        if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
            var content = op.getRequestBody().getContent().get("application/json");
            if (content != null && content.getSchema() != null) {
                ep.setRequestBodyType(schemaName(content.getSchema()));
            }
        }

        if (op.getResponses() != null) {
            ApiResponse success = op.getResponses().get("200") != null ? op.getResponses().get("200") : op.getResponses().get("201");
            if (success != null && success.getContent() != null) {
                var content = success.getContent().get("application/json");
                if (content != null && content.getSchema() != null) {
                    ep.setResponseType(schemaName(content.getSchema()));
                }
            }
        }
        return ep;
    }

    private String schemaName(Schema<?> schema) {
        if (schema == null) return "object";
        if (schema.getName() != null) return schema.getName();
        String ref = schema.get$ref();
        if (ref != null && ref.contains("/")) return ref.substring(ref.lastIndexOf('/') + 1);
        return schema.getType() != null ? schema.getType() : "object";
    }

    private String schemaType(Schema<?> schema) {
        if (schema == null) return "string";
        if (schema.getType() != null) return schema.getType();
        if (schema.getName() != null) return schema.getName();
        return "object";
    }

    private void mergeEndpoint(SpecResult result, Endpoint newEp) {
        Optional<Endpoint> existing = result.getEndpoints().stream()
                .filter(e -> e.getUri().equals(newEp.getUri()) && e.getHttpMethod().equals(newEp.getHttpMethod()))
                .findFirst();
        if (existing.isPresent()) {
            Endpoint ex = existing.get();
            if (newEp.getSummary() != null && !newEp.getSummary().isBlank()) ex.setSummary(newEp.getSummary());
            if (newEp.getDescription() != null && !newEp.getDescription().isBlank()) ex.setDescription(newEp.getDescription());
            if (newEp.getParameters() != null && !newEp.getParameters().isEmpty()) ex.setParameters(newEp.getParameters());
            if (newEp.getRequestBodyType() != null) ex.setRequestBodyType(newEp.getRequestBodyType());
            if (newEp.getResponseType() != null) ex.setResponseType(newEp.getResponseType());
        } else {
            result.getEndpoints().add(newEp);
        }
    }

    private void extractErrorCodesFromResponses(OpenAPI openApi, SpecResult result) {
        Map<String, PathItem> paths = openApi.getPaths();
        if (paths == null) return;

        Set<String> seen = new HashSet<>();
        for (PathItem item : paths.values()) {
            for (Operation op : List.of(item.getGet(), item.getPost(), item.getPut(), item.getDelete(), item.getPatch())) {
                if (op == null || op.getResponses() == null) continue;
                for (Map.Entry<String, ApiResponse> entry : op.getResponses().entrySet()) {
                    String code = entry.getKey();
                    if (code.startsWith("4") || code.startsWith("5")) {
                        if (seen.add(code)) {
                            ApiResponse r = entry.getValue();
                            ErrorCode ec = new ErrorCode();
                            ec.setCode("HTTP_" + code);
                            ec.setMessage(r.getDescription() != null ? r.getDescription() : "HTTP " + code);
                            ec.setHttpStatus(Integer.parseInt(code));
                            result.getErrorCodes().add(ec);
                        }
                    }
                }
            }
        }
    }
}
