// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author hakonhall
 */
public class TestCommandSupplier implements Supplier<Command> {
    private final TaskContext taskContext;
    private final List<TestCommand> expectedInvocations = new ArrayList<>();
    private int index = 0;

    public TestCommandSupplier(TaskContext taskContext) {
        this.taskContext = taskContext;
    }

    public TestCommandSupplier expectCommand(String commandLine, int exitValue, String out) {
        expectedInvocations.add(new TestCommand(taskContext, commandLine, exitValue, out));
        return this;
    }

    @Override
    public Command get() {
        if (index >= expectedInvocations.size()) {
            throw new IllegalStateException("Too many command invocations: Expected to create " +
                    expectedInvocations.size() + " Command objects");
        }

        return expectedInvocations.get(index++);
    }

    public void verifyInvocations() {
        if (index != expectedInvocations.size()) {
            throw new IllegalStateException("Received only " + index +
                    " command invocations: expected " + expectedInvocations.size());
        }

        for (int i = 0; i < index; ++i) {
            expectedInvocations.get(i).verifyInvocation();
        }
    }
}
