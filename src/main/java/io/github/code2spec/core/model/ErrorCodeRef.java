package io.github.code2spec.core.model;

/**
 * Reference to an error code from an endpoint.
 */
public class ErrorCodeRef {
    private String code;
    private String description;

    public ErrorCodeRef() {}
    public ErrorCodeRef(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
