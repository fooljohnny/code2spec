package com.example.jaxrs;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * 全局异常处理（与 test1 代码风格一致：@ControllerAdvice + @ExceptionHandler 数组形式）
 */
@ControllerAdvice
@Order(10)
public class CommonExceptionHandler {

    @ExceptionHandler({CommonException.class})
    public ResponseEntity<Map<String, Object>> commonExceptionHandler(CommonException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("errorCode", "COMMON_ERROR", "message", e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("errorCode", "PARAM_INVALID", "message", e.getMessage()));
    }
}
