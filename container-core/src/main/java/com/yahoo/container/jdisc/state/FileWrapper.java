// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * @author olaa
 */
public class FileWrapper {

    long getFileAgeInSeconds(Path path) throws IOException {
        Instant lastModifiedTime = Files.getLastModifiedTime(path).toInstant();
        return Instant.now().getEpochSecond() - lastModifiedTime.getEpochSecond();
    }

    Stream<Path> walkTree(Path path) throws IOException {
        return Files.walk(path);
    }

    boolean isRegularFile(Path path) {
        return Files.isRegularFile(path);
    }

}
