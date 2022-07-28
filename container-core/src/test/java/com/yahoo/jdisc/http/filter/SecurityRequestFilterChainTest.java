// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bjorncs
 */
public class SecurityRequestFilterChainTest {


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
        SecurityRequestFilterChain chain = (SecurityRequestFilterChain) SecurityRequestFilterChain.newInstance();
        assertEquals(chain.getFilters().size(), 0);

        List<SecurityRequestFilter> requestFilters = new ArrayList<SecurityRequestFilter>();
        chain = (SecurityRequestFilterChain) SecurityRequestFilterChain.newInstance();

        chain = (SecurityRequestFilterChain) SecurityRequestFilterChain.newInstance(new RequestHeaderFilter("abc", "xyz"),
                new RequestHeaderFilter("pqr", "def"));

        assertEquals(chain instanceof SecurityRequestFilterChain, true);
    }


    @Test
    void testFilterChainRun() {
        RequestFilter chain = SecurityRequestFilterChain.newInstance(new RequestHeaderFilter("abc", "xyz"),
                new RequestHeaderFilter("pqr", "def"));

        assertEquals(chain instanceof SecurityRequestFilterChain, true);
        ResponseHandler handler = newResponseHandler();
        HttpRequest request =  newRequest(URI.create("http://test/test"), HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        chain.filter(request, handler);
        assertTrue(request.headers().contains("abc", "xyz"));
        assertTrue(request.headers().contains("pqr", "def"));
    }

    @Test
    void testFilterChainResponds() {
        RequestFilter chain = SecurityRequestFilterChain.newInstance(
                new MyFilter(),
                new RequestHeaderFilter("abc", "xyz"),
                new RequestHeaderFilter("pqr", "def"));

        assertEquals(chain instanceof SecurityRequestFilterChain, true);
        ResponseHandler handler = newResponseHandler();
        HttpRequest request =  newRequest(URI.create("http://test/test"), HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
        chain.filter(request, handler);
        Response response = getResponse(handler);
        assertNotNull(response);
        assertFalse(request.headers().contains("abc", "xyz"));
        assertFalse(request.headers().contains("pqr", "def"));
    }

    private class RequestHeaderFilter extends AbstractResource implements SecurityRequestFilter {

        private final String key;
        private final String val;

        public RequestHeaderFilter(String key, String val) {
            this.key = key;
            this.val = val;
        }

        @Override
        public void filter(DiscFilterRequest request, ResponseHandler handler) {
            request.setHeaders(key, val);
        }
    }

    private class MyFilter extends AbstractResource implements SecurityRequestFilter {

        @Override
        public void filter(DiscFilterRequest request, ResponseHandler handler) {
            ResponseDispatch.newInstance(Response.Status.FORBIDDEN).dispatch(handler);
        }
    }

    private static ResponseHandler newResponseHandler() {
        return new NonWorkingResponseHandler();
    }

    private static Response getResponse(ResponseHandler handler) {
        return ((NonWorkingResponseHandler) handler).getResponse();
    }

    private static class NonWorkingResponseHandler implements ResponseHandler {

        private Response response = null;

        @Override
        public ContentChannel handleResponse(Response response) {
            this.response = response;
            return new NonWorkingContentChannel();
        }

        public Response getResponse() {
            return response;
        }
    }

    private static class NonWorkingContentChannel implements ContentChannel {

        @Override
        public void close(CompletionHandler handler) {

        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {

        }

    }

}
