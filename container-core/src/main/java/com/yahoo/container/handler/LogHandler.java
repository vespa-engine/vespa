// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.google.inject.Inject;
import com.yahoo.container.core.LogHandlerConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;

import java.io.OutputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

public class LogHandler extends ThreadedHttpRequestHandler {

    private final LogReader logReader;

    @Inject
    public LogHandler(Executor executor, LogHandlerConfig config) {
        this(executor, new LogReader(config.logDirectory(), config.logPattern()));
    }

    LogHandler(Executor executor, LogReader logReader) {
        super(executor);
        this.logReader = logReader;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {

        Instant earliestLogThreshold = Optional.ofNullable(request.getProperty("from"))
                .map(Long::valueOf).map(Instant::ofEpochMilli).orElse(Instant.MIN);
        Instant latestLogThreshold = Optional.ofNullable(request.getProperty("to"))
                .map(Long::valueOf).map(Instant::ofEpochMilli).orElseGet(Instant::now);

        return new HttpResponse(200) {
            {
                headers().add("Content-Encoding", "gzip");
            }
            @Override
            public void render(OutputStream outputStream) {
                logReader.writeLogs(outputStream, earliestLogThreshold, latestLogThreshold);
            }
        };
    }
}
