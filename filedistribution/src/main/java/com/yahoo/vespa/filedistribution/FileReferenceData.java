// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;

import java.nio.ByteBuffer;


/**
 * Utility class for a file reference with data and metadata
 *
 * @author hmusum
 */
public abstract class FileReferenceData {

    public enum Type {file, compressed}

    private final FileReference fileReference;
    private final String filename;
    private final Type type;

    public FileReferenceData(FileReference fileReference, String filename, Type type) {
        this.fileReference = fileReference;
        this.filename = filename;
        this.type = type;
    }

    public FileReference fileReference() {
        return fileReference;
    }

    public String filename() {
        return filename;
    }

    public Type type() {
        return type;
    }

    public byte [] content() {
        ByteBuffer bb = ByteBuffer.allocate((int)size());
        for (byte [] part = nextContent(0); part != null && part.length > 0; part = nextContent(0)) {
            bb.put(part);
        }
        return bb.array();
    }
    /**
     * Will provide the next part of the content.
     *
     * @param desiredSize of the part
     * @return The next part of the content. Empty when done.
     */
    public abstract byte[] nextContent(int desiredSize);

    /**
     * Only guaranteed to be valid after all content has been consumed.
     * @return xx64hash of content
     */
    public abstract long xxhash();

    /**
     * The size of the content in bytes
     *
     * @return number of bytes
     */
    public abstract long size();
}
