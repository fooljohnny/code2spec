package com.example.jaxrs;

/**
 * 通用业务异常（与 test1 CommonException 风格一致，供 ExceptionHandler 使用）
 */
public class CommonException extends RuntimeException {
    private final Object commonExceptionArgs;

    public CommonException(String message, Object commonExceptionArgs) {
        super(message);
        this.commonExceptionArgs = commonExceptionArgs;
    }

    public Object getCommonExceptionArgs() {
        return commonExceptionArgs;
    }
}
