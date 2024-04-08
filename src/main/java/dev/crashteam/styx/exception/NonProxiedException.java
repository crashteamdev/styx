package dev.crashteam.styx.exception;

public class NonProxiedException extends RuntimeException{
    public NonProxiedException() {
        super();
    }

    public NonProxiedException(String message) {
        super(message);
    }
}
