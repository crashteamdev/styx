package com.gloombox.styx.exception;

public class FailedProxyRequestException extends RuntimeException {

    private int statusCode;

    public FailedProxyRequestException(String message) {
        super(message);
    }

    public FailedProxyRequestException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
