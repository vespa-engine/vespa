// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A file registry for testing, and, it seems, doubling as a null registry in some code paths.
 *
 * @author Tony Vaagenes
 * @author hmusum
 */
public class MockFileRegistry implements FileRegistry {
    private final List<Entry> entries = new ArrayList<>();

    public FileReference addFile(String relativePath) {
        FileReference fileReference = new FileReference(relativePath);
        entries.add(new Entry(relativePath, fileReference));
        return fileReference;
    }

    public List<Entry> export() { return entries; }

    @Override
    public FileReference addUri(String uri) {
        FileReference fileReference = new FileReference(uri);
        entries.add(new Entry(uri, fileReference));
        return fileReference;
    }

    @Override
    public FileReference addBlob(String name, ByteBuffer blob) {
        String relativePath = "./" + name;
        FileReference fileReference = new FileReference(relativePath);
        entries.add(new Entry(relativePath, fileReference));
        return fileReference;
    }

}
