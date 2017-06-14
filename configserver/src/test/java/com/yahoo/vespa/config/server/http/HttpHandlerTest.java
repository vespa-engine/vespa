// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Response;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 * @since 5.34
 */
public class HttpHandlerTest {
    @Test
    public void testResponse() throws IOException {
        final String message = "failed";
        HttpHandler httpHandler = new HttpTestHandler(Executors.newSingleThreadExecutor(), AccessLog.voidAccessLog(), new InvalidApplicationException(message));
        HttpResponse response = httpHandler.handle(HttpRequest.createTestRequest("foo", com.yahoo.jdisc.http.HttpRequest.Method.GET));
        assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        Slime data = new Slime();
        new JsonDecoder().decode(data, baos.toByteArray());
        assertThat(data.get().field("error-code").asString(), is(HttpErrorResponse.errorCodes.INVALID_APPLICATION_PACKAGE.name()));
        assertThat(data.get().field("message").asString(), is(message));
    }

    private static class HttpTestHandler extends HttpHandler {
        private RuntimeException exception;
        public HttpTestHandler(Executor executor, AccessLog accessLog, RuntimeException exception) {
            super(executor, accessLog);
            this.exception = exception;
        }

        @Override
        public HttpResponse handleGET(HttpRequest request) {
            throw exception;
        }
    }
}
