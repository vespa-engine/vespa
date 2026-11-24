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
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * A buffered stream wrapping a ContentChannel.
 *
 * <p>Each write to the channel has a fixed cost, so writes are collected in buffers. After a flush,
 * the first buffer is 8 KiB, which holds a short response. If it fills up, the response is long, and
 * the next buffers are 64 KiB until the next flush, so a long response is sent in few, large writes.
 * Every write, however large, is copied into these buffers, so a buffer is only sent part full at flush().
 * Held data is sent at flush(), or as soon as the held arrays, plus a new buffer being made,
 * would take more than 256 KiB: the first time at 200 KiB (an 8 KiB and three 64 KiB buffers),
 * then every 256 KiB.
 * Holding data does not save writes, as each held buffer is still one write. When the channel is a
 * response's content channel, its first write sends the response status and headers, so holding
 * delays that until flush() or until about 200 KiB is written. This delay is needed: a processing
 * response takes its status and headers from its data when that first write is sent, and some of
 * that data may only be rendered after the first few buffers.</p>
 *
 * @author Steinar Knutsen
 */
public class ContentChannelOutputStream extends OutputStream implements WritableByteTransmitter {

    private static final Logger log = Logger.getLogger(ContentChannelOutputStream.class.getName());

    /** The size of the first buffer after a flush. */
    private static final int SMALL_BUFFER_SIZE = 8 * 1024;
    /** The size of the buffers after a buffer is filled. */
    private static final int LARGE_BUFFER_SIZE = 64 * 1024;
    /**
     * Held buffers are sent once their arrays, plus a new buffer being made, would take more than this many bytes.
     * This limits only the data this stream holds, not the data already sent and waiting in the channel.
     */
    private static final int MAX_HELD_BYTES = 256 * 1024;

    private final ContentChannel endpoint;
    private final List<ByteBuffer> held = new ArrayList<>(); // buffers waiting to be sent, in order
    private long heldBytes = 0; // the total capacity of the held buffers' arrays
    private ByteBuffer current = null; // the buffer being filled, made at the first write
    private int nextBufferSize = SMALL_BUFFER_SIZE; // 8 KiB after a flush, 64 KiB once a buffer has filled
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
        if (current == null || ! current.hasRemaining()) startNewBuffer();
        current.put((byte) b);
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
            holdCurrentBuffer();
            sendHeld();
        } finally {
            nextBufferSize = SMALL_BUFFER_SIZE;
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
        copyIntoBuffers(b, off, len);
    }

    /**
     * Buffered write the contents of the array to this stream,
     * <i>copying</i> the contents of the given array to this stream.
     * It is in other words safe to recycle the array {@code b}.
     */
    @Override
    public void write(byte[] b) throws IOException {
        copyIntoBuffers(b, 0, b.length);
    }

    /**
     * Writes like {@link #write(byte[], int, int)}: the data is copied, so the array may be reused.
     * A bad range or a null array is an IOException.
     */
    public void nonCopyingWrite(byte[] b, int off, int len) throws IOException {
        try {
            Objects.checkFromIndexSize(off, len, b.length);
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
        }
        copyIntoBuffers(b, off, len);
    }

    /**
     * Writes like {@link #write(byte[])}: the data is copied, so the array may be reused.
     * A null array is an IOException.
     */
    public void nonCopyingWrite(byte[] b) throws IOException {
        if (b == null) throw new IOException("The array to write is null");
        copyIntoBuffers(b, 0, b.length);
    }

    /**
     * Copies the data into the current buffer, and into new buffers as each one fills up.
     * Every write and nonCopyingWrite method ends here, and none calls another, so a subclass
     * that overrides one of them and calls another does not loop.
     */
    private void copyIntoBuffers(byte[] b, int off, int len) throws IOException {
        bufferedData += len;
        while (len > 0) {
            if (current == null || ! current.hasRemaining()) startNewBuffer();
            int n = Math.min(len, current.remaining());
            current.put(b, off, n);
            off += n;
            len -= n;
        }
    }

    /** Starts a new buffer. If it would take the held arrays over MAX_HELD_BYTES, the held buffers are sent first. */
    private void startNewBuffer() throws IOException {
        holdCurrentBuffer();
        if (heldBytes + nextBufferSize > MAX_HELD_BYTES) sendHeld();
        current = ByteBuffer.allocate(nextBufferSize);
    }

    /**
     * Moves the current buffer, if it has data, to the held buffers. A full buffer means the response
     * is long, so the next buffers are 64 KiB. This never goes over MAX_HELD_BYTES, as that was checked
     * when the buffer was made.
     */
    private void holdCurrentBuffer() {
        if (current == null || current.position() == 0) return;
        if ( ! current.hasRemaining()) nextBufferSize = LARGE_BUFFER_SIZE;
        current.flip();
        held.add(current);
        heldBytes += current.capacity();
        current = null; // the buffer is handed over, so never write to it again
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
