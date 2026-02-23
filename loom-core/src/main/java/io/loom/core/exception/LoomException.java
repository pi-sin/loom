package io.loom.core.exception;

public class LoomException extends RuntimeException {
    public LoomException(String message) {
        super(message);
    }

    public LoomException(String message, Throwable cause) {
        super(message, cause);
    }
}
