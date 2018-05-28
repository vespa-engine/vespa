// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.util;

import javax.annotation.concurrent.Immutable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Immutable info about a regular file.
 *
 * @author hakon
 */
@Immutable
public class FileInfo {
    private final Path path;
    private final boolean exists;
    private final Optional<Instant> lastModifiedTime;
    private final Optional<byte[]> content;

    static FileInfo forMissingFile(Path path) {
        return new FileInfo(path, false, Optional.empty(), Optional.empty());
    }

    static FileInfo forExistingFile(Path path, Instant lastModifiedTime, byte[] content) {
        return new FileInfo(path, true, Optional.of(lastModifiedTime), Optional.of(content));
    }

    Path path() {
        return path;
    }

    public boolean exists() {
        return exists;
    }

    Instant lastModifiedTime() {
        return lastModifiedTime.get();
    }

    public byte[] content() {
        return content.get();
    }

    /** See factory methods. */
    private FileInfo(Path path, boolean exists, Optional<Instant> lastModifiedTime, Optional<byte[]> content) {
        this.path = path;
        this.exists = exists;
        this.lastModifiedTime = lastModifiedTime;
        this.content = content;
    }
}
