package com.retail.engine.exception;

/**
 * Thrown when data violates format, domain, or relational constraints.
 */
public class ValidationException extends EngineException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

