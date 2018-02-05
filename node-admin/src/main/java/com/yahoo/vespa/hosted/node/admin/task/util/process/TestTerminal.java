// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.util.function.Function;

/**
 * @author hakonhall
 */
public class TestTerminal implements Terminal {
    private final TerminalImpl realTerminal;
    private final TestProcessFactory testProcessFactory = new TestProcessFactory();

    public TestTerminal() {
        this.realTerminal = new TerminalImpl(testProcessFactory);
    }

    /** Get the TestProcessFactory the terminal was started with. */
    public TestProcessFactory getTestProcessFactory() { return testProcessFactory; }

    /** Forward call to spawn() to callback. */
    public TestTerminal interceptCommand(String commandDescription,
                                         Function<CommandLine, ChildProcess2> callback) {
        testProcessFactory.interceptSpawn(commandDescription, callback);
        return this;
    }

    /** Wraps expectSpawn in TestProcessFactory, provided here as convenience. */
    public TestTerminal expectCommand(String commandLine, TestChildProcess2 toReturn) {
        testProcessFactory.expectSpawn(commandLine, toReturn);
        return this;
    }

    /** Wraps expectSpawn in TestProcessFactory, provided here as convenience. */
    public TestTerminal expectCommand(String commandLine, int exitCode, String output) {
        testProcessFactory.expectSpawn(commandLine, new TestChildProcess2(exitCode, output));
        return this;
    }

    /** Verifies command line matches commandLine, and returns successfully with output "". */
    public TestTerminal expectCommand(String commandLine) {
        expectCommand(commandLine, 0, "");
        return this;
    }

    /** Wraps expectSpawn in TestProcessFactory, provided here as convenience. */
    public TestTerminal ignoreCommand(String output) {
        testProcessFactory.ignoreSpawn(output);
        return this;
    }

    /** Wraps expectSpawn in TestProcessFactory, provided here as convenience. */
    public TestTerminal ignoreCommand() {
        testProcessFactory.ignoreSpawn();
        return this;
    }

    public void verifyAllCommandsExecuted() {
        testProcessFactory.verifyAllCommandsExecuted();
    }

    @Override
    public CommandLine newCommandLine(TaskContext taskContext) {
        return realTerminal.newCommandLine(taskContext);
    }
}
