package com.retail.engine;

import com.retail.engine.command.Command;
import com.retail.engine.command.CommandProcessor;
import com.retail.engine.command.CommandType;
import com.retail.engine.exception.EngineException;
import com.retail.engine.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Command Processing & Sequential Execution Order Tests")
public class CommandProcessingTest {

    @Test
    @DisplayName("Parse official commands.txt: verify exactly 218 executable commands")
    void testParseOfficialCommands() {
        Path commandsPath = Paths.get("commands.txt").toAbsolutePath().normalize();
        if (!Files.exists(commandsPath)) {
            commandsPath = Paths.get("data", "commands.txt").toAbsolutePath().normalize();
        }
        assertTrue(Files.exists(commandsPath), "commands.txt must exist");

        List<Command> commands = CommandProcessor.parseCommands(commandsPath);
        assertEquals(218, commands.size(), "commands.txt must contain exactly 218 commands");

        // Verify initial setup commands
        assertEquals(CommandType.LOAD_CONFIG, commands.get(0).type());
        assertEquals("offer_config.csv", commands.get(0).getArgument(0));

        assertEquals(CommandType.LOAD_CARDS, commands.get(1).type());
        assertEquals("cards.csv", commands.get(1).getArgument(0));

        // First transaction command is BILL|BILL-0001
        assertEquals(CommandType.BILL, commands.get(2).type());
        assertEquals("BILL-0001", commands.get(2).getArgument(0));

        // Final command is PRINT_CASHBACK_REPORT
        Command lastCmd = commands.get(commands.size() - 1);
        assertEquals(CommandType.PRINT_CASHBACK_REPORT, lastCmd.type());
    }

    @Test
    @DisplayName("Comments starting with '#' and blank lines are ignored")
    void testCommentsAndBlankLinesIgnored(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test_commands.txt");
        String content = "# Initial setup phase\n" +
                "\n" +
                "LOAD_CONFIG|offer_config.csv\n" +
                "# Load cards\n" +
                "\n" +
                "LOAD_CARDS|cards.csv\n" +
                "# Bill processing\n" +
                "BILL|BILL-0001\n" +
                "\n" +
                "# Final report\n" +
                "PRINT_CASHBACK_REPORT|2026-10\n";
        Files.writeString(file, content);

        List<Command> parsed = CommandProcessor.parseCommands(file);
        assertEquals(4, parsed.size(), "Only the 4 non-comment commands should be parsed");
        assertEquals(CommandType.LOAD_CONFIG, parsed.get(0).type());
        assertEquals(CommandType.LOAD_CARDS, parsed.get(1).type());
        assertEquals(CommandType.BILL, parsed.get(2).type());
        assertEquals(CommandType.PRINT_CASHBACK_REPORT, parsed.get(3).type());
    }

    @Test
    @DisplayName("Preserve source line numbers accurately for diagnostics")
    void testSourceLineNumberPreservation(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test_lines.txt");
        String content = "# Line 1: Comment\n" +
                "# Line 2: Comment\n" +
                "LOAD_CONFIG|offer_config.csv\n" + // Line 3
                "\n" +                              // Line 4
                "BILL|BILL-0001\n";                // Line 5
        Files.writeString(file, content);

        List<Command> parsed = CommandProcessor.parseCommands(file);
        assertEquals(2, parsed.size());
        assertEquals(3, parsed.get(0).lineNo());
        assertEquals(5, parsed.get(1).lineNo());
    }

    @Test
    @DisplayName("Unknown command throws ValidationException with line number and verb")
    void testUnknownCommandHandling(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("bad_commands.txt");
        String content = "LOAD_CONFIG|offer_config.csv\n" +
                "UNKNOWN_COMMAND_FOO|bar\n";
        Files.writeString(file, content);

        ValidationException ex = assertThrows(ValidationException.class, () -> CommandProcessor.parseCommands(file));
        assertTrue(ex.getMessage().contains("UNKNOWN_COMMAND_FOO"));
        assertTrue(ex.getMessage().contains("Line 2"));
    }

    @Test
    @DisplayName("Missing command file throws EngineException with clear error message")
    void testMissingCommandFile() {
        Path nonExistent = Paths.get("non_existent_commands_stream_123.txt");
        EngineException ex = assertThrows(EngineException.class, () -> CommandProcessor.parseCommands(nonExistent));
        assertTrue(ex.getMessage().contains("Command file not found"));
    }

    @Test
    @DisplayName("Duplicate commands in stream preserve sequential execution order")
    void testDuplicateCommandsInStream(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("dup_commands.txt");
        String content = "BILL|BILL-0001\n" +
                "BILL|BILL-0001\n";
        Files.writeString(file, content);

        List<Command> parsed = CommandProcessor.parseCommands(file);
        assertEquals(2, parsed.size());
        assertEquals("BILL-0001", parsed.get(0).getArgument(0));
        assertEquals("BILL-0001", parsed.get(1).getArgument(0));
        assertEquals(1, parsed.get(0).lineNo());
        assertEquals(2, parsed.get(1).lineNo());
    }

    @Test
    @DisplayName("Command with insufficient arguments throws ValidationException")
    void testInsufficientCommandArguments(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("short_cmd.txt");
        String content = "BILL\n"; // Missing bill_id argument
        Files.writeString(file, content);

        ValidationException ex = assertThrows(ValidationException.class, () -> CommandProcessor.parseCommands(file));
        assertTrue(ex.getMessage().contains("requires 1 argument"));
    }
}
