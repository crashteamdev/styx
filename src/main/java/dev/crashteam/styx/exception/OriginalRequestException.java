package dev.crashteam.styx.exception;

public class OriginalRequestException extends RuntimeException {

    private int statusCode;

    private Object body;

    public OriginalRequestException(String message) {
        super(message);
    }

    public OriginalRequestException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public OriginalRequestException(String message, Object body, int statusCode) {
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
