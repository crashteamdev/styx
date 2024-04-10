package dev.crashteam.styx.exception;

public class TooManyRequestException extends RuntimeException {
    private int statusCode;

    private Object body;

    public TooManyRequestException() {
    }

    public TooManyRequestException(String message) {
        super(message);
    }

    public TooManyRequestException(String message, Object body, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Object getBody() {
        return body;
    }
}
