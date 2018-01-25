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

    public ByteBuffer content() {
        ByteBuffer bb = ByteBuffer.allocate((int)size());
        while (bb.remaining() > 0) {
            nextContent(bb);
        }
        return bb;
    }
    /**
     * Will provide the next part of the content.
     *
     * @param bb with some available space
     * @return Number of bytes transferred.
     */
    public abstract int nextContent(ByteBuffer bb);

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

    /**
     * Close underlying files
     *
     */
    public abstract void close();
}
