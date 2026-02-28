package io.github.code2spec.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.code2spec.core.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports spec to RAG-optimized knowledge objects (JSON).
 */
public class RagKnowledgeExporter {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void export(SpecResult result, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        // Export endpoints as individual knowledge objects
        for (Endpoint ep : result.getEndpoints()) {
            Map<String, Object> doc = toRagDocument(ep, result.getErrorCodes());
            String filename = sanitizeFilename(ep.getOperationId() + "_" + ep.getHttpMethod()) + ".json";
            Files.writeString(outputDir.resolve(filename), gson.toJson(doc));
        }

        // Export error codes index
        Map<String, Object> errorIndex = new LinkedHashMap<>();
        errorIndex.put("type", "error_code_index");
        errorIndex.put("error_codes", result.getErrorCodes().stream()
                .map(this::toErrorCodeDoc)
                .collect(Collectors.toList()));
        Files.writeString(outputDir.resolve("_error_codes.json"), gson.toJson(errorIndex));

        // Export full index for retrieval
        List<Map<String, Object>> index = new ArrayList<>();
        for (Endpoint ep : result.getEndpoints()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uri", ep.getHttpMethod() + " " + ep.getUri());
            entry.put("operation_id", ep.getOperationId());
            entry.put("summary", ep.getSummary());
            if (ep.getBusinessSemantic() != null) {
                entry.put("function", ep.getBusinessSemantic().getFunction());
                entry.put("scenario", ep.getBusinessSemantic().getScenario());
            }
            index.add(entry);
        }
        Map<String, Object> indexDoc = new LinkedHashMap<>();
        indexDoc.put("type", "endpoint_index");
        indexDoc.put("endpoints", index);
        Files.writeString(outputDir.resolve("_index.json"), gson.toJson(indexDoc));
    }

    private Map<String, Object> toRagDocument(Endpoint ep, List<ErrorCode> allErrorCodes) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "rest_endpoint");
        doc.put("uri", ep.getHttpMethod() + " " + ep.getUri());
        doc.put("operation_id", ep.getOperationId());
        doc.put("summary", ep.getSummary());
        doc.put("description", ep.getDescription());

        if (ep.getBusinessSemantic() != null) {
            Map<String, String> bs = new LinkedHashMap<>();
            bs.put("function", ep.getBusinessSemantic().getFunction());
            bs.put("scenario", ep.getBusinessSemantic().getScenario());
            bs.put("implementation_notes", ep.getBusinessSemantic().getImplementationNotes());
            bs.put("cautions", ep.getBusinessSemantic().getCautions());
            doc.put("business_semantic", bs);
        }

        if (!ep.getParameters().isEmpty()) {
            doc.put("parameters", ep.getParameters().stream()
                    .map(p -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", p.getName());
                        m.put("in", p.getIn());
                        m.put("type", p.getType());
                        m.put("required", p.isRequired());
                        m.put("description", p.getDescription());
                        return m;
                    })
                    .collect(Collectors.toList()));
        }
        if (ep.getRequestBodyType() != null) doc.put("request_body_type", ep.getRequestBodyType());
        if (ep.getResponseType() != null) doc.put("response_type", ep.getResponseType());

        List<Map<String, Object>> errorDocs = new ArrayList<>();
        for (ErrorCodeRef ref : ep.getErrorCodes()) {
            allErrorCodes.stream()
                    .filter(ec -> ref.getCode().equals(ec.getCode()))
                    .findFirst()
                    .ifPresent(ec -> errorDocs.add(toErrorCodeDoc(ec)));
        }
        if (errorDocs.isEmpty() && !allErrorCodes.isEmpty()) {
            // Include all error codes as reference
            errorDocs.addAll(allErrorCodes.stream().map(this::toErrorCodeDoc).collect(Collectors.toList()));
        }
        doc.put("error_codes", errorDocs);

        return doc;
    }

    private Map<String, Object> toErrorCodeDoc(ErrorCode ec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", ec.getCode());
        m.put("message", ec.getMessage());
        m.put("http_status", ec.getHttpStatus());
        m.put("exception_type", ec.getExceptionType());
        m.put("root_cause", ec.getRootCause());
        m.put("handling_suggestion", ec.getHandlingSuggestion());
        m.put("prevention", ec.getPrevention());
        return m;
    }

    private String sanitizeFilename(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
