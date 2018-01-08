// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task;

import com.yahoo.vespa.hosted.node.admin.io.FileSystem;

public interface Task {
    interface TaskContext {
        FileSystem getFileSystem();
        boolean executeSubtask(Task task);
    }

    /**
     * @return Returns false if task was a no-op. Used for informational purposes only.
     * @throws RuntimeException if task could not be completed.
     */
    boolean execute(TaskContext context);
}
