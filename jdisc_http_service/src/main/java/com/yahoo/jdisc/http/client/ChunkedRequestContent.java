// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.Body;
import com.ning.http.client.BodyGenerator;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.http.core.HeaderFieldsUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
class ChunkedRequestContent implements BodyGenerator, ContentChannel {

    private static final byte[] LAST_CHUNK = "0\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRLF_BYTES = "\r\n".getBytes(StandardCharsets.UTF_8);
    private final AtomicReference<ChunkedRequestBody> body = new AtomicReference<>(new ChunkedRequestBody(this));
    private final AtomicBoolean writerClosed = new AtomicBoolean(false);
    private final Queue<Entry> writeQueue = new ConcurrentLinkedQueue<>();
    private final Queue<ByteBuffer> readQueue = new LinkedList<>();
    private final Request request;
    private boolean readerClosed = false;

    public ChunkedRequestContent(Request request) {
        this.request = request;
    }

    @Override
    public Body createBody() throws IOException {
        // this is called by Netty, and presumably has to be thread-safe since Netty assigns thread by connection --
        // retries are necessarily done using new connections
        Body body = this.body.getAndSet(null);
        if (body == null) {
            throw new UnsupportedOperationException("ChunkedRequestContent does not support retries.");
        }
        return body;
    }

    @Override
    public void write(ByteBuffer buf, CompletionHandler handler) {
        // this can be called by any JDisc thread, and needs to be thread-safe
        Objects.requireNonNull(buf, "buf");
        if (writerClosed.get()) {
            throw new IllegalStateException("ChunkedRequestContent is closed.");
        }
        writeQueue.add(new Entry(buf, handler));
    }

    @Override
    public void close(CompletionHandler handler) {
        // this can be called by any JDisc thread, and needs to be thread-safe
        if (writerClosed.getAndSet(true)) {
            throw new IllegalStateException("ChunkedRequestContent already closed.");
        }
        writeQueue.add(new Entry(null, handler));
    }

    public ByteBuffer nextChunk() {
        // this method is only called by the ChunkedRequestBody, which in turns is only called by the thread assigned to
        // the underlying Netty connection -- it does not need to be thread-safe
        if (!readQueue.isEmpty()) {
            ByteBuffer buf = readQueue.poll();
            if (buf == null) {
                readerClosed = true;
            }
            return buf;
        }
        if (writeQueue.isEmpty()) {
            return null;
        }
        Entry entry = writeQueue.poll();
        try {
            entry.handler.completed();
        } catch (Exception e) {
            // TODO: fail and close write queue
            // TODO: rethrow e to make ning abort request
        }
        if (entry.buf != null) {
            readQueue.add(ByteBuffer.wrap(Integer.toHexString(entry.buf.remaining()).getBytes(StandardCharsets.UTF_8)));
            readQueue.add(ByteBuffer.wrap(CRLF_BYTES));
            readQueue.add(entry.buf);
            readQueue.add(ByteBuffer.wrap(CRLF_BYTES));
        } else {
            readQueue.add(ByteBuffer.wrap(LAST_CHUNK));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HeaderFieldsUtil.copyTrailers(request, out);
            byte[] buf = out.toByteArray();
            if (buf.length > 0) {
                readQueue.add(ByteBuffer.wrap(buf));
            }
            readQueue.add(ByteBuffer.wrap(CRLF_BYTES));
            readQueue.add(null);
        }
        return readQueue.poll();
    }

    public boolean isEndOfInput() {
        // only called by the assigned Netty thread, does not need to be thread-safe
        return readerClosed;
    }

    private static class Entry {

        final ByteBuffer buf;
        final CompletionHandler handler;

        Entry(ByteBuffer buf, CompletionHandler handler) {
            this.buf = buf;
            this.handler = handler;
        }
    }
}
