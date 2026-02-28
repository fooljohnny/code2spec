package io.github.code2spec.llm;

/**
 * Context passed to LLM for error code enhancement.
 */
public class ErrorCodeContext {
    private String code;
    private String message;
    private int httpStatus;
    private String exceptionType;
    private String exceptionHandlerSnippet;
    private String throwLocationSnippet;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getExceptionHandlerSnippet() { return exceptionHandlerSnippet; }
    public void setExceptionHandlerSnippet(String exceptionHandlerSnippet) { this.exceptionHandlerSnippet = exceptionHandlerSnippet; }

    public String getThrowLocationSnippet() { return throwLocationSnippet; }
    public void setThrowLocationSnippet(String throwLocationSnippet) { this.throwLocationSnippet = throwLocationSnippet; }
}
