// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.yahoo.jdisc.http.HttpResponse.Status.*;
import static com.yahoo.jdisc.http.HttpRequest.Method.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 * @since 5.1.14
 */
public class SessionExampleHandlerTest {
    private static final String URI = "http://localhost:19071/session/example";

    @Test
    public void basicPut() throws IOException {
        final SessionExampleHandler handler = new SessionExampleHandler(Executors.newCachedThreadPool());
        final HttpRequest request = HttpRequest.createTestRequest(URI, PUT);
        HttpResponse response = handler.handle(request);
        assertThat(response.getStatus(), is(OK));
        assertThat(SessionHandlerTest.getRenderedString(response), is("{\"test\":\"PUT received\"}"));
    }

    @Test
    public void invalidMethod() {
        final SessionExampleHandler handler = new SessionExampleHandler(Executors.newCachedThreadPool());
        final HttpRequest request = HttpRequest.createTestRequest(URI, GET);
        HttpResponse response = handler.handle(request);
        assertThat(response.getStatus(), is(METHOD_NOT_ALLOWED));
    }


    /**
     * A handler that prepares a session given by an id in the request.
     *
     * @author hmusum
     * @since 5.1.14
     */
    public static class SessionExampleHandler extends ThreadedHttpRequestHandler {

        public SessionExampleHandler(Executor executor) {
            super(executor);
        }

        @Override
        public HttpResponse handle(HttpRequest request) {
            final com.yahoo.jdisc.http.HttpRequest.Method method = request.getMethod();
            switch (method) {
                case PUT:
                    return handlePUT(request);
                case GET:
                    return new SessionExampleResponse(METHOD_NOT_ALLOWED, "Method '" + method + "' is not supported");
                default:
                    return new SessionExampleResponse(INTERNAL_SERVER_ERROR);
            }
        }

        @SuppressWarnings({"UnusedDeclaration"})
        HttpResponse handlePUT(HttpRequest request) {
            return new SessionExampleResponse(OK, "PUT received");
        }

        private static class SessionExampleResponse extends HttpResponse {
            private final Slime slime = new Slime();
            private final Cursor root = slime.setObject();
            private final String message;


            private SessionExampleResponse(int status) {
                this(status, "");
                headers().put("Cache-Control","max-age=120");
            }

            private SessionExampleResponse(int status, String message) {
                super(status);
                this.message = message;
            }

            @Override
            public void render(OutputStream outputStream) throws IOException {
                root.setString("test", message);
                new JsonFormat(true).encode(outputStream, slime);
            }
        }
    }
}
