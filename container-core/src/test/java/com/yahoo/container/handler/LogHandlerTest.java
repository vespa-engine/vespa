// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.container.jdisc.AsyncHttpResponse;
import com.yahoo.container.jdisc.ContentChannelOutputStream;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class LogHandlerTest {

    @Test
    void handleCorrectlyParsesQueryParameters() throws IOException {
        MockLogReader mockLogReader = new MockLogReader();
        LogHandler logHandler = new LogHandler(mock(Executor.class), mockLogReader);

        {
            String uri = "http://myhost.com:1111/logs?from=1000&to=2000";
            AsyncHttpResponse response = logHandler.handle(HttpRequest.createTestRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.GET));
            ReadableContentChannel out = new ReadableContentChannel();
            new Thread(() -> Exceptions.uncheck(() -> response.render(new ContentChannelOutputStream(out), out, null))).start();
            String expectedResponse = "newer log";
            assertEquals(expectedResponse, new String(out.toStream().readAllBytes(), UTF_8));
        }

        {
            String uri = "http://myhost.com:1111/logs?from=0&to=1000";
            AsyncHttpResponse response = logHandler.handle(HttpRequest.createTestRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.GET));
            ReadableContentChannel out = new ReadableContentChannel();
            new Thread(() -> Exceptions.uncheck(() -> response.render(new ContentChannelOutputStream(out), out, null))).start();
            String expectedResponse = "older log";
            assertEquals(expectedResponse, new String(out.toStream().readAllBytes(), UTF_8));
        }

    }

    class MockLogReader extends LogReader {
        MockLogReader() {
            super("", "");
        }

        @Override
        protected void writeLogs(OutputStream out, Instant from, Instant to, long maxLines, Optional<String> hostname)  {
            try {
                if (to.isAfter(Instant.ofEpochMilli(1000))) {
                    out.write("newer log".getBytes());
                } else {
                    out.write("older log".getBytes());
                }
            } catch (Exception e) {}
        }
    }
}
