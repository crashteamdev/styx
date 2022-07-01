package dev.crashteam.styx.exception;

public class ProxyGlobalException extends RuntimeException {

    public ProxyGlobalException() {
    }

    public ProxyGlobalException(String message) {
        super(message);
    }

    public ProxyGlobalException(String message, Throwable cause) {
        super(message, cause);
    }
}
