package dev.crashteam.styx.exception;

public class NonValidHttpMethodException extends RuntimeException {

    public NonValidHttpMethodException() {
        super();
    }

    public NonValidHttpMethodException(String message) {
        super(message);
    }

    public NonValidHttpMethodException(String message, Throwable cause) {
        super(message, cause);
    }
}
