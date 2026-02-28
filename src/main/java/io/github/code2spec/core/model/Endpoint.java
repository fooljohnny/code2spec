package io.github.code2spec.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * REST endpoint model (rule-extracted + optionally LLM-enhanced).
 */
public class Endpoint {
    private String uri;
    private String httpMethod;
    private String operationId;
    private String summary;
    private String description;

    /** LLM-enhanced business semantic description */
    private BusinessSemantic businessSemantic;

    private List<Parameter> parameters = new ArrayList<>();
    private String requestBodyType;
    private String responseType;
    private List<ErrorCodeRef> errorCodes = new ArrayList<>();

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BusinessSemantic getBusinessSemantic() { return businessSemantic; }
    public void setBusinessSemantic(BusinessSemantic businessSemantic) { this.businessSemantic = businessSemantic; }

    public List<Parameter> getParameters() { return parameters; }
    public void setParameters(List<Parameter> parameters) { this.parameters = parameters; }

    public String getRequestBodyType() { return requestBodyType; }
    public void setRequestBodyType(String requestBodyType) { this.requestBodyType = requestBodyType; }

    public String getResponseType() { return responseType; }
    public void setResponseType(String responseType) { this.responseType = responseType; }

    public List<ErrorCodeRef> getErrorCodes() { return errorCodes; }
    public void setErrorCodes(List<ErrorCodeRef> errorCodes) { this.errorCodes = errorCodes; }
}
