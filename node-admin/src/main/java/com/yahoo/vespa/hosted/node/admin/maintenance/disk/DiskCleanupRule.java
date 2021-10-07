// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.disk;

import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;

import java.util.Collection;

/**
 * @author freva
 */
public interface DiskCleanupRule {

    Collection<PrioritizedFileAttributes> prioritize();

    enum Priority {
        LOWEST, LOW, MEDIUM, HIGH, HIGHEST
    }

    class PrioritizedFileAttributes {
        private final FileFinder.FileAttributes fileAttributes;
        private final Priority priority;

        public PrioritizedFileAttributes(FileFinder.FileAttributes fileAttributes, Priority priority) {
            this.fileAttributes = fileAttributes;
            this.priority = priority;
        }

        public Priority priority() { return priority; }
        public FileFinder.FileAttributes fileAttributes() { return fileAttributes; }
    }
}
