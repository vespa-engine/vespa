// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;

import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

public class TestTaskContext implements TaskContext {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final List<String> logs = new ArrayList<>();

    @Override
    public Cloud cloud() {
        return Cloud.YAHOO;
    }

    @Override
    public EnumSet<Role> roles() {
        return EnumSet.of(Role.CONFIG_SERVER_DOCKER_HOST);
    }

    @Override
    public FileSystem fileSystem() {
        return fileSystem;
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
}
