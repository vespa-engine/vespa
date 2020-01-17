// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.http.TesterClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author hmusum
 */
public class MockTesterClient extends TesterClient {

    @Override
    public HttpResponse getStatus(String testerHostname, int port) {
        return new MockStatusResponse();
    }

    @Override
    public HttpResponse getLog(String testerHostname, int port, Long after) {
        return new MockLogResponse();
    }

    private static class MockStatusResponse extends HttpResponse {

        private MockStatusResponse() {
            super(200);
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write("OK".getBytes(StandardCharsets.UTF_8));
        }

    }

    private static class MockLogResponse extends HttpResponse {

        private MockLogResponse() {
            super(200);
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write("log".getBytes(StandardCharsets.UTF_8));
        }

    }

}