// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

/**
 * A Terminal is a light-weight terminal-like interface for executing shell-like programs.
 *
 * @author hakonhall
 */
public interface Terminal {
    CommandLine newCommandLine(TaskContext taskContext);
}
