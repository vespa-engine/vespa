// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class to fork and exec a program, and gets its exit status and output.
 *
 * @author hakonhall
 */
public class Command {
    private static Logger logger = Logger.getLogger(Command.class.getName());
    private static Pattern ARGUMENT_PATTERN = Pattern.compile("^[a-zA-Z0-9=@%/+:.,_-]+$");

    private final TaskContext context;
    private final List<String> arguments = new ArrayList<>();

    public Command(TaskContext context) {
        this.context = context;
    }

    public Command add(String... arguments) { return add(Arrays.asList(arguments)); }
    public Command add(List<String> arguments) {
        this.arguments.addAll(arguments);
        return this;
    }

    public ChildProcess spawn(Logger commandLogger) {
        if (arguments.isEmpty()) {
            throw new IllegalStateException("No program has been specified");
        }

        String commandLine = commandLine();
        if (commandLogger != null) {
            context.logSystemModification(commandLogger, "Executing command: " + commandLine);
        }

        // Why isn't this using TaskContext.fileSystem? Because createTempFile assumes
        // default FileSystem. And Jimfs doesn't support toFile() needed for Redirect below.
        Path temporaryFile = IOExceptionUtil.uncheck(() -> Files.createTempFile(
                Command.class.getSimpleName() + "-",
                ".out"));

        ProcessBuilder builder = new ProcessBuilder(arguments)
                .redirectError(ProcessBuilder.Redirect.appendTo(temporaryFile.toFile()))
                .redirectOutput(temporaryFile.toFile());
        Process process = IOExceptionUtil.uncheck(builder::start);

        return new ChildProcessImpl(context, process, temporaryFile, commandLine);
    }

    String commandLine() {
        return arguments.stream().map(Command::maybeEscapeArgument).collect(Collectors.joining(" "));
    }

    private static String maybeEscapeArgument(String argument) {
        if (ARGUMENT_PATTERN.matcher(argument).matches()) {
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

    /**
     * Spawns a process that do not modify the system.
     *
     * This method can also be used to spawn a process that MAY have side effects
     * to be determined at some later time. The caller is then responsible for calling
     * TaskContext::logSystemModification afterwards. The caller is encouraged to
     * call ChildProcess::logAsModifyingSystemAfterAll to do this.
     */
    public ChildProcess spawnProgramWithoutSideEffects() {
        return spawn(null);
    }
}
