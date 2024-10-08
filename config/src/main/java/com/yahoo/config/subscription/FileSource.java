// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Source specifying config from one local file
 *
 * @author Vegard Havdal
 * @author jonmv
 */
public class FileSource implements ConfigSource {

    private final File file;

    public FileSource(File file) {
        this.file = validateFile(file);
    }

    public long getLastModified() {
        return file.lastModified();
    }

    public List<String> getContent() throws IOException {
        return Files.readAllLines(file.toPath());
    }

    private static File validateFile(File file) {
        if ( ! file.isFile()) throw new IllegalArgumentException("Not a file: " + file);
        return file;
    }
    public void validateFile() {
        validateFile(file);
    }

}
