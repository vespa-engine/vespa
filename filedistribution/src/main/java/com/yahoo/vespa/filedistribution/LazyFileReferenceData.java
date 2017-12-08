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
    public byte[] nextContent(int desiredSize) {
        ByteBuffer bb = ByteBuffer.allocate(Math.min(desiredSize, 0x100000));
        try {
            channel.read(bb);
        } catch (IOException e) {
            return null;
        }
        byte [] retval = bb.array();
        if (bb.position() != bb.array().length) {
            retval = new byte [bb.position()];
            bb.get(retval);
        }
        hasher.update(retval, 0, retval.length);
        return retval;
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
}
