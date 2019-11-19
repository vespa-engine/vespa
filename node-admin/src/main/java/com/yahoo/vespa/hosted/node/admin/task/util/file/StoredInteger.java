// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Class wrapping an integer stored on disk
 *
 * @author freva
 */
public class StoredInteger implements Supplier<OptionalInt> {

    private static final Logger logger = Logger.getLogger(StoredInteger.class.getName());

    private final UnixPath path;

    public StoredInteger(Path path) {
        this.path = new UnixPath(path);
    }

    @Override
    public OptionalInt get() {
        return path.readUtf8FileIfExists().stream().mapToInt(Integer::parseInt).findAny();
    }

    public void write(TaskContext taskContext, int value) {
        path.writeUtf8File(Integer.toString(value));
        taskContext.log(logger, "Stored new integer in %s: %d", path, value);
    }

    public Optional<Instant> getLastModifiedTime() {
        return path.getAttributesIfExists().map(FileAttributes::lastModifiedTime);
    }

}
