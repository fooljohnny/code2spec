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
            if (bs.getScenario() != null && !bs.getScenario().isBlank()) {
                sb.append("- **业务场景**: ").append(bs.getScenario()).append("\n");
            }
            if (bs.getImplementationNotes() != null && !bs.getImplementationNotes().isBlank()) {
                sb.append("- **实现要点**: ").append(bs.getImplementationNotes()).append("\n");
            }
            if (bs.getCautions() != null && !bs.getCautions().isBlank()) {
                sb.append("- **注意事项**: ").append(bs.getCautions()).append("\n");
            }
            sb.append("\n");
        }

        if (ep.getDescription() != null && !ep.getDescription().isBlank()) {
            sb.append("#### 描述\n\n").append(ep.getDescription()).append("\n\n");
        }

        if (!ep.getParameters().isEmpty()) {
            sb.append("#### 参数\n\n");
            sb.append("| 名称 | 位置 | 类型 | 必填 | 说明 |\n");
            sb.append("|-----|------|-----|-----|-----|\n");
            for (Parameter p : ep.getParameters()) {
                sb.append("| ").append(p.getName()).append(" | ").append(p.getIn()).append(" | ")
                        .append(p.getType()).append(" | ").append(p.isRequired() ? "是" : "否").append(" | ")
                        .append(p.getDescription() != null ? p.getDescription() : "").append(" |\n");
            }
            sb.append("\n");
        }
        if (ep.getRequestBodyType() != null) {
            sb.append("#### 请求体类型\n\n`").append(ep.getRequestBodyType()).append("`\n\n");
        }
        if (ep.getResponseType() != null) {
            sb.append("#### 响应类型\n\n`").append(ep.getResponseType()).append("`\n\n");
        }

        if (!ep.getErrorCodes().isEmpty() || !allErrorCodes.isEmpty()) {
            sb.append("#### 相关错误码\n\n");
            var codes = ep.getErrorCodes().isEmpty()
                    ? allErrorCodes.stream().map(ec -> new ErrorCodeRef(ec.getCode(), ec.getMessage())).collect(Collectors.toList())
                    : ep.getErrorCodes();
            for (ErrorCodeRef ref : codes) {
                allErrorCodes.stream().filter(ec -> ec.getCode().equals(ref.getCode())).findFirst()
                        .ifPresentOrElse(ec -> sb.append("- **").append(ec.getCode()).append("** (HTTP ").append(ec.getHttpStatus())
                                .append("): ").append(ec.getMessage()).append("\n"),
                                () -> sb.append("- ").append(ref.getCode()).append("\n"));
            }
            sb.append("\n");
        }
        sb.append("---\n\n");
        return sb.toString();
    }

    private String exportErrorCode(ErrorCode ec) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(ec.getCode()).append("\n\n");
        sb.append("- **HTTP 状态**: ").append(ec.getHttpStatus()).append("\n");
        sb.append("- **异常类型**: ").append(ec.getExceptionType()).append("\n");
        sb.append("- **消息**: ").append(ec.getMessage()).append("\n\n");
        if (ec.getRootCause() != null && !ec.getRootCause().isBlank()) {
            sb.append("**根因描述**: ").append(ec.getRootCause()).append("\n\n");
        }
        if (ec.getHandlingSuggestion() != null && !ec.getHandlingSuggestion().isBlank()) {
            sb.append("**处理建议**: ").append(ec.getHandlingSuggestion()).append("\n\n");
        }
        if (ec.getPrevention() != null && !ec.getPrevention().isBlank()) {
            sb.append("**预防建议**: ").append(ec.getPrevention()).append("\n\n");
        }
        sb.append("---\n\n");
        return sb.toString();
    }
}
