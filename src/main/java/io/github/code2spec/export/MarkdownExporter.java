package io.github.code2spec.export;

import io.github.code2spec.core.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Exports spec to human-readable Markdown.
 */
public class MarkdownExporter {

    public void export(SpecResult result, Path outputFile) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# REST API 文档\n\n");
        md.append("> 由 Code2Spec 自动生成，含 LLM 增强的业务语义与错误码说明\n\n");

        md.append("## 接口列表\n\n");
        for (Endpoint ep : result.getEndpoints()) {
            md.append(exportEndpoint(ep, result.getErrorCodes()));
        }

        md.append("## 错误码说明\n\n");
        for (ErrorCode ec : result.getErrorCodes()) {
            md.append(exportErrorCode(ec));
        }

        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, md.toString());
    }

    private String exportEndpoint(Endpoint ep, java.util.List<ErrorCode> allErrorCodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(ep.getHttpMethod()).append(" ").append(ep.getUri()).append("\n\n");
        sb.append("- **操作ID**: ").append(ep.getOperationId()).append("\n");
        sb.append("- **摘要**: ").append(ep.getSummary()).append("\n\n");

        if (ep.getBusinessSemantic() != null) {
            BusinessSemantic bs = ep.getBusinessSemantic();
            sb.append("#### 业务语义\n\n");
            if (bs.getFunction() != null && !bs.getFunction().isBlank()) {
                sb.append("- **功能概述**: ").append(bs.getFunction()).append("\n");
            }
            sb.append("- **业务场景**: ").append(nonBlankOrPlaceholder(bs.getScenario())).append("\n");
            if (bs.getImplementationNotes() != null && !bs.getImplementationNotes().isBlank()) {
                sb.append("- **实现要点**: ").append(bs.getImplementationNotes()).append("\n");
            }
            sb.append("- **注意事项**: ").append(nonBlankOrPlaceholder(bs.getCautions())).append("\n");
            sb.append("\n");
        } else {
            sb.append("#### 业务语义\n\n");
            sb.append("- **业务场景**: （启用 LLM 增强后可补充）\n");
            sb.append("- **注意事项**: （启用 LLM 增强后可补充）\n\n");
        }

        if (ep.getDescription() != null && !ep.getDescription().isBlank()) {
            sb.append("#### 描述\n\n").append(ep.getDescription()).append("\n\n");
        }

        if (!ep.getParameters().isEmpty()) {
            sb.append("#### 参数\n\n");
            sb.append("| 名称 | 位置 | 类型 | 必填 | 约束 | 说明 |\n");
            sb.append("|-----|------|-----|-----|-----|-----|\n");
            for (Parameter p : ep.getParameters()) {
                String constraints = formatParameterConstraints(p);
                sb.append("| ").append(p.getName()).append(" | ").append(p.getIn()).append(" | ")
                        .append(nullToEmpty(p.getType())).append(" | ").append(p.isRequired() ? "是" : "否").append(" | ")
                        .append(constraints).append(" | ")
                        .append(nullToEmpty(p.getDescription())).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("#### 请求体定义\n\n");
        if (ep.getRequestBodySchema() != null && !ep.getRequestBodySchema().getFields().isEmpty()) {
            sb.append("**类型**: `").append(ep.getRequestBodySchema().getSchemaName()).append("`\n\n");
            sb.append("| 字段 | 类型 | 必填 | 约束 | 说明 |\n");
            sb.append("|-----|-----|-----|-----|-----|\n");
            for (SchemaField f : ep.getRequestBodySchema().getFields()) {
                String constraints = formatFieldConstraints(f);
                sb.append("| ").append(f.getName()).append(" | ").append(nullToEmpty(f.getType())).append(" | ")
                        .append(f.isRequired() ? "是" : "否").append(" | ").append(constraints).append(" | ")
                        .append(nullToEmpty(f.getDescription())).append(" |\n");
            }
            sb.append("\n");
        } else if (ep.getRequestBodyType() != null) {
            sb.append("**类型**: `").append(ep.getRequestBodyType()).append("`\n\n");
            sb.append("（无字段定义，可从 OpenAPI 文件或启用 LLM 增强补充）\n\n");
        } else {
            sb.append("无请求体\n\n");
        }

        sb.append("#### 响应体定义\n\n");
        if (ep.getResponseBodySchema() != null && !ep.getResponseBodySchema().getFields().isEmpty()) {
            sb.append("**类型**: `").append(ep.getResponseBodySchema().getSchemaName()).append("`\n\n");
            sb.append("| 字段 | 类型 | 必填 | 约束 | 说明 |\n");
            sb.append("|-----|-----|-----|-----|-----|\n");
            for (SchemaField f : ep.getResponseBodySchema().getFields()) {
                String constraints = formatFieldConstraints(f);
                sb.append("| ").append(f.getName()).append(" | ").append(nullToEmpty(f.getType())).append(" | ")
                        .append(f.isRequired() ? "是" : "否").append(" | ").append(constraints).append(" | ")
                        .append(nullToEmpty(f.getDescription())).append(" |\n");
            }
            sb.append("\n");
        } else if (ep.getResponseType() != null) {
            sb.append("**类型**: `").append(ep.getResponseType()).append("`\n\n");
            sb.append("（无字段定义，可从 OpenAPI 文件或启用 LLM 增强补充）\n\n");
        } else {
            sb.append("无响应体\n\n");
        }

        if (!ep.getErrorCodes().isEmpty() || !allErrorCodes.isEmpty()) {
            sb.append("#### 相关错误码\n\n");
            var codes = ep.getErrorCodes().isEmpty()
                    ? allErrorCodes.stream().map(ec -> new ErrorCodeRef(ec.getCode(), ec.getMessage())).collect(Collectors.toList())
                    : ep.getErrorCodes();
            for (ErrorCodeRef ref : codes) {
                allErrorCodes.stream().filter(ec -> ec.getCode().equals(ref.getCode())).findFirst()
                        .ifPresentOrElse(ec -> {
                            sb.append("- **").append(ec.getCode()).append("** (HTTP ").append(ec.getHttpStatus()).append("): ")
                                    .append(ec.getMessage()).append("\n");
                            sb.append("  - **根因**: ").append(nonBlankOrPlaceholder(ec.getRootCause())).append("\n");
                            sb.append("  - **处理建议**: ").append(nonBlankOrPlaceholder(ec.getHandlingSuggestion())).append("\n");
                        }, () -> sb.append("- ").append(ref.getCode()).append("\n"));
            }
            sb.append("\n");
        }
        sb.append("---\n\n");
        return sb.toString();
    }

    private String nonBlankOrPlaceholder(String s) {
        return (s != null && !s.isBlank()) ? s : "（启用 LLM 增强后可补充）";
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private String formatParameterConstraints(Parameter p) {
        StringBuilder c = new StringBuilder();
        if (p.getMinimum() != null) c.append("min=").append(p.getMinimum()).append(" ");
        if (p.getMaximum() != null) c.append("max=").append(p.getMaximum()).append(" ");
        if (p.getMinLength() != null) c.append("minLen=").append(p.getMinLength()).append(" ");
        if (p.getMaxLength() != null) c.append("maxLen=").append(p.getMaxLength()).append(" ");
        if (p.getPattern() != null) c.append("pattern=").append(p.getPattern()).append(" ");
        if (p.getFormat() != null) c.append("format=").append(p.getFormat());
        return c.toString().trim().isEmpty() ? "-" : c.toString().trim();
    }

    private String formatFieldConstraints(SchemaField f) {
        StringBuilder c = new StringBuilder();
        if (f.getMinimum() != null) c.append("min=").append(f.getMinimum()).append(" ");
        if (f.getMaximum() != null) c.append("max=").append(f.getMaximum()).append(" ");
        if (f.getMinLength() != null) c.append("minLen=").append(f.getMinLength()).append(" ");
        if (f.getMaxLength() != null) c.append("maxLen=").append(f.getMaxLength()).append(" ");
        if (f.getPattern() != null) c.append("pattern=").append(f.getPattern()).append(" ");
        if (f.getFormat() != null) c.append("format=").append(f.getFormat());
        return c.toString().trim().isEmpty() ? "-" : c.toString().trim();
    }

    private String exportErrorCode(ErrorCode ec) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(ec.getCode()).append("\n\n");
        sb.append("- **HTTP 状态**: ").append(ec.getHttpStatus()).append("\n");
        sb.append("- **异常类型**: ").append(nullToEmpty(ec.getExceptionType())).append("\n");
        sb.append("- **消息**: ").append(nullToEmpty(ec.getMessage())).append("\n\n");
        sb.append("**根因描述**: ").append(nonBlankOrPlaceholder(ec.getRootCause())).append("\n\n");
        sb.append("**处理建议**: ").append(nonBlankOrPlaceholder(ec.getHandlingSuggestion())).append("\n\n");
        if (ec.getPrevention() != null && !ec.getPrevention().isBlank()) {
            sb.append("**预防建议**: ").append(ec.getPrevention()).append("\n\n");
        } else {
            sb.append("**预防建议**: （启用 LLM 增强后可补充）\n\n");
        }
        sb.append("---\n\n");
        return sb.toString();
    }
}
