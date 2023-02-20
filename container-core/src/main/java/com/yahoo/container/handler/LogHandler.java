// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.core.LogHandlerConfig;
import com.yahoo.container.jdisc.AsyncHttpResponse;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.RequestView;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.container.jdisc.utils.CapabilityRequiringRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.security.tls.Capability;

import java.io.OutputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

public class LogHandler extends ThreadedHttpRequestHandler implements CapabilityRequiringRequestHandler {

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

    @Override public Capability requiredCapability(RequestView __) { return Capability.LOGSERVER_API; }

    @Override
    public AsyncHttpResponse handle(HttpRequest request) {
        Instant from = Optional.ofNullable(request.getProperty("from"))
                               .map(Long::valueOf).map(Instant::ofEpochMilli).orElse(Instant.MIN);
        Instant to = Optional.ofNullable(request.getProperty("to"))
                             .map(Long::valueOf).map(Instant::ofEpochMilli).orElse(Instant.MAX);
        long maxLines = Optional.ofNullable(request.getProperty("maxLines"))
                                .map(Long::valueOf).orElse(100_000L);
        Optional<String> hostname = Optional.ofNullable(request.getProperty("hostname"));

        return new AsyncHttpResponse(200) {
            @Override
            public long maxPendingBytes() { return MB; }
            @Override
            public void render(OutputStream output, ContentChannel networkChannel, CompletionHandler handler) {
                try (output) {
                    logReader.writeLogs(output, from, to, maxLines, hostname);
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
