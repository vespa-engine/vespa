// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 */
public class HttpHandlerTest {
    @Test
    public void testResponse() throws IOException {
        final String message = "failed";
        HttpHandler httpHandler = new HttpTestHandler(new InvalidApplicationException(message));
        HttpResponse response = httpHandler.handle(HttpRequest.createTestRequest("foo", com.yahoo.jdisc.http.HttpRequest.Method.GET));
        assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        Slime data = new Slime();
        new JsonDecoder().decode(data, baos.toByteArray());
        assertThat(data.get().field("error-code").asString(), is(HttpErrorResponse.errorCodes.INVALID_APPLICATION_PACKAGE.name()));
        assertThat(data.get().field("message").asString(), is(message));
    }

    @Test
    public void testTimeoutParameter() {
        assertEquals(1500, HttpTestHandler.getRequestTimeout(
                HttpRequest.createTestRequest("foo",
                                              com.yahoo.jdisc.http.HttpRequest.Method.GET,
                                              null,
                                              Collections.singletonMap("timeout", "1.5")), Duration.ofSeconds(5)).toMillis());
    }

    private static class HttpTestHandler extends HttpHandler {
        private RuntimeException exception;
        public HttpTestHandler(RuntimeException exception) {
            super(HttpHandler.testOnlyContext());
            this.exception = exception;
        }

        @Override
        public HttpResponse handleGET(HttpRequest request) {
            throw exception;
        }
    }
}
