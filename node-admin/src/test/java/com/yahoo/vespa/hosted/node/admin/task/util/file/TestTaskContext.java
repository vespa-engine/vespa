// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.IdempotentTask;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

public class TestTaskContext implements TaskContext {
    private final List<String> logs = new ArrayList<>();

    @Override
    public Cloud cloud() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EnumSet<Role> roles() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileSystem fileSystem() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void logSystemModification(Logger logger, String actionDescription) {
        logs.add(actionDescription);
    }

    public List<String> getSystemModificationLog() {
        return logs;
    }

    public void clearSystemModificationLog() {
        logs.clear();
    }

    @Override
    public boolean executeSubtask(IdempotentTask task) {
        throw new UnsupportedOperationException();
    }
}
