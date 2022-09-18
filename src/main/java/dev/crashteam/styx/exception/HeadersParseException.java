package dev.crashteam.styx.exception;

public class HeadersParseException extends RuntimeException {

    public HeadersParseException() {
    }

    public HeadersParseException(String message) {
        super(message);
    }

    public HeadersParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
