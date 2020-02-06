// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import java.io.File;

/**
 * Source specifying config from one local file
 *
 * @author Vegard Havdal
 */
public class FileSource implements ConfigSource {

    private final File file;

    public FileSource(File file) {
        if ( ! file.isFile()) throw new IllegalArgumentException("Not an ordinary file: "+file);
        this.file = file;
    }

    public File getFile() {
        return file;
    }

}
