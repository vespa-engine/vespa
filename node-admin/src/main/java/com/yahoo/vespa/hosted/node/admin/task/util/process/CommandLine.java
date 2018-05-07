// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A CommandLine is used to specify and execute a shell-like program in a child process,
 * and capture its output.
 *
 * @author hakonhall
 */
public class CommandLine {
    private static final Logger logger = Logger.getLogger(CommandLine.class.getName());
    private static final Pattern UNESCAPED_ARGUMENT_PATTERN = Pattern.compile("^[a-zA-Z0-9=!@%/+:.,_-]+$");

    /** The default timeout. See setTimeout() for details. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    /** The default maximum number of output bytes. See setMaxOutputBytes() for details. */
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024 * 1024; // 1 Gb

    /**
     * The default grace period after SIGTERM has been sent during a graceful kill.
     * See setSigTermGracePeriod for details.
     */
    public static final Duration DEFAULT_SIGTERM_GRACE_PERIOD = Duration.ofMinutes(1);

    /**
     * The default grace period after SIGKILL has been sent during a graceful kill.
     * See setSigKillGracePeriod for details.
     */
    public static final Duration DEFAULT_SIGKILL_GRACE_PERIOD = Duration.ofMinutes(30);

    private final List<String> arguments = new ArrayList<>();
    private final TaskContext taskContext;
    private final ProcessFactory processFactory;

    private boolean redirectStderrToStdoutInsteadOfDiscard = true;
    private boolean executeSilentlyCalled = false;
    private Charset outputEncoding = StandardCharsets.UTF_8;
    private Duration timeout = DEFAULT_TIMEOUT;
    private long maxOutputBytes = DEFAULT_MAX_OUTPUT_BYTES;
    private Duration sigTermGracePeriod = DEFAULT_SIGTERM_GRACE_PERIOD;
    private Duration sigKillGracePeriod = DEFAULT_SIGKILL_GRACE_PERIOD;
    private Predicate<Integer> successfulExitCodePredicate = code -> code == 0;

    public CommandLine(TaskContext taskContext, ProcessFactory processFactory) {
        this.taskContext = taskContext;
        this.processFactory = processFactory;
    }

    /** Add arguments to the command. The first argument in the first call to add() is the program. */
    public CommandLine add(String... arguments) { return add(Arrays.asList(arguments)); }

    /** Add arguments to the command. The first argument in the first call to add() is the program. */
    public CommandLine add(Collection<String> arguments) {
        this.arguments.addAll(arguments);
        return this;
    }

    /** Add arguments by splitting arguments by space. */
    public CommandLine addTokens(String arguments) {
        return add(arguments.split("\\s+"));
    }

    /**
     * Execute a shell-like program in a child process:
     *  - the program is recorded and logged as modifying the system, but see executeSilently().
     *  - the program's stderr is redirected to stdout, but see discardStderr().
     *  - the program's output is assumed to be UTF-8, but see setOutputEncoding().
     *  - the program must terminate with exit code 0, but see ignoreExitCode().
     *  - the output of the program will be accessible in the returned CommandResult.
     *
     * Footnote 1: As a safety measure the size of the output is capped, and the program is
     * only allowed to execute up to a timeout. The defaults are set high so you typically do
     * not have to worry about reaching these limits, but otherwise see setMaxOutputBytes()
     * and setTimeout(), respectively.
     *
     * Footnote 2: If the child process is forced to be killed due to footnote 1, then
     * setSigTermGracePeriod() and setSigKillGracePeriod() can be used to tweak how much time
     * is given to the program to shut down. Again, the defaults should be reasonable.
     */
    public CommandResult execute() {
        taskContext.recordSystemModification(logger, "Executing command: " + toString());
        return doExecute();
    }

    /**
     * Same as execute(), except it will not record the program as modifying the system.
     *
     * If the program is later found to have modified the system, or otherwise worthy of
     * a record, call recordSilentExecutionAsSystemModification().
     */
    public CommandResult executeSilently() {
        executeSilentlyCalled = true;
        return doExecute();
    }

    /**
     * Record an already executed executeSilently() as having modified the system.
     * For instance with YUM it is not known until after a 'yum install' whether it
     * modified the system.
     */
    public void recordSilentExecutionAsSystemModification() {
        if (!executeSilentlyCalled) {
            throw new IllegalStateException("executeSilently has not been called");
        }
        // Disallow multiple consecutive calls to this method without an intervening call
        // to executeSilently().
        executeSilentlyCalled = false;

        taskContext.recordSystemModification(logger, "Executed command: " + toString());
    }

    /**
     * The first argument of the command specifies the program and is either the program's
     * filename (in case the environment variable PATH will be used to search for the program
     * file) or a path with the last component being the program's filename.
     *
     * @return The filename of the program.
     */
    public String programName() {
        if (arguments.isEmpty()) {
            throw new IllegalStateException(
                    "The program name cannot be determined yet as no arguments have been given");
        }
        String path = arguments.get(0);
        int lastIndex = path.lastIndexOf('/');
        if (lastIndex == -1) {
            return path;
        } else {
            return path.substring(lastIndex + 1);
        }
    }

    /** Returns a shell-like representation of the command. */
    @Override
    public String toString() {
        String command = arguments.stream()
                .map(CommandLine::maybeEscapeArgument)
                .collect(Collectors.joining(" "));

        // Note: Both of these cannot be confused with an argument since they would
        // require escaping.
        command += redirectStderrToStdoutInsteadOfDiscard ? " 2>&1" : " 2>/dev/null";

        return command;
    }


    /**
     * By default, stderr is redirected to stderr. This method will instead discard stderr.
     */
    public CommandLine discardStderr() {
        this.redirectStderrToStdoutInsteadOfDiscard = false;
        return this;
    }

    /**
     * By default, a non-zero exit code will cause the command execution to fail. This method
     * will instead ignore the exit code.
     */
    public CommandLine ignoreExitCode() {
        this.successfulExitCodePredicate = code -> true;
        return this;
    }

    /**
     * By default, the output of the command is parsed as UTF-8. This method will set a
     * different encoding.
     */
    public CommandLine setOutputEncoding(Charset outputEncoding) {
        this.outputEncoding = outputEncoding;
        return this;
    }

    /**
     * By default, the command will be gracefully killed after DEFAULT_TIMEOUT. This method
     * overrides that default.
     */
    public CommandLine setTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * By default, the command will be gracefully killed if it ever outputs more than
     * DEFAULT_MAX_OUTPUT_BYTES. This method overrides that default.
     */
    public CommandLine setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
        return this;
    }

    /**
     * By default, if the program needs to be gracefully killed it will wait up to
     * DEFAULT_SIGTERM_GRACE_PERIOD for the program to exit after it has been killed with
     * the SIGTERM signal.
     */
    public CommandLine setSigTermGracePeriod(Duration period) {
        this.sigTermGracePeriod = period;
        return this;
    }

    public CommandLine setSigKillGracePeriod(Duration period) {
        this.sigTermGracePeriod = period;
        return this;
    }
    // Accessor fields necessary for classes in this package. Could be public if necessary.
    List<String> getArguments() { return Collections.unmodifiableList(arguments); }
    boolean getRedirectStderrToStdoutInsteadOfDiscard() { return redirectStderrToStdoutInsteadOfDiscard; }
    Predicate<Integer> getSuccessfulExitCodePredicate() { return successfulExitCodePredicate; }
    Charset getOutputEncoding() { return outputEncoding; }
    Duration getTimeout() { return timeout; }
    long getMaxOutputBytes() { return maxOutputBytes; }
    Duration getSigTermGracePeriod() { return sigTermGracePeriod; }
    Duration getSigKillGracePeriod() { return sigKillGracePeriod; }

    private CommandResult doExecute() {
        try (ChildProcess2 child = processFactory.spawn(this)) {
            child.waitForTermination();
            int exitCode = child.exitCode();
            if (!successfulExitCodePredicate.test(exitCode)) {
                throw new ChildProcessFailureException(exitCode, toString(), child.getOutput());
            }

            String output = child.getOutput();
            return new CommandResult(this, exitCode, output);
        }
    }

    private static String maybeEscapeArgument(String argument) {
        if (UNESCAPED_ARGUMENT_PATTERN.matcher(argument).matches()) {
            return argument;
        } else {
            return escapeArgument(argument);
        }
    }

    private static String escapeArgument(String argument) {
        StringBuilder doubleQuoteEscaped = new StringBuilder(argument.length() + 10);

        for (int i = 0; i < argument.length(); ++i) {
            char c = argument.charAt(i);
            switch (c) {
                case '"':
                case '\\':
                    doubleQuoteEscaped.append("\\").append(c);
                    break;
                default:
                    doubleQuoteEscaped.append(c);
            }
        }

        return "\"" + doubleQuoteEscaped + "\"";
    }
}
