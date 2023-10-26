// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool;

import org.apache.commons.cli.CommandLine;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author vekterli
 */
public record ToolInvocation(CommandLine arguments,
                             Map<String, String> envVars,
                             InputStream stdIn,
                             PrintStream stdOut,
                             PrintStream stdError,
                             ConsoleInput consoleInputOrNull,
                             boolean debugMode) {

    public void printIfDebug(Supplier<String> stringSupplier) {
        if (debugMode) {
            stdError.println(stringSupplier.get());
        }
    }

}
