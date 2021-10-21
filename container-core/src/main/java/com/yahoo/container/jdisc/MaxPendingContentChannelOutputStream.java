// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author baldersheim
 */
public class MaxPendingContentChannelOutputStream extends ContentChannelOutputStream {

    private final long maxPending;
    private final AtomicLong sent = new AtomicLong(0);
    private final AtomicLong acked = new AtomicLong(0);

    public MaxPendingContentChannelOutputStream(ContentChannel endpoint, long maxPending) {
        super(endpoint);
        this.maxPending = maxPending;
    }

    private long pendingBytes() {
        return sent.get() - acked.get();
    }

    private class TrackCompletion implements CompletionHandler {

        private final long written;
        private final AtomicBoolean replied = new AtomicBoolean(false);

        TrackCompletion(long written) {
            this.written = written;
            sent.addAndGet(written);
        }

        @Override
        public void completed() {
            if (!replied.getAndSet(true)) {
                acked.addAndGet(written);
            }
        }

        @Override
        public void failed(Throwable t) {
            if (!replied.getAndSet(true)) {
                acked.addAndGet(written);
            }
        }

    }

    @Override
    public void send(ByteBuffer src) throws IOException {
        try {
            stallWhilePendingAbove(maxPending);
        }
        catch (InterruptedException ignored) {
            throw new InterruptedIOException("Interrupted waiting for IO");
        }
        CompletionHandler pendingTracker = new TrackCompletion(src.remaining());
        try {
            send(src, pendingTracker);
        }
        catch (Throwable throwable) {
            pendingTracker.failed(throwable);
            throw throwable;
        }
    }

    private void stallWhilePendingAbove(long pending) throws InterruptedException {
        while (pendingBytes() > pending) {
            Thread.sleep(1);
        }
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        try {
            stallWhilePendingAbove(0);
        }
        catch (InterruptedException e) {
            throw new InterruptedIOException("Interrupted waiting for IO");
        }
    }

}
