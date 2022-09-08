// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

public class CommandLineTest {
    private final TestTerminal terminal = new TestTerminal();
    private final TestTaskContext context = new TestTaskContext();
    private final CommandLine commandLine = terminal.newCommandLine(context);

    @AfterEach
    public void tearDown() {
        terminal.verifyAllCommandsExecuted();
    }

    @Test
    void testStrings() {
        terminal.expectCommand(
                "/bin/bash \"with space\" \"speci&l\" \"\" \"double\\\"quote\" 2>&1",
                0,
                "");
        commandLine.add("/bin/bash", "with space", "speci&l", "", "double\"quote").execute();
        assertEquals("bash", commandLine.programName());
    }

    @Test
    void testBasicExecute() {
        terminal.expectCommand("foo bar 2>&1", 0, "line1\nline2\n\n");
        CommandResult result = commandLine.add("foo", "bar").execute();
        assertEquals(0, result.getExitCode());
        assertEquals("line1\nline2", result.getOutput());
        assertEquals("line1\nline2\n\n", result.getUntrimmedOutput());
        assertEquals(List.of("line1", "line2"), result.getOutputLines());
        assertEquals(1, context.getSystemModificationLog().size());
        assertEquals("Executing command: foo bar 2>&1", context.getSystemModificationLog().get(0));

        List<CommandLine> commandLines = terminal.getTestProcessFactory().getMutableCommandLines();
        assertEquals(1, commandLines.size());
        assertEquals(commandLine, commandLines.get(0));

        int lines = result.map(r -> r.getOutputLines().size());
        assertEquals(2, lines);
    }

    @Test
    void verifyDefaults() {
        assertEquals(CommandLine.DEFAULT_TIMEOUT, commandLine.getTimeout());
        assertEquals(CommandLine.DEFAULT_MAX_OUTPUT_BYTES, commandLine.getMaxOutputBytes());
        assertEquals(CommandLine.DEFAULT_SIGTERM_GRACE_PERIOD, commandLine.getSigTermGracePeriod());
        assertEquals(CommandLine.DEFAULT_SIGKILL_GRACE_PERIOD, commandLine.getSigKillGracePeriod());
        assertEquals(0, commandLine.getArguments().size());
        assertEquals(Optional.empty(), commandLine.getOutputFile());
        assertEquals(StandardCharsets.UTF_8, commandLine.getOutputEncoding());
        assertTrue(commandLine.getRedirectStderrToStdoutInsteadOfDiscard());
        Predicate<Integer> defaultExitCodePredicate = commandLine.getSuccessfulExitCodePredicate();
        assertTrue(defaultExitCodePredicate.test(0));
        assertFalse(defaultExitCodePredicate.test(1));
    }

    @Test
    void executeSilently() {
        terminal.ignoreCommand("");
        commandLine.add("foo", "bar").executeSilently();
        assertEquals(0, context.getSystemModificationLog().size());
        commandLine.recordSilentExecutionAsSystemModification();
        assertEquals(1, context.getSystemModificationLog().size());
        assertEquals("Executed command: foo bar 2>&1", context.getSystemModificationLog().get(0));
    }

    @Test
    void processFactorySpawnFails() {
        assertThrows(NegativeArraySizeException.class, () -> {
            terminal.interceptCommand(
                    commandLine.toString(),
                    command -> {
                        throw new NegativeArraySizeException();
                    });
            commandLine.add("foo").execute();
        });
    }

    @Test
    void waitingForTerminationExceptionStillClosesChild() {
        TestChildProcess2 child = new TestChildProcess2(0, "");
        child.throwInWaitForTermination(new NegativeArraySizeException());
        terminal.interceptCommand(commandLine.toString(), command -> child);
        assertFalse(child.closeCalled());
        try {
            commandLine.add("foo").execute();
            fail();
        } catch (NegativeArraySizeException e) {
            // OK
        }

        assertTrue(child.closeCalled());
    }

    @Test
    void programFails() {
        terminal.expectCommand("foo 2>&1", 1, "");
        try {
            commandLine.add("foo").execute();
            fail();
        } catch (ChildProcessFailureException e) {
            assertEquals(
                    "Command 'foo 2>&1' terminated with exit code 1: stdout/stderr: ''",
                    e.getMessage());
        }
    }

    @Test
    void mapException() {
        terminal.ignoreCommand("output");
        CommandResult result = terminal.newCommandLine(context).add("program").execute();
        IllegalArgumentException exception = new IllegalArgumentException("foo");
        try {
            result.mapOutput(output -> {
                throw exception;
            });
            fail();
        } catch (UnexpectedOutputException e) {
            assertEquals("Command 'program 2>&1' output was not of the expected format: " +
                    "Failed to map output: stdout/stderr: 'output'", e.getMessage());
            assertEquals(e.getCause(), exception);
        }
    }

    @Test
    void testMapEachLine() {
        assertEquals(
                1 + 2 + 3,
                terminal.ignoreCommand("1\n2\n3\n")
                        .newCommandLine(context)
                        .add("foo")
                        .execute()
                        .mapEachLine(Integer::valueOf)
                        .stream()
                        .mapToInt(i -> i)
                        .sum());
    }

    @Test
    void addTokensWithMultipleWhiteSpaces() {
        terminal.expectCommand("iptables -L 2>&1");
        commandLine.addTokens("iptables  -L").execute();

        terminal.verifyAllCommandsExecuted();
    }

    @Test
    void addTokensWithSpecialCharacters() {
        terminal.expectCommand("find . ! -name hei 2>&1");
        commandLine.addTokens("find . ! -name hei").execute();

        terminal.verifyAllCommandsExecuted();
    }

    @Test
    void testEnvironment() {
        terminal.expectCommand("env k1=v1 -u k2 \"key 3=value 3\" programname 2>&1");
        commandLine.add("programname")
                .setEnvironmentVariable("key 3", "value 3")
                .removeEnvironmentVariable("k2")
                .setEnvironmentVariable("k1", "v1")
                .execute();
        terminal.verifyAllCommandsExecuted();
    }

    @Test
    public void testToString() {
        commandLine.add("bash", "-c", "echo", "$MY_SECRET");
        assertEquals("bash -c echo \"$MY_SECRET\" 2>&1", commandLine.toString());
        commandLine.censorArgument();
        assertEquals("bash -c echo <censored> 2>&1", commandLine.toString());

        terminal.expectCommand("bash -c echo \"$MY_SECRET\" 2>&1");
        commandLine.execute();
        terminal.verifyAllCommandsExecuted();
    }

}
