package com.example.api.test1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

    @ExceptionHandler({CommonException.class})
    public ResponseEntity<Object> commonExceptionHandler(CommonException e) {
        return ResponseEntity.status(500).body(e.getMessage());
    }
}
