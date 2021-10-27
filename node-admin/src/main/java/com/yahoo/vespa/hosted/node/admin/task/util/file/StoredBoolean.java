// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Class wrapping a boolean stored on disk.
 *
 * <p>The implementation is compatible with {@link StoredInteger} when absence or 0 means false.
 *
 * @author hakonhall
 */
public class StoredBoolean {
    private static final Logger logger = Logger.getLogger(StoredBoolean.class.getName());

    private final UnixPath path;

    /** The parent directory must exist. Value is false by default. */
    public StoredBoolean(Path path) {
        this.path = new UnixPath(path);
    }

    public boolean value() {
        return path.readUtf8FileIfExists().map(String::trim).map(s -> !"0".equals(s)).orElse(false);
    }

    /** Sets value to true. */
    public void set(TaskContext context) {
        if (!value()) {
            context.log(logger, "Writes " + path);
            path.writeUtf8File("1");
        }
    }

    public void set(TaskContext context, boolean value) {
        if (value) {
            set(context);
        } else {
            clear(context);
        }
    }

    /** Sets value to false. */
    public void clear(TaskContext context) {
        if (value()) {
            context.log(logger, "Deletes " + path);
            path.deleteIfExists();
        }
    }
}
