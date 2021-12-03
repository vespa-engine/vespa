// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.servlet.ServletResponse;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 * @since 5.27
 */
public class ServletFilterResponseTest {

    private final String headerName = "header1";
    private final String headerValue = "h1";

    private DiscFilterResponse filterResponse;
    private ServletResponse parentResponse;

    @Before
    public void init() throws Exception {
        filterResponse = new ServletFilterResponse(newServletResponse());
        parentResponse = ((ServletFilterResponse)filterResponse).getServletResponse();

    }

    private ServletResponse newServletResponse() throws Exception {
        MockServletResponse parent = new MockServletResponse();
        parent.addHeader(headerName, headerValue);
        return new ServletResponse(parent);
    }


    @Test
    public void headers_can_be_set() throws Exception {
        String name = "myHeader";
        String value = name + "Value";
        filterResponse.setHeaders(name, value);

        assertEquals(filterResponse.getHeader(name), value);
        assertEquals(parentResponse.getHeader(name), value);
    }

    @Test
    public void headers_can_be_added() throws Exception {
        String newValue = "h2";
        filterResponse.addHeader(headerName, newValue);

        // The DiscFilterResponse has no getHeaders()
        assertEquals(filterResponse.getHeader(headerName), newValue);

        assertEquals(parentResponse.getHeaders(headerName), Arrays.asList(headerValue, newValue));
    }

    @Test
    public void headers_can_be_removed() throws Exception {
        filterResponse.removeHeaders(headerName);

        assertEquals(filterResponse.getHeader(headerName), null);
        assertEquals(parentResponse.getHeader(headerName), null);
    }

    @Test
    public void set_cookie_overwrites_old_values() {
        Cookie to_be_removed = new Cookie("to-be-removed", "");
        Cookie to_keep = new Cookie("to-keep", "");
        filterResponse.setCookie(to_be_removed.getName(), to_be_removed.getValue());
        filterResponse.setCookie(to_keep.getName(), to_keep.getValue());

        assertEquals(filterResponse.getCookies(), Arrays.asList(to_keep));
        assertEquals(parentResponse.getHeaders(HttpHeaders.Names.SET_COOKIE), Arrays.asList(to_keep.toString()));
    }


    private static class MockServletResponse extends org.eclipse.jetty.server.Response {
        private MockServletResponse() {
            super(null, null);
        }
    }

}
