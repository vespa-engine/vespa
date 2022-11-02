// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Deletes a file or empty directory.
 *
 * @author hakonhall
 */
public class FileDeleter {
    private static final Logger logger = Logger.getLogger(FileDeleter.class.getName());

    private final Path path;

    public FileDeleter(Path path) {
        this.path = path;
    }

    public boolean converge(TaskContext context) {
        boolean deleted = uncheck(() -> Files.deleteIfExists(path));
        if (deleted) {
            context.recordSystemModification(logger, "Deleted " + path);
        }

        return deleted;
    }
}
