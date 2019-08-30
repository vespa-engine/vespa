// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author olaa
 */
public class FileWrapper {

    List<String> readAllLines(Path path) throws IOException {
        return Files.readAllLines(path);
    }

    Stream<Path> walkTree(Path path) throws IOException {
        return Files.walk(path);
    }

    Instant getLastModifiedTime(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant();
    }

    boolean isRegularFile(Path path) {
        return Files.isRegularFile(path);
    }
}
