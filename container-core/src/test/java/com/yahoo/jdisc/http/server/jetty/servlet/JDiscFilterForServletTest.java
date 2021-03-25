// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty.servlet;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.server.jetty.FilterBindings;
import com.yahoo.jdisc.http.server.jetty.FilterInvoker;
import com.yahoo.jdisc.http.server.jetty.SimpleHttpClient.ResponseValidator;
import com.yahoo.jdisc.http.server.jetty.TestDriver;
import com.yahoo.jdisc.http.server.jetty.TestDrivers;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

/**
 * @author Tony Vaagenes
 * @author bjorncs
 */
public class JDiscFilterForServletTest extends ServletTestBase {
    @Test
    public void request_filter_can_return_response() throws IOException, InterruptedException {
        TestDriver testDriver = requestFilterTestDriver();
        ResponseValidator response = httpGet(testDriver, TestServlet.PATH).execute();

        response.expectContent(containsString(TestRequestFilter.responseContent));
    }

    @Test
    public void request_can_be_forwarded_through_request_filter_to_servlet() throws IOException {
        TestDriver testDriver = requestFilterTestDriver();
        ResponseValidator response = httpGet(testDriver, TestServlet.PATH).
                addHeader(TestRequestFilter.BYPASS_FILTER_HEADER, Boolean.TRUE.toString()).
                execute();

        response.expectContent(containsString(TestServlet.RESPONSE_CONTENT));
    }

    @Test
    public void response_filter_can_modify_response() throws IOException {
        TestDriver testDriver = responseFilterTestDriver();
        ResponseValidator response = httpGet(testDriver, TestServlet.PATH).execute();

        response.expectHeader(TestResponseFilter.INVOKED_HEADER, is(Boolean.TRUE.toString()));
    }

    @Test
    public void response_filter_is_run_on_empty_sync_response() throws IOException {
        TestDriver testDriver = responseFilterTestDriver();
        ResponseValidator response = httpGet(testDriver, NoContentTestServlet.PATH).execute();

        response.expectHeader(TestResponseFilter.INVOKED_HEADER, is(Boolean.TRUE.toString()));
    }

    @Test
    public void response_filter_is_run_on_empty_async_response() throws IOException {
        TestDriver testDriver = responseFilterTestDriver();
        ResponseValidator response = httpGet(testDriver, NoContentTestServlet.PATH).
                addHeader(NoContentTestServlet.HEADER_ASYNC, Boolean.TRUE.toString()).
                execute();

        response.expectHeader(TestResponseFilter.INVOKED_HEADER, is(Boolean.TRUE.toString()));
    }

    private TestDriver requestFilterTestDriver() throws IOException {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", new TestRequestFilter())
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        return TestDrivers.newInstance(dummyRequestHandler, bindings(filterBindings));
    }

    private TestDriver responseFilterTestDriver() throws IOException {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", new TestResponseFilter())
                .addResponseFilterBinding("my-response-filter", "http://*/*")
                .build();
        return TestDrivers.newInstance(dummyRequestHandler, bindings(filterBindings));
    }



    private Module bindings(FilterBindings filterBindings) {
        return Modules.combine(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(FilterBindings.class).toInstance(filterBindings);
                        bind(FilterInvoker.class).toInstance(new FilterInvoker() {
                            @Override
                            public HttpServletRequest invokeRequestFilterChain(
                                    RequestFilter requestFilter,
                                    URI uri,
                                    HttpServletRequest httpRequest,
                                    ResponseHandler responseHandler) {
                                TestRequestFilter filter = (TestRequestFilter) requestFilter;
                                filter.runAsSecurityFilter(httpRequest, responseHandler);
                                return httpRequest;
                            }

                            @Override
                            public void invokeResponseFilterChain(
                                    ResponseFilter responseFilter,
                                    URI uri,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {

                                TestResponseFilter filter = (TestResponseFilter) responseFilter;
                                filter.runAsSecurityFilter(request, response);
                            }
                        });
                    }
                },
                guiceModule());
    }

    static class TestRequestFilter extends AbstractResource implements RequestFilter {
        static final String simpleName = TestRequestFilter.class.getSimpleName();
        static final String responseContent = "Rejected by " + simpleName;
        static final String BYPASS_FILTER_HEADER = "BYPASS_HEADER" + simpleName;

        @Override
        public void filter(HttpRequest request, ResponseHandler handler) {
            throw new UnsupportedOperationException();
        }

        public void runAsSecurityFilter(HttpServletRequest request, ResponseHandler responseHandler) {
            if (Boolean.parseBoolean(request.getHeader(BYPASS_FILTER_HEADER)))
                return;

            ContentChannel contentChannel = responseHandler.handleResponse(new Response(500));
            contentChannel.write(ByteBuffer.wrap(responseContent.getBytes(StandardCharsets.UTF_8)), null);
            contentChannel.close(null);
        }
    }


    static class TestResponseFilter extends AbstractResource implements ResponseFilter {
        static final String INVOKED_HEADER = TestResponseFilter.class.getSimpleName() + "_INVOKED_HEADER";

        @Override
        public void filter(Response response, Request request) {
            throw new UnsupportedClassVersionError();
        }

        public void runAsSecurityFilter(HttpServletRequest request, HttpServletResponse response) {
            response.addHeader(INVOKED_HEADER, Boolean.TRUE.toString());
        }
    }
}
