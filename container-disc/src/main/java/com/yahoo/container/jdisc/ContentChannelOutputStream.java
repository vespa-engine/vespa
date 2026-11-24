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
import java.util.Objects;
import java.util.logging.Logger;

/**
 * A buffered stream wrapping a ContentChannel.
 *
 * <p>Each write to the channel has a fixed cost, so writes are collected in buffers. After a flush,
 * the first buffer is 8 KiB, which holds a short response. If it fills up, the response is long, and
 * the next buffers are 64 KiB until the next flush, so a long response is sent in few, large writes.
 * Every write, however large, is copied into these buffers. A buffer is sent as soon as it is full,
 * or at flush() if it is not, so the stream holds at most one buffer.
 * When the channel is a response's content channel, its first write sends the response status
 * and headers: at flush(), or when the first 8 KiB buffer is full.</p>
 *
 * @author Steinar Knutsen
 */
public class ContentChannelOutputStream extends OutputStream implements WritableByteTransmitter {

    private static final Logger log = Logger.getLogger(ContentChannelOutputStream.class.getName());

    /** The size of the first buffer after a flush. */
    private static final int SMALL_BUFFER_SIZE = 8 * 1024;
    /** The size of the buffers after a buffer is filled. */
    private static final int LARGE_BUFFER_SIZE = 64 * 1024;

    private final ContentChannel endpoint;
    private ByteBuffer current = null; // the buffer being filled, made when there is data for it
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
        if (current == null) current = ByteBuffer.allocate(nextBufferSize);
        current.put((byte) b);
        bufferedData++;
        if ( ! current.hasRemaining()) sendCurrentBuffer();
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
            sendCurrentBuffer();
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
     * Copies the data into the current buffer, sending each buffer as soon as it is full.
     * Every write and nonCopyingWrite method ends here, and none calls another, so a subclass
     * that overrides one of them and calls another does not loop.
     */
    private void copyIntoBuffers(byte[] b, int off, int len) throws IOException {
        bufferedData += len;
        while (len > 0) {
            if (current == null) current = ByteBuffer.allocate(nextBufferSize);
            int n = Math.min(len, current.remaining());
            current.put(b, off, n);
            off += n;
            len -= n;
            if ( ! current.hasRemaining()) sendCurrentBuffer();
        }
    }

    /**
     * Sends the current buffer, if there is one. A full buffer means the response is long, so the next
     * buffers are 64 KiB. The buffer is let go before it is sent, so it is never written to again,
     * and never sent again if the send throws.
     */
    private void sendCurrentBuffer() throws IOException {
        if (current == null) return;
        if ( ! current.hasRemaining()) nextBufferSize = LARGE_BUFFER_SIZE;
        ByteBuffer data = current.flip();
        current = null;
        try {
            send(data);
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
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
