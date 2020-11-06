// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;

import java.nio.ByteBuffer;

public class EmptyFileReferenceData extends FileReferenceData {

    private final byte[] content;
    private final long xxhash;
    private int contentRead = 0;

    private EmptyFileReferenceData(FileReference fileReference, String filename, Type type, byte[] content, long xxhash) {
        super(fileReference, filename, type);
        this.content = content;
        this.xxhash = xxhash;
    }

    public static FileReferenceData empty(FileReference fileReference, String filename) {
        return new EmptyFileReferenceData(fileReference, filename, FileReferenceData.Type.file, new byte[0], 0);
    }

    public ByteBuffer content() {
        return ByteBuffer.wrap(content);
    }

    @Override
    public int nextContent(ByteBuffer bb) {
        if (contentRead >= content.length) {
            return -1;
        } else {
            int left = content.length - contentRead;
            int size = Math.min(bb.remaining(), left);
            bb.put(content, contentRead, size);
            contentRead += size;
            return size;
        }
    }

    @Override
    public long xxhash() {
        return xxhash;
    }

    @Override
    public long size() {
        return content.length;
    }

    @Override
    public void close() {
        // no-op
    }
}
