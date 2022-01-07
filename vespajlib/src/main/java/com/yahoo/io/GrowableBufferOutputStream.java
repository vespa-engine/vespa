// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.nio.channels.WritableByteChannel;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Stack;
import java.util.LinkedList;
import java.util.Iterator;
import java.nio.ByteBuffer;

/**
 * @author Bjørn Borud
 */
public class GrowableBufferOutputStream extends OutputStream {

    private ByteBuffer lastBuffer;
    private final ByteBuffer directBuffer;
    private final LinkedList<ByteBuffer> bufferList = new LinkedList<>();
    private final Stack<ByteBuffer> recycledBuffers = new Stack<>();

    private final int bufferSize;
    private final int maxBuffers;

    public GrowableBufferOutputStream(int bufferSize, int maxBuffers) {
        this.bufferSize = bufferSize;
        this.maxBuffers = maxBuffers;
        lastBuffer = ByteBuffer.allocate(bufferSize);
        directBuffer = ByteBuffer.allocateDirect(bufferSize);
    }

    @Override
    public void write(byte[] cbuf, int off, int len) throws IOException {
        if (lastBuffer.remaining() >= len) {
            lastBuffer.put(cbuf, off, len);
            return;
        }

        int residue = len;

        while (residue > 0) {
            int newOffset = len - residue;
            int toWrite = Math.min(lastBuffer.remaining(), residue);

            lastBuffer.put(cbuf, newOffset, toWrite);
            residue -= toWrite;
            if (residue != 0) {
                extend();
            }
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b,0,b.length);
    }

    @Override
    public String toString() {
        return "GrowableBufferOutputStream, writable size " + writableSize()
                + " bytes, " + numWritableBuffers() + " buffers, last buffer"
                + " position " + lastBuffer.position() + ", last buffer limit "
                + lastBuffer.limit();
    }

    public void write(int b) {
        if (lastBuffer.remaining() == 0) {
            extend();
        }
        lastBuffer.put((byte) b);
    }

    @Override
    public void flush() {
        // if the last buffer is untouched we do not need to do anything;  if
        // it has been touched we call extend(), which enqueues the buffer
        // and allocates or recycles a buffer for us
        if (lastBuffer.position() > 0) {
            extend();
        }
    }

    @Override
    public void close() {
        flush();
    }

    public int channelWrite(WritableByteChannel channel) throws IOException {
        ByteBuffer buffer;
        int totalWritten = 0;

        while (!bufferList.isEmpty()) {
            buffer = bufferList.getFirst();
            int written = 0;

            synchronized (directBuffer) {
                directBuffer.clear();
                directBuffer.put(buffer);
                directBuffer.flip();
                written = channel.write(directBuffer);
                int left = directBuffer.remaining();

                if (left > 0) {
                    int oldpos = buffer.position();

                    buffer.position(oldpos - left);
                }
                totalWritten += written;
            }

            // if we've completed writing this buffer we can dispose of it
            if (buffer.remaining() == 0) {
                bufferList.removeFirst();
                recycleBuffer(buffer);
            }

            // if we didn't write any bytes we terminate
            if (written == 0) {
                break;
            }
        }

        return totalWritten;
    }

    public int numWritableBuffers() {
        return bufferList.size();
    }

    public void clear() {
        flush();
        bufferList.clear();
    }

    public void clearCache() {
        recycledBuffers.clear();
    }

    public void clearAll() {
        clear();
        clearCache();
    }

    public int writableSize() {
        Iterator<ByteBuffer> it = bufferList.iterator();
        int size = 0;

        while (it.hasNext()) {
            size += (it.next()).remaining();
        }

        return size;
    }

    public ByteBuffer[] getWritableBuffers() {
        flush();
        ByteBuffer[] result = new ByteBuffer[numWritableBuffers()];
        return bufferList.toArray(result);
    }

    private void extend() {
        enqueueBuffer(lastBuffer);

        if (recycledBuffers.empty()) {
            lastBuffer = ByteBuffer.allocate(bufferSize);
        } else {
            lastBuffer = recycledBuffers.pop();
            lastBuffer.clear();
        }
    }

    private void enqueueBuffer(ByteBuffer buffer) {
        buffer.flip();
        bufferList.addLast(buffer);
    }

    private void recycleBuffer(ByteBuffer buffer) {
        if (recycledBuffers.size() >= maxBuffers) {
            return;
        }
        recycledBuffers.push(buffer);
    }

}
