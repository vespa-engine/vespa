// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author hakonhall
 */
public class TestProcessFactory implements ProcessFactory {
    private static class SpawnCall {
        private final String commandDescription;
        private final Function<CommandLine, ChildProcess2> callback;

        private SpawnCall(String commandDescription,
                          Function<CommandLine, ChildProcess2> callback) {
            this.commandDescription = commandDescription;
            this.callback = callback;
        }
    }
    private final List<SpawnCall> expectedSpawnCalls = new ArrayList<>();
    private final List<CommandLine> spawnCommandLines = new ArrayList<>();

    /** Forward call to spawn() to callback. */
    public TestProcessFactory interceptSpawn(String commandDescription,
                                             Function<CommandLine, ChildProcess2> callback) {
        expectedSpawnCalls.add(new SpawnCall(commandDescription, callback));
        return this;
    }

    // Convenience method for the caller to avoid having to create a TestChildProcess2 instance.
    public TestProcessFactory expectSpawn(String commandLineString, TestChildProcess2 toReturn) {
        return interceptSpawn(
                commandLineString,
                commandLine -> defaultSpawn(commandLine, commandLineString, toReturn));
    }

    // Convenience method for the caller to avoid having to create a TestChildProcess2 instance.
    public TestProcessFactory expectSpawn(String commandLine, int exitCode, String output) {
        return expectSpawn(commandLine, new TestChildProcess2(exitCode, output));
    }

    /** Ignore the CommandLine passed to spawn(), just return successfully with the given output. */
    public TestProcessFactory ignoreSpawn(String output) {
        return interceptSpawn(
                "[call index " + expectedSpawnCalls.size() + "]",
                commandLine -> new TestChildProcess2(0, output));
    }

    public TestProcessFactory ignoreSpawn() {
        return ignoreSpawn("");
    }

    public void verifyAllCommandsExecuted() {
        if (spawnCommandLines.size() < expectedSpawnCalls.size()) {
            int missingCommandIndex = spawnCommandLines.size();
            throw new IllegalStateException("Command #" + missingCommandIndex +
                    " never executed: " +
                    expectedSpawnCalls.get(missingCommandIndex).commandDescription);
        }
    }

    /**
     * WARNING: CommandLine is mutable, and e.g. reusing a CommandLine for the next call
     * would make the CommandLine in this list no longer reflect the original CommandLine.
     */
    public List<CommandLine> getMutableCommandLines() {
        return spawnCommandLines;
    }

    @Override
    public ChildProcess2 spawn(CommandLine commandLine) {
        String commandLineString = commandLine.toString();
        if (spawnCommandLines.size() + 1 > expectedSpawnCalls.size()) {
            throw new IllegalStateException("Too many invocations: " + commandLineString);
        }
        spawnCommandLines.add(commandLine);

        return expectedSpawnCalls.get(spawnCommandLines.size() - 1).callback.apply(commandLine);
    }

    private static ChildProcess2 defaultSpawn(CommandLine commandLine,
                                              String expectedCommandLineString,
                                              ChildProcess2 toReturn) {
        String actualCommandLineString = commandLine.toString();
        if (!Objects.equals(actualCommandLineString, expectedCommandLineString)) {
            throw new IllegalArgumentException("Expected command line '" +
                    expectedCommandLineString + "' but got '" + actualCommandLineString + "'");
        }

        return toReturn;
    }
}
