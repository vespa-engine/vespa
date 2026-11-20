// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.io.WritableByteTransmitter;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import java.util.logging.Level;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * A buffered stream wrapping a ContentChannel.
 *
 * <p>Each write to the channel has a fixed cost, so writes are collected in buffers. After a flush,
 * the first buffer is 8 KiB, which holds a short response. If it fills up, the response is long, and
 * the next buffers are 64 KiB until the next flush, so a long response is sent in few, large writes.
 * A write of 64 KiB or more becomes a buffer of its own: {@link #write(byte[], int, int)} copies it
 * once, and {@link #nonCopyingWrite(byte[], int, int)} uses the caller's array. Held data is sent at
 * flush(), or as soon as the held arrays, plus any new buffer being made, would take more than 256 KiB.
 * Holding data does not save writes, as each held buffer is still one write. It delays the first
 * write, which sends the response status and headers, until flush() or until the response is long.</p>
 *
 * @author Steinar Knutsen
 */
public class ContentChannelOutputStream extends OutputStream implements WritableByteTransmitter {

    private static final Logger log = Logger.getLogger(ContentChannelOutputStream.class.getName());

    /** The size of the first buffer after a flush. */
    private static final int SMALL_BUFFER_SIZE = 8 * 1024;
    /** The size of the buffers after a buffer is filled, and the smallest write that is kept whole. */
    private static final int LARGE_BUFFER_SIZE = 64 * 1024;
    /**
     * Held buffers are sent once their arrays, plus a new buffer being made, would take more than this many bytes.
     * This limits only the data this stream holds, not the data already sent and waiting in the channel.
     */
    private static final int MAX_HELD_BYTES = 256 * 1024;

    private final ContentChannel endpoint;
    private final List<ByteBuffer> held = new ArrayList<>();
    private long heldBytes = 0;
    private byte[] current = null; // allocated when needed
    private int count = 0; // bytes used in current
    private boolean filledSinceFlush = false; // whether a buffer was filled since the last flush
    private long bufferedData = 0L; // bytes given to this stream by write and nonCopyingWrite
    private long byteBufferData = 0L; // bytes sent to the endpoint
    private boolean failed = false;
    private final Object failLock = new Object();

    public ContentChannelOutputStream(ContentChannel endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Buffered write of a single byte.
     */
    @Override
    public void write(int b) throws IOException {
        if (current == null || count == current.length) newBuffer();
        current[count++] = (byte) b;
        bufferedData++;
    }

    /**
     * Sends the buffered data to the ContentChannel. Does not close the ContentChannel.
     */
    @Override
    public void close() throws IOException {
        // the endpoint is closed in a finally{} block inside AbstractHttpRequestHandler
        // this class should be possible to close willynilly as it is exposed to plug-ins
        flush();
    }

    /**
     * Sends the buffered data to the ContentChannel. Does not close the ContentChannel.
     */
    @Override
    public void flush() throws IOException {
        try {
            holdCurrent();
            sendHeld();
        } finally {
            filledSinceFlush = false;
        }
    }

    /**
     * Buffered write of the contents of the array to this stream,
     * <i>copying</i> the contents of the given array to this stream.
     * It is in other words safe to recycle the array {@code b}.
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len >= LARGE_BUFFER_SIZE) {
            holdWhole(Arrays.copyOfRange(b, off, off + len), 0, len);
            return;
        }
        bufferedData += len;
        while (len > 0) {
            if (current == null || count == current.length) newBuffer();
            int n = Math.min(len, current.length - count);
            System.arraycopy(b, off, current, count, n);
            count += n;
            off += n;
            len -= n;
        }
    }

    /**
     * Buffered write the contents of the array to this stream,
     * <i>copying</i> the contents of the given array to this stream.
     * It is in other words safe to recycle the array {@code b}.
     */
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Buffered write of the contents of the array to this stream,
     * <i>transferring</i> ownership of that array to this stream. It is in
     * other words <i>not</i> safe to recycle the array {@code b}.
     * An array range under 64 KiB is copied into the buffers.
     */
    public void nonCopyingWrite(byte[] b, int off, int len) throws IOException {
        try { // callers of this method get an IOException for a bad range
            Objects.checkFromIndexSize(off, len, b.length);
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
        }
        if (len < LARGE_BUFFER_SIZE) write(b, off, len);
        else holdWhole(b, off, len);
    }

    /**
     * Buffered write the contents of the array to this stream,
     * <i>transferring</i> ownership of that array to this stream. It is in
     * other words <i>not</i> safe to recycle the array {@code b}.
     */
    public void nonCopyingWrite(byte[] b) throws IOException {
        if (b == null) throw new IOException("The array to write is null");
        nonCopyingWrite(b, 0, b.length);
    }

    /** Holds an array that this stream owns as one buffer, after the data written before it. */
    private void holdWhole(byte[] b, int off, int len) throws IOException {
        bufferedData += len;
        holdCurrent();
        hold(ByteBuffer.wrap(b, off, len));
        if (heldBytes > MAX_HELD_BYTES) sendHeld();
    }

    /**
     * Holds the current buffer, if it has data, then starts a new buffer: 64 KiB if a buffer was
     * filled since the last flush, otherwise 8 KiB. Sends the held buffers first if they and the
     * new buffer would take more than MAX_HELD_BYTES.
     */
    private void newBuffer() throws IOException {
        holdCurrent();
        int size = filledSinceFlush ? LARGE_BUFFER_SIZE : SMALL_BUFFER_SIZE;
        if (heldBytes + size > MAX_HELD_BYTES) sendHeld();
        current = new byte[size];
    }

    /**
     * Moves the data in the current buffer to the held buffers, and notes whether the buffer was full.
     * This never goes over MAX_HELD_BYTES, as that was checked when the buffer was made.
     */
    private void holdCurrent() {
        if (count == 0) return;
        if (count == current.length) filledSinceFlush = true;
        ByteBuffer data = ByteBuffer.wrap(current, 0, count);
        current = null; // the array is handed over, so never write to it again
        count = 0;
        hold(data);
    }

    private void hold(ByteBuffer data) {
        held.add(data);
        heldBytes += data.capacity(); // the whole array is kept, not only the data in it
    }

    /**
     * Sends the held buffers. If a send throws, the rest of the held buffers are dropped too,
     * so a later flush does not send them.
     */
    private void sendHeld() throws IOException {
        try {
            for (ByteBuffer data : held) send(data);
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
        } finally {
            held.clear();
            heldBytes = 0;
        }
    }

    /**
     * Write a ByteBuffer to the wrapped ContentChannel. Do invoke
     * {@link ContentChannelOutputStream#flush()} before send(ByteBuffer) to
     * avoid garbled output if the stream API has been accessed before using the
     * ByteBuffer based API. As with ContentChannel, this transfers ownership of
     * the ByteBuffer to this stream.
     */
    @Override
    public void send(ByteBuffer src) throws IOException {
        // Don't flush from here: this method is used by the buffering itself
        send(src, null);
    }

    protected void send(ByteBuffer src, CompletionHandler completionHandler) throws IOException {
        try {
            byteBufferData += src.remaining();
            endpoint.write(src, new LoggingCompletionHandler(completionHandler));
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
        }
    }

    /** Returns the number of bytes written to this stream */
    public long written() {
        return bufferedData + byteBufferData;
    }

    private class LoggingCompletionHandler implements CompletionHandler {

        private final CompletionHandler nested;

        LoggingCompletionHandler(CompletionHandler nested) {
            this.nested = nested;
        }

        @Override
        public void completed() {
            if (nested != null) {
                nested.completed();
            }
        }

        @Override
        public void failed(Throwable t) {
            Level logLevel;
            synchronized (failLock) {
                if (failed) {
                    logLevel = Level.FINEST;
                } else {
                    logLevel = Level.FINE;
                }
                failed = true;
            }
            if (log.isLoggable(logLevel)) {
                log.log(logLevel, "Got exception when writing to client: " + Exceptions.toMessageString(t));
            }
            if (nested != null) {
                nested.failed(t);
            }
        }
    }

}
