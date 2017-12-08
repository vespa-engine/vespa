// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import net.jpountz.xxhash.XXHashFactory;

import java.nio.ByteBuffer;

/**
 * Utility class for a file reference with data and metadata
 *
 * @author hmusum
 */
public class FileReferenceData {

    public enum Type {file, compressed}

    private final FileReference fileReference;
    private final String filename;
    private final Type type;
    private final byte[] content;
    private final long xxhash;

    public FileReferenceData(FileReference fileReference, String filename, Type type, byte[] content) {
        this(fileReference, filename, type, content, XXHashFactory.fastestInstance().hash64().hash(ByteBuffer.wrap(content), 0));
    }

    public FileReferenceData(FileReference fileReference, String filename, Type type, byte[] content, long xxhash) {
        this.fileReference = fileReference;
        this.filename = filename;
        this.type = type;
        this.content = content;
        this.xxhash = xxhash;
    }

    public static FileReferenceData empty(FileReference fileReference, String filename) {
        return new FileReferenceData(fileReference, filename, FileReferenceData.Type.file, new byte[0], 0);
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

    public byte[] content() {
        return content;
    }

    public long xxhash() {
        return xxhash;
    }
}
