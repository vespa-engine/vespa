// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Version;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DiscFilterRequestTest {

	private static HttpRequest newRequest(URI uri, HttpRequest.Method method, HttpRequest.Version version) {
		InetSocketAddress address = new InetSocketAddress("example.yahoo.com", 69);
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        HttpRequest request = HttpRequest.newServerRequest(driver, uri, method, version, address);
        request.release();
        assertTrue(driver.close());
        return request;
    }

    @Test
    void testRequestConstruction() {
        URI uri = URI.create("http://localhost:8080/test?param1=abc");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        httpReq.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html;charset=UTF-8");
        httpReq.headers().add("X-Custom-Header", "custom_header");
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(new Cookie("XYZ", "value"));
        cookies.add(new Cookie("ABC", "value"));
        httpReq.encodeCookieHeader(cookies);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        assertEquals(request.getHeader("X-Custom-Header"), "custom_header");
        assertEquals(request.getHeader(HttpHeaders.Names.CONTENT_TYPE), "text/html;charset=UTF-8");

        List<Cookie> c = request.getCookies();
        assertNotNull(c);
        assertEquals(c.size(), 2);

        assertEquals(request.getParameter("param1"), "abc");
        assertNull(request.getParameter("param2"));
        assertEquals(request.getVersion(), Version.HTTP_1_1);
        assertEquals(request.getProtocol(), Version.HTTP_1_1.name());
        assertNull(request.getRequestedSessionId());
    }

    @Test
    void testRequestConstruction2() {
        URI uri = URI.create("http://localhost:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        httpReq.headers().add("some-header", "some-value");
        DiscFilterRequest request = new DiscFilterRequest(httpReq);

        request.addHeader("some-header", "some-value");
        String value = request.getUntreatedHeaders().get("some-header").get(0);
        assertEquals(value, "some-value");
    }

    @Test
    void testRequestAttributes() {
        URI uri = URI.create("http://localhost:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        request.setAttribute("some_attr", "some_value");

        assertTrue(request.containsAttribute("some_attr"));

        assertEquals(request.getAttribute("some_attr"), "some_value");

    }

    @Test
    void testGetAttributeNames() {
        URI uri = URI.create("http://localhost:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        request.setAttribute("some_attr_1", "some_value1");
        request.setAttribute("some_attr_2", "some_value2");

        Enumeration<String> e = request.getAttributeNames();
        List<String> attrList = Collections.list(e);
        assertEquals(2, attrList.size());
        assertTrue(attrList.contains("some_attr_1"));
        assertTrue(attrList.contains("some_attr_2"));

    }

    @Test
    void testRemoveAttribute() {
        URI uri = URI.create("http://localhost:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        request.setAttribute("some_attr", "some_value");

        assertTrue(request.containsAttribute("some_attr"));

        request.removeAttribute("some_attr");

        assertFalse(request.containsAttribute("some_attr"));
    }

    @Test
    void testGetIntHeader() {
        URI uri = URI.create("http://localhost:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);

        assertEquals(-1, request.getIntHeader("int_header"));

        request.addHeader("int_header", String.valueOf(5));

        assertEquals(5, request.getIntHeader("int_header"));
    }

    @Test
    void testDateHeader() {
        URI uri = URI.create("http://localhost:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);


        assertEquals(-1, request.getDateHeader(HttpHeaders.Names.IF_MODIFIED_SINCE));

        request.addHeader(HttpHeaders.Names.IF_MODIFIED_SINCE, "Sat, 29 Oct 1994 19:43:31 GMT");

        assertEquals(783459811000L, request.getDateHeader(HttpHeaders.Names.IF_MODIFIED_SINCE));
    }

    @Test
    void testParameterAPIsAsList() {
        URI uri = URI.create("http://example.yahoo.com:8080/test?param1=abc&param2=xyz&param2=pqr");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        assertEquals(request.getParameter("param1"), "abc");

        List<String> values = request.getParameterValuesAsList("param2");
        assertEquals(values.get(0), "xyz");
        assertEquals(values.get(1), "pqr");

        List<String> paramNames = request.getParameterNamesAsList();
        assertEquals(paramNames.size(), 2);

    }

    @Test
    void testParameterAPI() {
        URI uri = URI.create("http://example.yahoo.com:8080/test?param1=abc&param2=xyz&param2=pqr");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        assertEquals(request.getParameter("param1"), "abc");

        Enumeration<String> values = request.getParameterValues("param2");
        List<String> valuesList = Collections.list(values);
        assertEquals(valuesList.get(0), "xyz");
        assertEquals(valuesList.get(1), "pqr");

        Enumeration<String> paramNames = request.getParameterNames();
        List<String> paramNamesList = Collections.list(paramNames);
        assertEquals(paramNamesList.size(), 2);
    }

    @Test
    void testGetHeaderNamesAsList() {
        URI uri = URI.create("http://localhost:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        httpReq.headers().add(HttpHeaders.Names.CONTENT_TYPE, "multipart/form-data");
        httpReq.headers().add("header_1", "value1");
        httpReq.headers().add("header_2", "value2");
        DiscFilterRequest request = new DiscFilterRequest(httpReq);

        assertNotNull(request.getHeaderNamesAsList());
        assertEquals(request.getHeaderNamesAsList().size(), 3);
    }

    @Test
    void testGetHeadersAsList() {
        URI uri = URI.create("http://localhost:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);

        assertNotNull(request.getHeaderNamesAsList());
        assertEquals(request.getHeaderNamesAsList().size(), 0);

        httpReq.headers().add("header_1", "value1");
        httpReq.headers().add("header_1", "value2");

        assertEquals(request.getHeadersAsList("header_1").size(), 2);
    }

    @Test
    void testIsMultipart() {

        URI uri = URI.create("http://localhost:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        httpReq.headers().add(HttpHeaders.Names.CONTENT_TYPE, "multipart/form-data");
        DiscFilterRequest request = new DiscFilterRequest(httpReq);

        assertTrue(DiscFilterRequest.isMultipart(request));

        httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        httpReq.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html;charset=UTF-8");
        request = new DiscFilterRequest(httpReq);

        assertFalse(DiscFilterRequest.isMultipart(request));

        assertFalse(DiscFilterRequest.isMultipart(null));


        httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        request = new DiscFilterRequest(httpReq);
        assertFalse(DiscFilterRequest.isMultipart(request));
    }

    @Test
    void testGetRemotePortLocalPort() {

        URI uri = URI.create("http://example.yahoo.com:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);

        assertEquals(69, request.getRemotePort());
        assertEquals(8080, request.getLocalPort());

        if (request.getRemoteHost() != null) // if we have network
            assertEquals("example.yahoo.com", request.getRemoteHost());

        request.setRemoteAddr("1.1.1.1");

        assertEquals("1.1.1.1", request.getRemoteAddr());
    }

    @Test
    void testCharacterEncoding() {
        URI uri = URI.create("http://example.yahoo.com:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        request.setHeaders(HttpHeaders.Names.CONTENT_TYPE, "text/html;charset=UTF-8");

        assertEquals(request.getCharacterEncoding(), "UTF-8");

        httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        request = new DiscFilterRequest(httpReq);
        request.setHeaders(HttpHeaders.Names.CONTENT_TYPE, "text/html");
        request.setCharacterEncoding("UTF-8");

        assertEquals(request.getCharacterEncoding(), "UTF-8");

        assertEquals(request.getHeader(HttpHeaders.Names.CONTENT_TYPE), "text/html;charset=UTF-8");
    }

    @Test
    void testGetServerPort() {
        {
            URI uri = URI.create("http://example.yahoo.com/test");
            HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
            DiscFilterRequest request = new DiscFilterRequest(httpReq);
            assertEquals(request.getServerPort(), 80);

        }
        {
            URI uri = URI.create("https://example.yahoo.com/test");
            HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
            DiscFilterRequest request = new DiscFilterRequest(httpReq);
            assertEquals(request.getServerPort(), 443);
        }
    }

    @Test
    void testIsSecure() {
        {
            URI uri = URI.create("http://example.yahoo.com/test");
            HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
            DiscFilterRequest request = new DiscFilterRequest(httpReq);
            assertFalse(request.isSecure());
        }
        {
            URI uri = URI.create("https://example.yahoo.com/test");
            HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
            DiscFilterRequest request = new DiscFilterRequest(httpReq);
            assertTrue(request.isSecure());
        }
    }

    @Test
    void requireThatUnresolvableRemoteAddressesAreSupported() {
        URI uri = URI.create("http://doesnotresolve.zzz:8080/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        assertNull(request.getLocalAddr());
    }

    @Test
    void testGetUntreatedHeaders() {
        URI uri = URI.create("http://example.yahoo.com/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        httpReq.headers().add("key1", "value1");
        httpReq.headers().add("key2", Arrays.asList("value1", "value2"));

        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        HeaderFields headers = request.getUntreatedHeaders();
        assertEquals(headers.keySet().size(), 2);
        assertEquals(headers.get("key1").get(0), "value1");
        assertEquals(headers.get("key2").get(0), "value1");
        assertEquals(headers.get("key2").get(1), "value2");
    }

    @Test
    void testClearCookies() {
        URI uri = URI.create("http://example.yahoo.com/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        httpReq.headers().put(HttpHeaders.Names.COOKIE, "XYZ=value");
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        request.clearCookies();
        assertNull(request.getHeader(HttpHeaders.Names.COOKIE));
    }

    @Test
    void testGetWrapedCookies() {
        URI uri = URI.create("http://example.yahoo.com/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        httpReq.headers().put(HttpHeaders.Names.COOKIE, "XYZ=value");
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        JDiscCookieWrapper[] wrappers = request.getWrappedCookies();
        assertEquals(wrappers.length, 1);
        assertEquals(wrappers[0].getName(), "XYZ");
        assertEquals(wrappers[0].getValue(), "value");
    }

    @Test
    void testAddCookie() {
        URI uri = URI.create("http://example.yahoo.com/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterRequest request = new DiscFilterRequest(httpReq);
        request.addCookie(JDiscCookieWrapper.wrap(new Cookie("name", "value")));

        List<Cookie> cookies = request.getCookies();
        assertEquals(cookies.size(), 1);
        assertEquals(cookies.get(0).getName(), "name");
        assertEquals(cookies.get(0).getValue(), "value");
    }
}
