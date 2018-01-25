package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import net.jpountz.xxhash.XXHashFactory;

import java.nio.ByteBuffer;

public class FileReferenceDataBlob extends FileReferenceData {
    private final byte[] content;
    private final long xxhash;

    public FileReferenceDataBlob(FileReference fileReference, String filename, Type type, byte[] content) {
        this(fileReference, filename, type, content, XXHashFactory.fastestInstance().hash64().hash(ByteBuffer.wrap(content), 0));
    }

    public FileReferenceDataBlob(FileReference fileReference, String filename, Type type, byte[] content, long xxhash) {
        super(fileReference, filename, type);
        this.content = content;
        this.xxhash = xxhash;
    }

    public static FileReferenceData empty(FileReference fileReference, String filename) {
        return new FileReferenceDataBlob(fileReference, filename, FileReferenceData.Type.file, new byte[0], 0);
    }

    public ByteBuffer content() {
        return ByteBuffer.wrap(content);
    }
    @Override
    public int nextContent(ByteBuffer bb) {
        bb.put(content);
        return content.length;
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
