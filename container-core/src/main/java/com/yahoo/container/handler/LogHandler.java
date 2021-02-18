// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.google.inject.Inject;
import com.yahoo.container.core.LogHandlerConfig;
import com.yahoo.container.jdisc.AsyncHttpResponse;
import com.yahoo.container.jdisc.ContentChannelOutputStream;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class LogHandler extends ThreadedHttpRequestHandler {

    private final LogReader logReader;
    private static final long MB = 1024*1024;

    @Inject
    public LogHandler(Executor executor, LogHandlerConfig config) {
        this(executor, new LogReader(config.logDirectory(), config.logPattern()));
    }

    LogHandler(Executor executor, LogReader logReader) {
        super(executor);
        this.logReader = logReader;
    }

    @Override
    public AsyncHttpResponse handle(HttpRequest request) {
        Instant from = Optional.ofNullable(request.getProperty("from"))
                               .map(Long::valueOf).map(Instant::ofEpochMilli).orElse(Instant.MIN);
        Instant to = Optional.ofNullable(request.getProperty("to"))
                             .map(Long::valueOf).map(Instant::ofEpochMilli).orElse(Instant.MAX);
        Optional<String> hostname = Optional.ofNullable(request.getProperty("hostname"));

        return new AsyncHttpResponse(200) {
            @Override
            public void render(OutputStream output, ContentChannel networkChannel, CompletionHandler handler) {
                try {
                    OutputStream blockingOutput = new MaxPendingContentChannelOutputStream(networkChannel, 1*MB);
                    logReader.writeLogs(blockingOutput, from, to, hostname);
                    blockingOutput.close();
                }
                catch (Throwable t) {
                    log.log(Level.WARNING, "Failed reading logs from " + from + " to " + to, t);
                }
                finally {
                    networkChannel.close(handler);
                }
            }
        };
    }


    private static class MaxPendingContentChannelOutputStream extends ContentChannelOutputStream {
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

        private class TrackCompletition implements CompletionHandler {
            private final long written;
            private final AtomicBoolean replied = new AtomicBoolean(false);
            TrackCompletition(long written) {
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
            } catch (InterruptedException ignored) {
                throw new IOException("Interrupted waiting for IO");
            }
            CompletionHandler pendingTracker = new TrackCompletition(src.remaining());
            try {
                send(src, pendingTracker);
            } catch (Throwable throwable) {
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
                throw new IOException("Interrupted waiting for IO");
            }
        }

    }

}
