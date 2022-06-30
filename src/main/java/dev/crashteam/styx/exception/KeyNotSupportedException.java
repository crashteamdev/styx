package dev.crashteam.styx.exception;

public class KeyNotSupportedException extends RuntimeException {

    public KeyNotSupportedException() {
    }

    public KeyNotSupportedException(String message) {
        super(message);
    }

    public KeyNotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
