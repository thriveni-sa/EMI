package com.retail.engine.exception;

/**
 * Thrown when configuration parameters are missing, corrupted, or unparseable.
 */
public class ConfigurationException extends EngineException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

