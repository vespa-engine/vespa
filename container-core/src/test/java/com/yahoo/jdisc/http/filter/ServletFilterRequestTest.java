// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.server.jetty.JettyMockRequestBuilder;
import com.yahoo.jdisc.http.servlet.ServletRequest;
import org.eclipse.jetty.server.Request;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.yahoo.jdisc.http.HttpRequest.Version;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test the parts of the DiscFilterRequest API that are implemented
 * by ServletFilterRequest, both directly and indirectly via
 * {@link com.yahoo.jdisc.http.servlet.ServletRequest}.
 *
 * @author gjoranv
 */
public class ServletFilterRequestTest {

    private final String host = "host1";
    private final int port = 8080;
    private final String path = "/path1";
    private final String paramName = "param1";
    private final String paramValue = "p1";
    private final String listParamName = "listParam";
    private final String[] listParamValue = new String[]{"1", "2"};
    private final String headerName = "header1";
    private final String headerValue = "h1";
    private final String attributeName = "attribute1";
    private final String attributeValue = "a1";

    private URI uri;
    private DiscFilterRequest filterRequest;
    private ServletRequest parentRequest;

    @Before
    public void init() throws Exception {
        uri = new URI("http", null, host, port, path, paramName + "=" + paramValue, null);

        filterRequest = new ServletFilterRequest(newServletRequest());
        parentRequest = ((ServletFilterRequest)filterRequest).getServletRequest();
    }

    private ServletRequest newServletRequest() {
        Request parent = JettyMockRequestBuilder.newBuilder()
                .remote("1.2.3.4", host, port)
                .header(headerName, List.of(headerValue))
                .parameter(paramName, List.of(paramValue))
                .parameter(listParamName, List.of(listParamValue))
                .attribute(attributeName, attributeValue)
                .build();
        return new ServletRequest(parent, uri);
    }

    @Test
    public void parent_properties_are_propagated_to_disc_filter_request() throws Exception {
        assertEquals(filterRequest.getVersion(), Version.HTTP_1_1);
        assertEquals(filterRequest.getMethod(), "GET");
        assertEquals(filterRequest.getUri(), uri);
        assertEquals(filterRequest.getRemoteHost(), host);
        assertEquals(filterRequest.getRemotePort(), port);
        assertEquals(filterRequest.getRequestURI(), path); // getRequestUri return only the path by design

        assertEquals(filterRequest.getParameter(paramName), paramValue);
        assertEquals(filterRequest.getParameterMap().get(paramName),
                     Collections.singletonList(paramValue));
        assertEquals(filterRequest.getParameterValuesAsList(listParamName), Arrays.asList(listParamValue));

        assertEquals(filterRequest.getHeader(headerName), headerValue);
        assertEquals(filterRequest.getAttribute(attributeName), attributeValue);
    }

    @Test
    public void untreatedHeaders_is_populated_from_the_parent_request() {
        assertEquals(filterRequest.getUntreatedHeaders().getFirst(headerName), headerValue);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void uri_can_be_set() throws Exception {
        URI newUri = new URI("http", null, host, port + 1, path, paramName + "=" + paramValue, null);
        filterRequest.setUri(newUri);

        assertEquals(filterRequest.getUri(), newUri);
        assertEquals(parentRequest.getUri(), newUri);
    }

    @Test
    public void attributes_can_be_set() throws Exception {
        String name = "newAttribute";
        String value = name + "Value";
        filterRequest.setAttribute(name, value);

        assertEquals(filterRequest.getAttribute(name), value);
        assertEquals(parentRequest.getAttribute(name), value);
    }

    @Test
    public void attributes_can_be_removed() {
        filterRequest.removeAttribute(attributeName);

        assertEquals(filterRequest.getAttribute(attributeName), null);
        assertEquals(parentRequest.getAttribute(attributeName), null);
    }

    @Test
    public void headers_can_be_set() throws Exception {
        String name = "myHeader";
        String value = name + "Value";
        filterRequest.setHeaders(name, value);

        assertEquals(filterRequest.getHeader(name), value);
        assertEquals(parentRequest.getHeader(name), value);
    }

    @Test
    public void headers_can_be_removed() throws Exception {
        filterRequest.removeHeaders(headerName);

        assertEquals(filterRequest.getHeader(headerName), null);
        assertEquals(parentRequest.getHeader(headerName), null);
    }

    @Test
    public void headers_can_be_added() {
        String value = "h2";
        filterRequest.addHeader(headerName, value);

        List<String> expected = Arrays.asList(headerValue, value);
        assertEquals(filterRequest.getHeadersAsList(headerName), expected);
        assertEquals(Collections.list(parentRequest.getHeaders(headerName)), expected);
    }

    @Test
    public void cookies_can_be_added_and_removed() {
        Cookie cookie = new Cookie("name", "value");
        filterRequest.addCookie(JDiscCookieWrapper.wrap(cookie));

        assertEquals(filterRequest.getCookies(), Collections.singletonList(cookie));
        assertEquals(parentRequest.getCookies().length, 1);

        javax.servlet.http.Cookie servletCookie = parentRequest.getCookies()[0];
        assertEquals(servletCookie.getName(), cookie.getName());
        assertEquals(servletCookie.getValue(), cookie.getValue());

        filterRequest.clearCookies();
        assertTrue(filterRequest.getCookies().isEmpty());
        assertEquals(parentRequest.getCookies().length, 0);
    }

    @Test
    public void character_encoding_can_be_set() throws Exception {
        // ContentType must be non-null before setting character encoding
        filterRequest.setHeaders(HttpHeaders.Names.CONTENT_TYPE, "");

        String encoding = "myEncoding";
        filterRequest.setCharacterEncoding(encoding);

        assertTrue(filterRequest.getCharacterEncoding().contains(encoding));
        assertTrue(parentRequest.getCharacterEncoding().contains(encoding));
    }

}
