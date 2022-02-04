package dev.crashteam.styx.exception;

import lombok.Data;

public class FailedProxyRequestException extends RuntimeException {

    private int statusCode;

    public FailedProxyRequestException(String message) {
        super(message);
    }

    public FailedProxyRequestException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
