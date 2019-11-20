// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
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
    private final FileAttributesCache attributesCache;

    public FileSync(Path path) {
        this.path = new UnixPath(path);
        this.contentCache = new FileContentCache(this.path);
        this.attributesCache = new FileAttributesCache(this.path);
    }

    public boolean convergeTo(TaskContext taskContext, PartialFileData partialFileData) {
        return convergeTo(taskContext, partialFileData, false);
    }

    /**
     * CPU, I/O, and memory usage is optimized for repeated calls with the same arguments.
     *
     * @param atomicWrite Whether to write updates to a temporary file in the same directory, and atomically move it
     *                    to path. Ensures the file cannot be read while in the middle of writing it.
     * @return true if the system was modified: content was written, or owner was set, etc.
     *         system is only modified if necessary (different).
     */
    public boolean convergeTo(TaskContext taskContext, PartialFileData partialFileData, boolean atomicWrite) {
        boolean modifiedSystem = false;

        if (partialFileData.getContent().isPresent())
            modifiedSystem |= convergeTo(taskContext, partialFileData.getContent().get(), atomicWrite);

        AttributeSync attributeSync = new AttributeSync(path.toPath()).with(partialFileData);
        modifiedSystem |= attributeSync.converge(taskContext, this.attributesCache);

        return modifiedSystem;
    }

    /**
     * CPU, I/O, and memory usage is optimized for repeated calls with the same argument.
     *
     * @param atomicWrite Whether to write updates to a temporary file in the same directory, and atomically move it
     *                    to path. Ensures the file cannot be read while in the middle of writing it.
     * @return true if the content was written. Only modified if necessary (different).
     */
    public boolean convergeTo(TaskContext taskContext, byte[] content, boolean atomicWrite) {
        Optional<Instant> lastModifiedTime = attributesCache.forceGet().map(FileAttributes::lastModifiedTime);

        if (lastModifiedTime.isEmpty()) {
            taskContext.recordSystemModification(logger, "Creating file " + path);
            path.createParents();
            writeBytes(content, atomicWrite);
            contentCache.updateWith(content, attributesCache.forceGet().orElseThrow().lastModifiedTime());
            return true;
        }

        if (Arrays.equals(content, contentCache.get(attributesCache.getOrThrow().lastModifiedTime()))) {
            return false;
        } else {
            taskContext.recordSystemModification(logger, "Patching file " + path);
            writeBytes(content, atomicWrite);
            contentCache.updateWith(content, attributesCache.forceGet().orElseThrow().lastModifiedTime());
            return true;
        }
    }

    private void writeBytes(byte[] content, boolean atomic) {
        if (atomic) {
            String tmpPath = path.toPath().toString() + ".FileSyncTmp";
            new UnixPath(path.toPath().getFileSystem().getPath(tmpPath))
                    .writeBytes(content)
                    .atomicMove(path.toPath());
        } else {
            path.writeBytes(content);
        }
    }
}
