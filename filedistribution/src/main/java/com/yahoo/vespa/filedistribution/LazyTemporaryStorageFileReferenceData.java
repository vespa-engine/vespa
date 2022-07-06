// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * File reference data stored in a temporary file that will be deleted when {@link #close()} is called.
 */
public class LazyTemporaryStorageFileReferenceData extends LazyFileReferenceData {

    public LazyTemporaryStorageFileReferenceData(FileReference fileReference, String filename, Type type, File file, CompressionType compressionType) throws IOException {
        super(fileReference, filename, type, file, compressionType);
    }

    public void close() {
        try {
            super.close();
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
