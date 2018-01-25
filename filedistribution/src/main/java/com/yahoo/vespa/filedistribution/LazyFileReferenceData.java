// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;

public class LazyFileReferenceData extends FileReferenceData {
    private final File file;
    private final ReadableByteChannel channel;
    private final StreamingXXHash64 hasher;
    public LazyFileReferenceData(FileReference fileReference, String filename, Type type, File file) throws IOException {
        super(fileReference, filename, type);
        this.file = file;
        channel = Files.newByteChannel(file.toPath());
        this.hasher = XXHashFactory.fastestInstance().newStreamingHash64(0);
    }

    @Override
    public int nextContent(ByteBuffer bb) {
        int read = 0;
        int pos = bb.position();
        try {
            read = channel.read(bb);
        } catch (IOException e) {
            return -1;
        }
        if (read > 0) {
            hasher.update(bb.array(), pos, read);
        }
        return read;
    }

    @Override
    public long xxhash() {
        return hasher.getValue();
    }

    @Override
    public long size() {
        try {
            return Files.size(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
