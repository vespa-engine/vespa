// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
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

        boolean modifiedSystem = maybeUpdateContent(taskContext, partialFileData.getContent(), currentAttributes);

        AttributeSync attributeSync = new AttributeSync(path.toPath()).with(partialFileData);
        modifiedSystem |= attributeSync.converge(taskContext, currentAttributes);

        return modifiedSystem;
    }

    private boolean maybeUpdateContent(TaskContext taskContext,
                                       Optional<String> content,
                                       FileAttributesCache currentAttributes) {
        if (!content.isPresent()) {
            return false;
        }

        if (!currentAttributes.exists()) {
            taskContext.recordSystemModification(logger, "Creating file " + path);
            path.createParents();
            path.writeUtf8File(content.get());
            contentCache.updateWith(content.get(), currentAttributes.forceGet().lastModifiedTime());
            return true;
        }

        if (Objects.equals(content.get(), contentCache.get(currentAttributes.get().lastModifiedTime()))) {
            return false;
        } else {
            taskContext.recordSystemModification(logger, "Patching file " + path);
            path.writeUtf8File(content.get());
            contentCache.updateWith(content.get(), currentAttributes.forceGet().lastModifiedTime());
            return true;
        }
    }
}
