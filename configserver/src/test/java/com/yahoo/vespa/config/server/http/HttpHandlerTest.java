// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class HttpHandlerTest {

    @Test
    public void testResponse() throws IOException {
        final String message = "failed";
        HttpHandler httpHandler = new HttpTestHandler(new InvalidApplicationException(message));
        HttpResponse response = httpHandler.handle(HttpRequest.createTestRequest("foo", com.yahoo.jdisc.http.HttpRequest.Method.GET));
        assertEquals(Response.Status.BAD_REQUEST, response.getStatus());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        Slime data = SlimeUtils.jsonToSlime(baos.toByteArray());
        assertEquals(HttpErrorResponse.ErrorCode.INVALID_APPLICATION_PACKAGE.name(), data.get().field("error-code").asString());
        assertEquals(message, data.get().field("message").asString());
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
        private final RuntimeException exception;
        HttpTestHandler(RuntimeException exception) {
            super(HttpHandler.testContext());
            this.exception = exception;
        }

        @Override
        public HttpResponse handleGET(HttpRequest request) {
            throw exception;
        }
    }
}
