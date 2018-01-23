// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task;

import com.yahoo.vespa.hosted.node.admin.io.FileSystem;

import java.nio.file.Path;

public class MakeDirectoryTask implements Task {
    private final Path path;
    private boolean withParents = false;

    public MakeDirectoryTask(Path path) {
        this.path = path;
    }

    public MakeDirectoryTask withParents() {
        this.withParents = true;
        return this;
    }

    Path getPath() {
        return path;
    }

    boolean getWithParents() {
        return withParents;
    }

    private boolean makeDirectory(FileSystem fileSystem,
                                  Path directory,
                                  boolean withParents) {
        if (fileSystem.isDirectory(directory)) {
            return false;
        }

        if (withParents) {
            makeDirectory(fileSystem, directory.getParent(), withParents);
        }

        fileSystem.createDirectory(directory);

        return true;
    }

    @Override
    public boolean execute(TaskContext context) {
        return makeDirectory(context.getFileSystem(), path, withParents);
    }
}
