package io.github.code2spec.core.model;

/**
 * Error code model with optional LLM-enhanced root cause and handling suggestions.
 */
public class ErrorCode {
    private String code;
    private String message;
    private int httpStatus;
    private String exceptionType;

    /** LLM-enhanced: root cause description */
    private String rootCause;
    /** LLM-enhanced: handling suggestion for API consumers */
    private String handlingSuggestion;
    /** LLM-enhanced: how to prevent this error */
    private String prevention;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getHandlingSuggestion() { return handlingSuggestion; }
    public void setHandlingSuggestion(String handlingSuggestion) { this.handlingSuggestion = handlingSuggestion; }

    public String getPrevention() { return prevention; }
    public void setPrevention(String prevention) { this.prevention = prevention; }
}
