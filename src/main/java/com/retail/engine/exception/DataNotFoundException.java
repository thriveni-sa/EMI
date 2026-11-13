package com.retail.engine.exception;

/**
 * Thrown when an entity lookup (card, bill, return) fails.
 */
public class DataNotFoundException extends EngineException {

    public DataNotFoundException(String message) {
        super(message);
    }

    public DataNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

