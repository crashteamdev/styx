package dev.crashteam.styx.exception;

public class ProxyForbiddenException extends RuntimeException {

    private int statusCode;

    private Object body;

    public ProxyForbiddenException() {
    }

    public ProxyForbiddenException(String message) {
        super(message);
    }

    public ProxyForbiddenException(String message, Object body, int statusCode) {
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
