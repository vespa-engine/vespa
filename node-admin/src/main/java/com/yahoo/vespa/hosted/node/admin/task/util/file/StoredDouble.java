// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Class wrapping a float stored on disk
 *
 * @author freva
 */
public class StoredDouble implements Supplier<OptionalDouble> {

    private static final Logger logger = Logger.getLogger(StoredDouble.class.getName());

    private final UnixPath path;

    public StoredDouble(Path path) {
        this.path = new UnixPath(path);
    }

    @Override
    public OptionalDouble get() {
        return path.readUtf8FileIfExists().stream().mapToDouble(Double::parseDouble).findAny();
    }

    public void write(TaskContext taskContext, double value) {
        path.writeUtf8File(Double.toString(value));
        taskContext.log(logger, "Stored new double in %s: %f", path, value);
    }

    public void clear() {
        path.deleteIfExists();
    }

    public Optional<Instant> getLastModifiedTime() {
        return path.getAttributesIfExists().map(FileAttributes::lastModifiedTime);
    }

}
