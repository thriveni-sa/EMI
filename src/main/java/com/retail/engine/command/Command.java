package com.retail.engine.command;

import java.util.Arrays;
import java.util.Objects;

/**
 * Value object representing a parsed command from commands.txt.
 */
public record Command(int lineNo, CommandType type, String[] arguments) {

    public Command {
        Objects.requireNonNull(type, "CommandType must not be null");
        arguments = (arguments == null) ? new String[0] : arguments.clone();
    }

    public String getArgument(int index) {
        if (index < 0 || index >= arguments.length) {
            throw new IndexOutOfBoundsException("Argument index out of bounds: " + index);
        }
        return arguments[index];
    }

    @Override
    public String toString() {
        return "Command{" +
                "lineNo=" + lineNo +
                ", type=" + type +
                ", arguments=" + Arrays.toString(arguments) +
                '}';
    }
}

