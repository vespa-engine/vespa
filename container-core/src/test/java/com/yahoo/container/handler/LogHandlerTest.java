// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;
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
            String expectedResponse = "{\"logs\":{\"one\":\"newer_log\"}}";
            assertEquals(expectedResponse, bos.toString());
        }

        {
            String uri = "http://myhost.com:1111/logs?from=0&to=1000";
            HttpResponse response = logHandler.handle(HttpRequest.createTestRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.GET));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            response.render(bos);
            String expectedResponse = "{\"logs\":{\"two\":\"older_log\"}}";
            assertEquals(expectedResponse, bos.toString());
        }

    }

    class MockLogReader extends LogReader {
        @Override
        protected JSONObject readLogs(String logDirectory, long earliestLogThreshold, long latestLogThreshold) throws JSONException {
            if(latestLogThreshold > 1000) {
                return new JSONObject("{\"one\":\"newer_log\"}");
            } else {
                return new JSONObject("{\"two\":\"older_log\"}");
            }
        }
    }
}
