// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import com.yahoo.text.Utf8;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Utf8ByteWriter extends AbstractByteWriter {

    private ByteBuffer myBuf;
    public Utf8ByteWriter(int initialBuffer) {
        super(Utf8.getNewEncoder());
        myBuf = ByteBuffer.allocate(initialBuffer);
    }

    @Override
    public void send(ByteBuffer src) throws IOException {
        if (myBuf.remaining() < src.remaining()) {
            ByteBuffer newBuf = ByteBuffer.allocate(Integer.highestOneBit(myBuf.position()+src.remaining()) << 1);
            myBuf.flip();
            newBuf.put(myBuf);
            myBuf = newBuf;
        }
        myBuf.put(src);
    }

    @Override
    public void flush() throws IOException {
        buffer.flush();
    }

    @Override
    public void close() throws IOException {
        buffer.flush();
        myBuf.flip();
    }

    /**
     * Return a buffer ready for read. Must only be called after writer has been closed.
     *
     * @return A flipped ByteBuffer
     */
    public ByteBuffer getBuf() {
        if (myBuf.position() != 0) {
            throw new IllegalStateException("Call close() befor getBuf(), pos=" + myBuf.position() + ", limit=" + myBuf.limit());
        }
        return myBuf;
    }

}
