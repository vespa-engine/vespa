// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Makes file I/O mockable.
 *
 * @author hakon
 */
public class FilesApi {
    boolean exists(Path path) {
        return Files.exists(path);
    }

    Instant getLastModifiedTime(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant();
    }

    byte[] readAllBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }
}
