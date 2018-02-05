// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

/**
 * @author hakonhall
 */
public class TerminalImpl {
    private final ProcessFactory processFactory;

    public TerminalImpl(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }

    public CommandLine newCommandLine(TaskContext taskContext) {
        return new CommandLine(taskContext, processFactory);
    }
}
