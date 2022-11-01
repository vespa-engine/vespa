// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import java.io.File;

/**
 * Source specifying config from one local file
 *
 * @author Vegard Havdal
 */
public class FileSource implements ConfigSource {

    private final File file;
    private long generation;
    private long timestamp;

    public FileSource(File file) {
        if ( ! file.isFile()) throw new IllegalArgumentException("Not an ordinary file: " + file);
        this.file = file;
        this.timestamp = file.lastModified();
        this.generation = 1;
    }

    public File getFile() {
        return file;
    }

    public long generation() {
        return timestamp != (timestamp = file.lastModified()) ? ++generation : generation;
    }

}
