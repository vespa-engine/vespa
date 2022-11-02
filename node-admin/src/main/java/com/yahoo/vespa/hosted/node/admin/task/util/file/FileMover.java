// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Utility for idempotent move of (any type of) file.
 *
 * @author hakonhall
 */
public class FileMover {
    private static final Logger logger = Logger.getLogger(FileMover.class.getName());

    private final Path source;
    private final Path destination;
    private final Set<CopyOption> moveOptions = new HashSet<>();

    public FileMover(Path source, Path destination) {
        this.source = source;
        this.destination = destination;
    }

    public FileMover replaceExisting() {
        moveOptions.add(StandardCopyOption.REPLACE_EXISTING);
        return this;
    }

    public FileMover atomic() {
        moveOptions.add(StandardCopyOption.ATOMIC_MOVE);
        return this;
    }

    /**
     * Move file.
     *
     * @return false if the source doesn't exist while the destination do.
     * @see Files#move(Path, Path, CopyOption...) Files.move()
     */
    public boolean converge(TaskContext context) {
        if (!Files.exists(source) && Files.exists(destination)) return false;
        uncheck(() -> Files.move(source, destination, moveOptions.toArray(CopyOption[]::new)));
        context.recordSystemModification(logger, "Moved " + source + " to " + destination);
        return true;
    }
}
