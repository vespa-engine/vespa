// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.io.BufferChain;
import com.yahoo.io.WritableByteTransmitter;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.log.LogLevel;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A buffered stream wrapping a ContentChannel.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ContentChannelOutputStream extends OutputStream implements WritableByteTransmitter {

    private static final Logger log = Logger.getLogger(ContentChannelOutputStream.class.getName());
    private final BufferChain buffer;
    private final ContentChannel endpoint;
    private long byteBufferData = 0L;
    private boolean failed = false;
    private final Object failLock = new Object();

    public ContentChannelOutputStream(final ContentChannel endpoint) {
        this.endpoint = endpoint;
        buffer = new BufferChain(this);
    }

    /**
     * Buffered write of a single byte.
     */
    @Override
    public void write(final int b) throws IOException {
        try {
            buffer.append((byte) b);
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
        }
    }

    /**
     * Flush the internal buffers, does not touch the ContentChannel.
     */
    @Override
    public void close() throws IOException {
        // the endpoint is closed in a finally{} block inside AbstractHttpRequestHandler
        // this class should be possible to close willynilly as it is exposed to plug-ins
        try {
            buffer.flush();
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
        }
    }

    /**
     * Flush the internal buffers, does not touch the ContentChannel.
     */
    @Override
    public void flush() throws IOException {
        try {
            buffer.flush();
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
        }
    }

    /**
     * Buffered write of the contents of the array to this stream,
     * <i>copying</i> the contents of the given array to this stream.
     * It is in other words safe to recycle the array {@code b}.
     */
    @Override
    public void write(final byte[] b, final int off, final int len)
            throws IOException {
        nonCopyingWrite(Arrays.copyOfRange(b, off, off + len));
    }

    /**
     * Buffered write the contents of the array to this stream,
     * <i>copying</i> the contents of the given array to this stream.
     * It is in other words safe to recycle the array {@code b}.
     */
    @Override
    public void write(final byte[] b) throws IOException {
            nonCopyingWrite(Arrays.copyOf(b, b.length));
    }

    /**
     * Buffered write of the contents of the array to this stream,
     * <i>transferring</i> ownership of that array to this stream. It is in
     * other words <i>not</i> safe to recycle the array {@code b}.
     */
    public void nonCopyingWrite(final byte[] b, final int off, final int len)
            throws IOException {
        try {
            buffer.append(b, off, len);
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
        }
    }

    /**
     * Buffered write the contents of the array to this stream,
     * <i>transferring</i> ownership of that array to this stream. It is in
     * other words <i>not</i> safe to recycle the array {@code b}.
     */
    public void nonCopyingWrite(final byte[] b) throws IOException {
        try {
            buffer.append(b);
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
    public void send(final ByteBuffer src) throws IOException {
        // Don't do a buffer.flush() from here, this method is used by the
        // buffer itself
        try {
            byteBufferData += (long) src.remaining();
            endpoint.write(src, new LoggingCompletionHandler());
        } catch (RuntimeException e) {
            throw new IOException(Exceptions.toMessageString(e), e);
        }
    }

    /**
     * Give the number of bytes written.
     *
     * @return the number of bytes written to this stream
     */
    public long written() {
        return buffer.appended() + byteBufferData;
    }

    class LoggingCompletionHandler implements CompletionHandler {
        @Override
        public void completed() {
        }

        @Override
        public void failed(Throwable t) {
            Level logLevel;
            synchronized (failLock) {
                if (failed) {
                    logLevel = LogLevel.SPAM;
                } else {
                    logLevel = LogLevel.DEBUG;
                }
                failed = true;
            }
            if (log.isLoggable(logLevel)) {
                log.log(logLevel, "Got exception when writing to client: " + Exceptions.toMessageString(t));
            }
        }
    }
}
