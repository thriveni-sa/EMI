package com.retail.engine.command;

/**
 * Commands supported by the batch processing engine.
 */
public enum CommandType {
    LOAD_CONFIG,
    LOAD_CARDS,
    BILL,
    RETURN,
    PRINT_CASHBACK_REPORT;

    public static CommandType fromString(String verb) {
        if (verb == null || verb.trim().isEmpty()) {
            throw new IllegalArgumentException("Command verb cannot be empty");
        }
        return CommandType.valueOf(verb.trim().toUpperCase());
    }
}

