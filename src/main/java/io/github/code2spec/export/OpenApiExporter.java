package io.github.code2spec.export;

import io.github.code2spec.core.model.*;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports spec to OpenAPI 3.x.
 */
public class OpenApiExporter {

    public OpenAPI export(SpecResult result) {
        OpenAPI openApi = new OpenAPI();
        openApi.setInfo(new Info()
                .title("REST API")
                .description("由 Code2Spec 自动生成")
                .version("1.0.0"));

        Paths paths = new Paths();
        for (Endpoint ep : result.getEndpoints()) {
            String path = ep.getUri();
            PathItem pathItem = paths.get(path);
            if (pathItem == null) {
                pathItem = new PathItem();
                paths.addPathItem(path, pathItem);
            }
            Operation op = toOperation(ep, result.getErrorCodes());
            setHttpMethod(pathItem, ep.getHttpMethod(), op);
        }
        openApi.setPaths(paths);
        return openApi;
    }

    public void exportToFile(SpecResult result, Path outputFile) throws IOException {
        OpenAPI openApi = export(result);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, Json.pretty(openApi));
    }

    private Operation toOperation(Endpoint ep, List<ErrorCode> errorCodes) {
        Operation op = new Operation();
        op.setOperationId(ep.getOperationId());
        op.setSummary(ep.getSummary());
        op.setDescription(buildDescription(ep));

        ep.getParameters().forEach(p -> {
            io.swagger.v3.oas.models.parameters.Parameter param =
                    new io.swagger.v3.oas.models.parameters.Parameter()
                            .name(p.getName())
                            .in(p.getIn())
                            .required(p.isRequired())
                            .schema(new Schema().type(mapType(p.getType())));
            if (p.getDescription() != null) param.setDescription(p.getDescription());
            op.addParametersItem(param);
        });

        if (ep.getRequestBodyType() != null) {
            op.setRequestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema().type("object").description(ep.getRequestBodyType())))));
        }

        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", new ApiResponse()
                .description("成功")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema().type("object").description(ep.getResponseType())))));
        for (ErrorCode ec : errorCodes) {
            responses.addApiResponse(String.valueOf(ec.getHttpStatus()),
                    new ApiResponse().description(buildErrorDescription(ec)));
        }
        op.setResponses(responses);
        return op;
    }

    private String buildDescription(Endpoint ep) {
        StringBuilder sb = new StringBuilder();
        if (ep.getDescription() != null) sb.append(ep.getDescription()).append("\n\n");
        if (ep.getBusinessSemantic() != null) {
            BusinessSemantic bs = ep.getBusinessSemantic();
            if (bs.getFunction() != null) sb.append("**功能**: ").append(bs.getFunction()).append("\n");
            if (bs.getScenario() != null) sb.append("**场景**: ").append(bs.getScenario()).append("\n");
            if (bs.getImplementationNotes() != null) sb.append("**实现要点**: ").append(bs.getImplementationNotes()).append("\n");
            if (bs.getCautions() != null) sb.append("**注意事项**: ").append(bs.getCautions()).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildErrorDescription(ErrorCode ec) {
        StringBuilder sb = new StringBuilder();
        sb.append(ec.getMessage());
        if (ec.getRootCause() != null && !ec.getRootCause().isBlank()) {
            sb.append(" 根因: ").append(ec.getRootCause());
        }
        if (ec.getHandlingSuggestion() != null && !ec.getHandlingSuggestion().isBlank()) {
            sb.append(" 处理建议: ").append(ec.getHandlingSuggestion());
        }
        return sb.toString();
    }

    private void setHttpMethod(PathItem pathItem, String method, Operation op) {
        switch (method.toUpperCase()) {
            case "GET" -> pathItem.setGet(op);
            case "POST" -> pathItem.setPost(op);
            case "PUT" -> pathItem.setPut(op);
            case "DELETE" -> pathItem.setDelete(op);
            case "PATCH" -> pathItem.setPatch(op);
            default -> pathItem.setGet(op);
        }
    }

    private String mapType(String javaType) {
        if (javaType == null) return "string";
        return switch (javaType.toLowerCase()) {
            case "int", "integer", "long" -> "integer";
            case "double", "float" -> "number";
            case "boolean" -> "boolean";
            default -> "string";
        };
    }
}
