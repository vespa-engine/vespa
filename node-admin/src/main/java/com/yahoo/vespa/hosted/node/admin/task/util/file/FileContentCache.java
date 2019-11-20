// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.time.Instant;
import java.util.Optional;

/**
 * Class to avoid repeated reads of file content when the file seldom changes.
 *
 * @author hakonhall
 */
class FileContentCache {
    private final UnixPath path;

    private Optional<byte[]> value = Optional.empty();
    private Optional<Instant> modifiedTime = Optional.empty();

    FileContentCache(UnixPath path) {
        this.path = path;
    }

    byte[] get(Instant lastModifiedTime) {
        if (modifiedTime.isEmpty() || lastModifiedTime.isAfter(modifiedTime.get())) {
            value = Optional.of(path.readBytes());
            modifiedTime = Optional.of(lastModifiedTime);
        }

        return value.get();
    }

    void updateWith(byte[] content, Instant modifiedTime) {
        this.value = Optional.of(content);
        this.modifiedTime = Optional.of(modifiedTime);
    }
}
