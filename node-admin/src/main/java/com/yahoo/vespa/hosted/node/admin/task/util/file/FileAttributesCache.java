// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.util.Optional;

// @ThreadUnsafe
public class FileAttributesCache {
    private final UnixPath path;

    private Optional<FileAttributes> attributes = Optional.empty();

    public FileAttributesCache(UnixPath path) {
        this.path =  path;
    }

    public FileAttributes get() {
        if (!attributes.isPresent()) {
            attributes = Optional.of(path.getAttributes());
        }

        return attributes.get();
    }

    public FileAttributes forceGet() {
        attributes = Optional.empty();
        return get();
    }

    public boolean exists() {
        if (attributes.isPresent()) {
            return true;
        }

        Optional<FileAttributes> attributes = path.getAttributesIfExists();
        if (attributes.isPresent()) {
            // Might as well update this.attributes
            this.attributes = attributes;
            return true;
        } else {
            return false;
        }
    }
}
