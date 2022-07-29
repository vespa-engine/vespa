// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.util;

import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.Cookie;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.yahoo.jdisc.http.filter.util.FilterUtils.sendMessageResponse;
import static com.yahoo.jdisc.http.filter.util.FilterUtils.sendRedirectResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author valerijf
 */
public class FilterUtilsTest {

    private static final List<Cookie> cookies = List.of(new Cookie("my-cookie", "value1"), new Cookie("second-cookie", "value2"));

    @Test
    void redirect_test() {
        RequestHandlerTestDriver.MockResponseHandler responseHandler = new RequestHandlerTestDriver.MockResponseHandler();

        String location = "http://domain.tld/path?query";
        sendRedirectResponse(responseHandler, cookies, location);
        assertEquals(302, responseHandler.getStatus());
        assertHeaders(responseHandler.getResponse(), Map.entry("Location", location), Map.entry("Set-Cookie", "my-cookie=value1"), Map.entry("Set-Cookie", "second-cookie=value2"));
    }

    @Test
    void message_response() {
        RequestHandlerTestDriver.MockResponseHandler responseHandler = new RequestHandlerTestDriver.MockResponseHandler();

        sendMessageResponse(responseHandler, List.of(), 404, "Not found");
        assertEquals(404, responseHandler.getStatus());
        assertHeaders(responseHandler.getResponse());
        assertEquals("{\n  \"message\" : \"Not found\"\n}", responseHandler.readAll());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static void assertHeaders(Response response, Map.Entry<String, String>... expectedHeaders) {
        assertEquals(List.of(expectedHeaders), response.headers().entries());
    }
}