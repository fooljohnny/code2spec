package com.example.api;

public class ErrorResponse {
    private String code;
    private String message;
    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
