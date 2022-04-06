// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Base class for handler tests
 *
 * @author hmusum
 */
public class HandlerTest {

    public static void assertHttpStatusCodeErrorCodeAndMessage(
            HttpResponse response,
            int statusCode,
            HttpErrorResponse.ErrorCode errorCode,
            String contentType,
            String message) throws IOException {
        assertNotNull(response);
        String renderedString = SessionHandlerTest.getRenderedString(response);
        if (renderedString == null) {
            renderedString = "assert failed";
        }
        assertEquals(renderedString, statusCode, response.getStatus());
        if (errorCode != null) {
            assertTrue(renderedString.contains(errorCode.name()));
        }
        if (contentType != null) {
            assertEquals(renderedString, contentType, response.getContentType());
        }
        assertTrue("\n" + renderedString + "\n should contain \n" + message, renderedString.contains(message));
    }

    public static void assertHttpStatusCodeErrorCodeAndMessage(HttpResponse response, int statusCode, HttpErrorResponse.ErrorCode errorCode, String message) throws IOException {
        assertHttpStatusCodeErrorCodeAndMessage(response, statusCode, errorCode, null, message);
    }

    public static void assertHttpStatusCodeAndMessage(HttpResponse response, int statusCode, String message) throws IOException {
        assertHttpStatusCodeErrorCodeAndMessage(response, statusCode, null, message);
    }

    public static void assertHttpStatusCodeAndMessage(HttpResponse response, int statusCode, String contentType, String message) throws IOException {
        assertHttpStatusCodeErrorCodeAndMessage(response, statusCode, null, contentType, message);
    }

}
