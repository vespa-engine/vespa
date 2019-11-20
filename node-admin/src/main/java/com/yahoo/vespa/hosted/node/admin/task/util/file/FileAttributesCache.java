// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.util.Optional;

// @ThreadUnsafe
public class FileAttributesCache {
    private final UnixPath path;

    private Optional<FileAttributes> attributes = Optional.empty();

    public FileAttributesCache(UnixPath path) {
        this.path = path;
    }

    public Optional<FileAttributes> get() {
        if (attributes.isEmpty()) {
            attributes = path.getAttributesIfExists();
        }

        return attributes;
    }

    public FileAttributes getOrThrow() {
        return get().orElseThrow();
    }

    public Optional<FileAttributes> forceGet() {
        attributes = Optional.empty();
        return get();
    }
}
