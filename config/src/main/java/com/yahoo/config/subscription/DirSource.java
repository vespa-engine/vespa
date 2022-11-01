// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import java.io.File;

/**
 * Source specifying config from a local directory
 * @author Vegard Havdal
 */
public class DirSource implements ConfigSource {
    private final File dir;

    public DirSource(File dir) {
        if ( ! dir.isDirectory()) throw new IllegalArgumentException("Not a directory: " + dir);
        this.dir = dir;
    }

    public FileSource getFile(String name) {
        return new FileSource(new File(dir, name));
    }

}
