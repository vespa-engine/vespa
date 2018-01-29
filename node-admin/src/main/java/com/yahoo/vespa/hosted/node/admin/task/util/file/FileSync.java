// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Class to minimize resource usage with repetitive and mostly identical, idempotent, and
 * mutating file operations, e.g. setting file content, setting owner, etc.
 *
 * Only changes to the file is logged.
 *
 * @author hakohall
 */
// @ThreadUnsafe
public class FileSync {
    private static final Logger logger = Logger.getLogger(FileSync.class.getName());

    private final UnixPath path;
    private final FileContentCache contentCache;

    public FileSync(Path path) {
        this.path = new UnixPath(path);
        this.contentCache = new FileContentCache(this.path);
    }

    /**
     * CPU, I/O, and memory usage is optimized for repeated calls with the same arguments.
     * @return true if the system was modified: content was written, or owner was set, etc.
     *         system is only modified if necessary (different).
     */
    public boolean convergeTo(TaskContext taskContext, PartialFileData partialFileData) {
        FileAttributesCache currentAttributes = new FileAttributesCache(path);

        boolean modifiedSystem = false;

        modifiedSystem |= maybeUpdateContent(taskContext, partialFileData.getContent(), currentAttributes);

        modifiedSystem |= convergeAttribute(
                taskContext,
                "owner",
                partialFileData.getOwner(),
                () -> currentAttributes.get().owner(),
                path::setOwner);

        modifiedSystem |= convergeAttribute(
                taskContext,
                "group",
                partialFileData.getGroup(),
                () -> currentAttributes.get().group(),
                path::setGroup);

        modifiedSystem |= convergeAttribute(
                taskContext,
                "permissions",
                partialFileData.getPermissions(),
                () -> currentAttributes.get().permissions(),
                path::setPermissions);

        return modifiedSystem;
    }

    private boolean convergeAttribute(TaskContext taskContext,
                                      String attributeName,
                                      Optional<String> wantedValue,
                                      Supplier<String> currentValueSupplier,
                                      Consumer<String> valueSetter) {
        if (!wantedValue.isPresent()) {
            return false;
        }

        String currentValue = currentValueSupplier.get();
        if (Objects.equals(wantedValue.get(), currentValue)) {
            return false;
        } else {
            String actionDescription = String.format("Changing %s of %s from %s to %s",
                    attributeName,
                    path,
                    currentValue,
                    wantedValue.get());
            taskContext.logSystemModification(logger, actionDescription);
            valueSetter.accept(wantedValue.get());
            return true;
        }
    }

    private boolean maybeUpdateContent(TaskContext taskContext,
                                       Optional<String> content,
                                       FileAttributesCache currentAttributes) {
        if (!content.isPresent()) {
            return false;
        }

        if (!currentAttributes.exists()) {
            taskContext.logSystemModification(logger, "Creating file " + path);
            path.createParents();
            path.writeUtf8File(content.get());
            contentCache.updateWith(content.get(), currentAttributes.forceGet().lastModifiedTime());
            return true;
        }

        if (Objects.equals(content.get(), contentCache.get(currentAttributes.get().lastModifiedTime()))) {
            return false;
        } else {
            taskContext.logSystemModification(logger, "Patching file " + path);
            path.writeUtf8File(content.get());
            contentCache.updateWith(content.get(), currentAttributes.forceGet().lastModifiedTime());
            return true;
        }
    }
}
