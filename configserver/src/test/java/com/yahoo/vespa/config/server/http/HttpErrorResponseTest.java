// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import static com.yahoo.jdisc.http.HttpResponse.Status.*;

/**
 * @author Ulf Lilleengen
 */
public class HttpErrorResponseTest {
    @Test
    public void testThatHttpErrorResponseIsRenderedAsJson() throws IOException {
        HttpErrorResponse response = HttpErrorResponse.badRequest("Error doing something");
        assertThat(response.getJdiscResponse().getStatus(), is(BAD_REQUEST));
        assertThat(SessionHandlerTest.getRenderedString(response), is("{\"error-code\":\"BAD_REQUEST\",\"message\":\"Error doing something\"}"));
    }

    @Test
    public void testThatHttpErrorResponseProvidesCorrectErrorMessage() throws IOException {
        HttpErrorResponse response = HttpErrorResponse.badRequest("Error doing something");
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST, HttpErrorResponse.ErrorCode.BAD_REQUEST, "Error doing something");
    }

    @Test
    public void testThatHttpErrorResponseHasJsonContentType() {
        HttpErrorResponse response = HttpErrorResponse.badRequest("Error doing something");
        assertThat(response.getContentType(), is("application/json"));
    }
}
