// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class SecurityResponseFilterChainTest {
    private static HttpRequest newRequest(URI uri, HttpRequest.Method method, HttpRequest.Version version) {
        InetSocketAddress address = new InetSocketAddress("java.corp.yahoo.com", 69);
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        HttpRequest request = HttpRequest.newServerRequest(driver, uri, method, version, address);
        request.release();
        assertTrue(driver.close());
        return request;
    }

    @Test
    void testFilterChainConstruction() {
        SecurityResponseFilterChain chain = (SecurityResponseFilterChain) SecurityResponseFilterChain.newInstance();
        assertEquals(chain.getFilters().size(), 0);

        chain = (SecurityResponseFilterChain) SecurityResponseFilterChain.newInstance(new ResponseHeaderFilter("abc", "xyz"),
                new ResponseHeaderFilter("pqr", "def"));

        assertEquals(chain instanceof SecurityResponseFilterChain, true);
    }

    @Test
    void testFilterChainRun() {
        URI uri = URI.create("http://localhost:8080/echo");
        HttpRequest request = newRequest(uri, HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        Response response = HttpResponse.newInstance(Response.Status.OK);

        ResponseFilter chain = SecurityResponseFilterChain.newInstance(new ResponseHeaderFilter("abc", "xyz"),
                new ResponseHeaderFilter("pqr", "def"));
        chain.filter(response, null);
        assertTrue(response.headers().contains("abc", "xyz"));
        assertTrue(response.headers().contains("pqr", "def"));
    }

    private class ResponseHeaderFilter extends AbstractResource implements SecurityResponseFilter {

        private final String key;
        private final String val;

        public ResponseHeaderFilter(String key, String val) {
            this.key = key;
            this.val = val;
        }

        @Override
        public void filter(DiscFilterResponse response, RequestView request) {
            response.setHeaders(key, val);
        }

    }



}
