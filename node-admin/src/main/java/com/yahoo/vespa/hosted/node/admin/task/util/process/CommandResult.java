// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A CommandResult is the result of the execution of a CommandLine.
 *
 * @author hakonhall
 */
public class CommandResult {
    private static final Pattern NEWLINE = Pattern.compile("\\n");

    private final CommandLine commandLine;
    private final int exitCode;
    private final String output;

    CommandResult(CommandLine commandLine, int exitCode, String output) {
        this.commandLine = commandLine;
        this.exitCode = exitCode;
        this.output = output;
    }

    public int getExitCode() {
        return exitCode;
    }

    /** Returns the output with leading and trailing white-space removed. */
    public String getOutput() { return output.trim(); }

    public String getUntrimmedOutput() { return output; }

    /** Returns the output lines of the command, omitting trailing empty lines. */
    public List<String> getOutputLines() {
        return getOutputLinesStream().collect(Collectors.toList());
    }

    public Stream<String> getOutputLinesStream() {
        if (output.isEmpty()) {
            // For some reason an empty string => one-element list.
            return Stream.empty();
        }

        // For some reason this removes trailing empty elements, but that's OK with us.
        return NEWLINE.splitAsStream(output);
    }

    /**
     * Convenience method for getting the CommandLine, whose execution resulted in
     * this CommandResult instance.
     *
     * Warning: the CommandLine is mutable and may be changed by the caller of the execution
     * through other references! This is just a convenience method for getting that instance.
     */
    public CommandLine getCommandLine() { return commandLine; }
}
