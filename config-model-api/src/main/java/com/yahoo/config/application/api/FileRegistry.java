// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import java.util.List;
import java.util.Set;

import com.yahoo.config.FileReference;

/**
 * @author tonytv
 */
public interface FileRegistry {

    FileReference addFile(String relativePath);
    FileReference addUri(String uri);

    /**
     * Returns the name of the host which is the source of the files
     */
    String fileSourceHost();

    List<Entry> export();

    class Entry {
        public final String relativePath;
        public final FileReference reference;

        public Entry(String relativePath, FileReference reference) {
            this.relativePath = relativePath;
            this.reference = reference;
        }
    }

}
