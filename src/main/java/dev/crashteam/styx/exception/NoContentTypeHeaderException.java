package dev.crashteam.styx.exception;

public class NoContentTypeHeaderException extends RuntimeException {

    public NoContentTypeHeaderException() {
    }

    public NoContentTypeHeaderException(String message) {
        super(message);
    }

    public NoContentTypeHeaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
