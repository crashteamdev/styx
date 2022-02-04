package dev.crashteam.styx.exception;

import lombok.Data;

public class OriginalRequestException extends RuntimeException {

    private int statusCode;

    public OriginalRequestException(String message) {
        super(message);
    }

    public OriginalRequestException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
