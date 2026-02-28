package io.github.code2spec.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete specification result containing endpoints and error codes.
 */
public class SpecResult {
    private String basePath = "/";
    private List<Endpoint> endpoints = new ArrayList<>();
    private List<ErrorCode> errorCodes = new ArrayList<>();

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public List<Endpoint> getEndpoints() { return endpoints; }
    public void setEndpoints(List<Endpoint> endpoints) { this.endpoints = endpoints; }

    public List<ErrorCode> getErrorCodes() { return errorCodes; }
    public void setErrorCodes(List<ErrorCode> errorCodes) { this.errorCodes = errorCodes; }
}
