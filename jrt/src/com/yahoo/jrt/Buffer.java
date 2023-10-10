// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;


class Buffer {
    private ByteBuffer buf;
    private int        readPos;
    private int        writePos;
    private boolean    readMode;

    private void setReadMode() {
        if (readMode) {
            buf.limit(writePos);
            return;
        }
        writePos = buf.position();
        buf.position(readPos);
        buf.limit(writePos);
        readMode = true;
    }

    private void setWriteMode() {
        if (!readMode) {
            buf.limit(buf.capacity());
            return;
        }
        readPos = buf.position();
        buf.limit(buf.capacity());
        if (readPos == writePos) {
            readPos = 0;
            writePos = 0;
        }
        buf.position(writePos);
        readMode = false;
    }

    private void ensureFree(int minFree) {
        // assumes setWriteMode called just before
        if (buf.remaining() >= minFree) {
            return;
        }
        writePos = buf.position();
        int used = writePos - readPos;
        int free = buf.remaining() + readPos;
        if (free >= minFree && free >= used) {
            buf.position(readPos);
            buf.limit(writePos);
            buf.compact();
            readPos = 0;
        } else {
            int size = buf.capacity() * 2;
            if (buf.capacity() + free < minFree) {
                size = buf.capacity() + minFree;
            }
            ByteBuffer tmp = ByteBuffer.allocate(size);
            tmp.order(buf.order());
            buf.position(readPos);
            buf.limit(writePos);
            tmp.put(buf);
            buf = tmp;
            readPos = 0;
        }
    }

    public Buffer(int size) {
        buf = ByteBuffer.allocate(size);
        readPos = 0;
        writePos = 0;
        readMode = false;
    }

    public boolean shrink(int size) {
        int rpos = readMode? buf.position() : readPos;
        int wpos = readMode? writePos : buf.position();
        int used = wpos - rpos;
        if (used > size || buf.capacity() <= size) {
            return false;
        }
        ByteBuffer tmp = ByteBuffer.allocate(size);
        tmp.order(buf.order());
        buf.position(rpos);
        buf.limit(wpos);
        tmp.put(buf);
        buf = tmp;
        readPos = 0;
        writePos = used;
        buf.position(readMode? readPos : writePos);
        buf.limit(readMode? writePos : buf.capacity());
        return true;
    }

    public int bytes() {
        return (readMode)
            ? (writePos - buf.position())
            : (buf.position() - readPos);
    }

    public ByteBuffer getReadable() {
        setReadMode();
        return buf;
    }

    public ByteBuffer getWritable(int minFree) {
        setWriteMode();
        ensureFree(minFree);
        return buf;
    }
}
