// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import ai.vespa.http.HttpURL;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.http.LogRetriever;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * @author olaa
 */
public class MockLogRetriever extends LogRetriever {

    private final int statuCode;
    private final String logLine;

    public MockLogRetriever() { this(200, "log line"); }

    public MockLogRetriever(int statusCode, String logLine) {
        this.statuCode = statusCode;
        this.logLine = logLine;
    }

    @Override
    public HttpResponse getLogs(HttpURL logServerUri, Optional<Instant> deployTime) {
        return new HttpResponse(statuCode) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                outputStream.write(logLine.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

}
