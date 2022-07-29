// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DiscFilterResponseTest {

    private static HttpRequest newRequest(URI uri, HttpRequest.Method method, HttpRequest.Version version) {
        InetSocketAddress address = new InetSocketAddress("localhost", 69);
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        HttpRequest request = HttpRequest.newServerRequest(driver, uri, method, version, address);
        request.release();
        assertTrue(driver.close());
        return request;
    }

    public static HttpResponse newResponse(Request request, int status) {
        return HttpResponse.newInstance(status);
    }

    @Test
    void testGetSetStatus() {
        HttpRequest request = newRequest(URI.create("http://localhost:8080/echo"),
                HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterResponse response = new DiscFilterResponse(HttpResponse.newInstance(HttpResponse.Status.OK));

        assertEquals(response.getStatus(), HttpResponse.Status.OK);
        response.setStatus(HttpResponse.Status.REQUEST_TIMEOUT);
        assertEquals(response.getStatus(), HttpResponse.Status.REQUEST_TIMEOUT);
    }

    @Test
    void testAttributes() {
        HttpRequest request = newRequest(URI.create("http://localhost:8080/echo"),
                HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterResponse response = new DiscFilterResponse(HttpResponse.newInstance(HttpResponse.Status.OK));
        response.setAttribute("attr_1", "value1");
        assertEquals(response.getAttribute("attr_1"), "value1");
        List<String> list = Collections.list(response.getAttributeNames());
        assertEquals(list.get(0), "attr_1");
        response.removeAttribute("attr_1");
        assertNull(response.getAttribute("attr_1"));
    }

    @Test
    void testAddHeader() {
        HttpRequest request = newRequest(URI.create("http://localhost:8080/echo"),
                HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        DiscFilterResponse response = new DiscFilterResponse(HttpResponse.newInstance(HttpResponse.Status.OK));
        response.addHeader("header1", "value1");
        assertEquals(response.getHeader("header1"), "value1");
    }

    @Test
    void testAddCookie() {
        URI uri = URI.create("http://example.com/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        HttpResponse httpResp = newResponse(httpReq, 200);
        DiscFilterResponse response = new DiscFilterResponse(httpResp);
        response.addCookie(JDiscCookieWrapper.wrap(new Cookie("name", "value")));

        List<Cookie> cookies = response.getCookies();
        assertEquals(cookies.size(), 1);
        assertEquals(cookies.get(0).getName(), "name");
    }

    @Test
    void testSetCookie() {
        URI uri = URI.create("http://example.com/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        HttpResponse httpResp = newResponse(httpReq, 200);
        DiscFilterResponse response = new DiscFilterResponse(httpResp);
        response.setCookie("name", "value");
        List<Cookie> cookies = response.getCookies();
        assertEquals(cookies.size(), 1);
        assertEquals(cookies.get(0).getName(), "name");

    }

    @Test
    void testSetHeader() {
        URI uri = URI.create("http://example.com/test");
        HttpRequest httpReq = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        HttpResponse httpResp = newResponse(httpReq, 200);
        DiscFilterResponse response = new DiscFilterResponse(httpResp);
        response.setHeader("name", "value");
        assertEquals(response.getHeader("name"), "value");
    }

}
