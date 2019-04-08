// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class LogHandlerTest {

    @Test
    public void handleCorrectlyParsesQueryParameters() throws IOException {
        MockLogReader mockLogReader = new MockLogReader();
        LogHandler logHandler = new LogHandler(mock(Executor.class), mockLogReader);

        {
            String uri = "http://myhost.com:1111/logs?from=1000&to=2000";
            HttpResponse response = logHandler.handle(HttpRequest.createTestRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.GET));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            response.render(bos);
            String expectedResponse = "newer log";
            assertEquals(expectedResponse, bos.toString());
        }

        {
            String uri = "http://myhost.com:1111/logs?from=0&to=1000";
            HttpResponse response = logHandler.handle(HttpRequest.createTestRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.GET));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            response.render(bos);
            String expectedResponse = "older log";
            assertEquals(expectedResponse, bos.toString());
        }

    }

    class MockLogReader extends LogReader {
        MockLogReader() {
            super("", "");
        }

        @Override
        protected void writeLogs(OutputStream outputStream, Instant earliestLogThreshold, Instant latestLogThreshold)  {
            try {
                if (latestLogThreshold.isAfter(Instant.ofEpochMilli(1000))) {
                    outputStream.write("newer log".getBytes());
                } else {
                    outputStream.write("older log".getBytes());
                }
            } catch (Exception e) {}
        }
    }
}
