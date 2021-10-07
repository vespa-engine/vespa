// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.io.InterruptedIOException;
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
    private static final long MB = 1024 * 1024;

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
            public long maxPendingBytes() { return MB; }
            @Override
            public void render(OutputStream output, ContentChannel networkChannel, CompletionHandler handler) {
                try (output) {
                    logReader.writeLogs(output, from, to, hostname);
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



}
