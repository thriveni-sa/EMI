package com.retail.engine.command;

import com.retail.engine.exception.EngineException;
import com.retail.engine.exception.ValidationException;
import com.retail.engine.validation.DataValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses commands.txt preserving sequential file order and strict argument validation.
 */
public final class CommandProcessor {

    private static final String FILE_NAME = "commands.txt";

    public interface CommandHandler {
        void handle(Command command);
    }

    public static List<Command> parseCommands(Path commandFile) {
        if (!Files.exists(commandFile)) {
            throw new EngineException(String.format("[%s] Command file not found: %s", FILE_NAME, commandFile.toAbsolutePath()));
        }

        List<Command> commandList = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(commandFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip comments and empty lines
                }

                String[] parts = line.split("\\|", -1);
                String verb = parts[0].trim();
                CommandType type;
                try {
                    type = CommandType.fromString(verb);
                } catch (IllegalArgumentException e) {
                    throw new ValidationException(String.format(
                            "[%s Line %d] Unrecognized command verb: '%s'",
                            FILE_NAME, lineNo, verb
                    ));
                }

                String[] args = (parts.length > 1) ?
                        Arrays.stream(parts, 1, parts.length).map(String::trim).toArray(String[]::new) :
                        new String[0];

                validateCommandArguments(type, lineNo, args);
                commandList.add(new Command(lineNo, type, args));
            }
        } catch (IOException e) {
            throw new EngineException(String.format("[%s] Error reading commands: %s", FILE_NAME, e.getMessage()), e);
        }

        return commandList;
    }

    private static void validateCommandArguments(CommandType type, int lineNo, String[] args) {
        switch (type) {
            case LOAD_CONFIG -> {
                if (args.length != 1 || args[0].isEmpty()) {
                    throw new ValidationException(String.format("[%s Line %d] LOAD_CONFIG requires 1 argument (file path)", FILE_NAME, lineNo));
                }
            }
            case LOAD_CARDS -> {
                if (args.length != 1 || args[0].isEmpty()) {
                    throw new ValidationException(String.format("[%s Line %d] LOAD_CARDS requires 1 argument (file path)", FILE_NAME, lineNo));
                }
            }
            case BILL -> {
                if (args.length != 1 || args[0].isEmpty()) {
                    throw new ValidationException(String.format("[%s Line %d] BILL requires 1 argument (bill_id)", FILE_NAME, lineNo));
                }
                DataValidator.validateBillId(FILE_NAME, lineNo, args[0]);
            }
            case RETURN -> {
                if (args.length != 1 || args[0].isEmpty()) {
                    throw new ValidationException(String.format("[%s Line %d] RETURN requires 1 argument (return_id)", FILE_NAME, lineNo));
                }
                DataValidator.validateReturnId(FILE_NAME, lineNo, args[0]);
            }
            case PRINT_CASHBACK_REPORT -> {
                if (args.length != 1 || args[0].isEmpty()) {
                    throw new ValidationException(String.format("[%s Line %d] PRINT_CASHBACK_REPORT requires 1 argument (YYYY-MM)", FILE_NAME, lineNo));
                }
                DataValidator.validateYearMonth("PRINT_CASHBACK_REPORT", lineNo, args[0]);
            }
        }
    }
}

