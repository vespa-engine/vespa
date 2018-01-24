// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProcessImpl implements ProcessApi {
    private static Pattern ARGUMENT_PATTERN = Pattern.compile("^[a-zA-Z0-9=@%/+:.,_-]+$");

    @Override
    public ChildProcessImpl spawn(List<String> arguments, Path outFile) {
        ProcessBuilder builder = new ProcessBuilder(arguments)
                .redirectError(ProcessBuilder.Redirect.appendTo(outFile.toFile()))
                .redirectOutput(outFile.toFile());
        Process process = IOExceptionUtil.uncheck(builder::start);
        return new ChildProcessImpl(process, outFile, commandLine(arguments));
    }

    String commandLine(List<String> arguments) {
        return arguments.stream()
                .map(ProcessImpl::maybeEscapeArgument)
                .collect(Collectors.joining(" "));
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
}
