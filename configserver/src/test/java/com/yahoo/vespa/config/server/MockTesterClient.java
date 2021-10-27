// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                outputStream.write("OK".getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    @Override
    public HttpResponse getLog(String testerHostname, int port, Long after) {
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                outputStream.write("log".getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    @Override
    public HttpResponse startTests(String testerHostname, int port, String suite, byte[] config) {
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) { }
        };
    }

    @Override
    public HttpResponse isTesterReady(String testerHostname, int port) {
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                outputStream.write("{ \"message\": \"OK\" } ".getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    @Override
    public HttpResponse getReport(String testerHostname, int port) {
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                outputStream.write("report".getBytes(StandardCharsets.UTF_8));
            }
        };
    }

}
