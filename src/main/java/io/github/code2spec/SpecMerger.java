package io.github.code2spec;

import io.github.code2spec.core.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Merges Java-parsed and OpenAPI-parsed spec results.
 * OpenAPI descriptions/schemas take precedence when both exist.
 */
public class SpecMerger {

    public SpecResult merge(SpecResult javaResult, SpecResult openApiResult) {
        SpecResult merged = new SpecResult();
        merged.setBasePath(javaResult.getBasePath());

        Map<String, Endpoint> byKey = new LinkedHashMap<>();
        for (Endpoint ep : javaResult.getEndpoints()) {
            byKey.put(key(ep), ep);
        }
        for (Endpoint ep : openApiResult.getEndpoints()) {
            String k = key(ep);
            if (byKey.containsKey(k)) {
                enrichFromOpenApi(byKey.get(k), ep);
            } else {
                byKey.put(k, ep);
            }
        }
        merged.setEndpoints(new ArrayList<>(byKey.values()));

        merged.setErrorCodes(mergeErrorCodes(javaResult.getErrorCodes(), openApiResult.getErrorCodes()));
        return merged;
    }

    private String key(Endpoint ep) {
        return ep.getHttpMethod() + " " + ep.getUri();
    }

    private void enrichFromOpenApi(Endpoint target, Endpoint fromOpenApi) {
        if (fromOpenApi.getSummary() != null && !fromOpenApi.getSummary().isBlank()) {
            target.setSummary(fromOpenApi.getSummary());
        }
        if (fromOpenApi.getDescription() != null && !fromOpenApi.getDescription().isBlank()) {
            target.setDescription(fromOpenApi.getDescription());
        }
        if (fromOpenApi.getParameters() != null && !fromOpenApi.getParameters().isEmpty()) {
            target.setParameters(fromOpenApi.getParameters());
        }
        if (fromOpenApi.getRequestBodyType() != null) {
            target.setRequestBodyType(fromOpenApi.getRequestBodyType());
        }
        if (fromOpenApi.getResponseType() != null) {
            target.setResponseType(fromOpenApi.getResponseType());
        }
        if (fromOpenApi.getRequestBodySchema() != null) {
            target.setRequestBodySchema(fromOpenApi.getRequestBodySchema());
        }
        if (fromOpenApi.getResponseBodySchema() != null) {
            target.setResponseBodySchema(fromOpenApi.getResponseBodySchema());
        }
    }

    private List<ErrorCode> mergeErrorCodes(List<ErrorCode> java, List<ErrorCode> openApi) {
        Map<String, ErrorCode> byCode = new LinkedHashMap<>();
        for (ErrorCode ec : java) {
            byCode.put(ec.getCode(), ec);
        }
        for (ErrorCode ec : openApi) {
            if (!byCode.containsKey(ec.getCode())) {
                byCode.put(ec.getCode(), ec);
            } else {
                ErrorCode existing = byCode.get(ec.getCode());
                if (ec.getMessage() != null && !ec.getMessage().isBlank()) {
                    existing.setMessage(ec.getMessage());
                }
            }
        }
        return new ArrayList<>(byCode.values());
    }
}
