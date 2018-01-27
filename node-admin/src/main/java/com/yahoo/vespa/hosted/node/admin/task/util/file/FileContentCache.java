// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.time.Instant;
import java.util.Optional;

/**
 * Class to avoid repeated reads of file content when the file seldom changes.
 */
class FileContentCache {
    private final UnixPath path;

    private Optional<String> value = Optional.empty();
    private Optional<Instant> modifiedTime = Optional.empty();

    FileContentCache(UnixPath path) {
        this.path = path;
    }

    String get(Instant lastModifiedTime) {
        if (!value.isPresent() || lastModifiedTime.compareTo(modifiedTime.get()) > 0) {
            value = Optional.of(path.readUtf8File());
            modifiedTime = Optional.of(lastModifiedTime);
        }

        return value.get();
    }

    void updateWith(String content, Instant modifiedTime) {
        this.value = Optional.of(content);
        this.modifiedTime = Optional.of(modifiedTime);
    }
}
