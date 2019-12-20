// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.http.LogRetriever;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author olaa
 */
public class MockLogRetriever extends LogRetriever {

    @Override
    public HttpResponse getLogs(String logServerHostname) {
        return new MockHttpResponse();
    }

    private static class MockHttpResponse extends HttpResponse {

        private MockHttpResponse() {
            super(200);
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write("log line".getBytes(StandardCharsets.UTF_8));
        }

    }

}