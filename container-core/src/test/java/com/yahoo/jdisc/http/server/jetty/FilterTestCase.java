// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.AbstractModule;
import com.google.inject.util.Modules;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.filter.ResponseHeaderFilter;
import com.yahoo.jdisc.http.filter.chain.RequestFilterChain;
import com.yahoo.jdisc.http.filter.chain.ResponseFilterChain;
import com.yahoo.jdisc.http.guiceModules.ConnectorFactoryRegistryModule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class FilterTestCase {
    @Test
    public void requireThatRequestFilterIsNotRunOnUnboundPath() throws Exception {
        RequestFilterMockBase filter = mock(RequestFilterMockBase.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filter)
                .addRequestFilterBinding("my-request-filter", "http://*/filtered/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(filter, never()).filter(any(HttpRequest.class), any(ResponseHandler.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatRequestFilterIsRunOnBoundPath() throws Exception {
        final RequestFilter filter = mock(RequestFilterMockBase.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filter)
                .addRequestFilterBinding("my-request-filter", "http://*/filtered/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/filtered/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(filter, times(1)).filter(any(HttpRequest.class), any(ResponseHandler.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatRequestFilterChangesAreSeenByRequestHandler() throws Exception {
        final RequestFilter filter = new HeaderRequestFilter("foo", "bar");
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filter)
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        assertThat(requestHandler.getHeaderMap().get("foo").get(0), is("bar"));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatRequestFilterCanRespond() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", new RespondForbiddenFilter())
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html").expectStatusCode(is(Response.Status.FORBIDDEN));

        assertThat(requestHandler.hasBeenInvokedYet(), is(false));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatFilterCanHaveNullCompletionHandler() throws Exception {
        final int responseStatus = Response.Status.OK;
        final String responseMessage = "Excellent";
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", new NullCompletionHandlerFilter(responseStatus, responseMessage))
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html")
                .expectStatusCode(is(responseStatus))
                .expectContent(is(responseMessage));

        assertThat(requestHandler.hasBeenInvokedYet(), is(false));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatRequestFilterExecutionIsExceptionSafe() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", new ThrowingRequestFilter())
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html").expectStatusCode(is(Response.Status.INTERNAL_SERVER_ERROR));

        assertThat(requestHandler.hasBeenInvokedYet(), is(false));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatResponseFilterIsNotRunOnUnboundPath() throws Exception {
        final ResponseFilter filter = mock(ResponseFilterMockBase.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", filter)
                .addResponseFilterBinding("my-response-filter", "http://*/filtered/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(filter, never()).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatResponseFilterIsRunOnBoundPath() throws Exception {
        final ResponseFilter filter = mock(ResponseFilterMockBase.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", filter)
                .addResponseFilterBinding("my-response-filter", "http://*/filtered/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/filtered/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(filter, times(1)).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatResponseFilterChangesAreWrittenToResponse() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", new HeaderResponseFilter("foo", "bar"))
                .addResponseFilterBinding("my-response-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html")
                .expectHeader("foo", is("bar"));

        assertThat(requestHandler.awaitInvocation(), is(true));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatResponseFilterExecutionIsExceptionSafe() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", new ThrowingResponseFilter())
                .addResponseFilterBinding("my-response-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html").expectStatusCode(is(Response.Status.INTERNAL_SERVER_ERROR));

        assertThat(requestHandler.awaitInvocation(), is(true));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatRequestFilterAndResponseFilterCanBindToSamePath() throws Exception {
        final RequestFilter requestFilter = mock(RequestFilterMockBase.class);
        final ResponseFilter responseFilter = mock(ResponseFilterMockBase.class);
        final String uriPattern = "http://*/*";
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", requestFilter)
                .addRequestFilterBinding("my-request-filter", uriPattern)
                .addResponseFilter("my-response-filter", responseFilter)
                .addResponseFilterBinding("my-response-filter", uriPattern)
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(requestFilter, times(1)).filter(any(HttpRequest.class), any(ResponseHandler.class));
        verify(responseFilter, times(1)).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatResponseFromRequestFilterGoesThroughResponseFilter() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", new RespondForbiddenFilter())
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .addResponseFilter("my-response-filter", new HeaderResponseFilter("foo", "bar"))
                .addResponseFilterBinding("my-response-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html")
                .expectStatusCode(is(Response.Status.FORBIDDEN))
                .expectHeader("foo", is("bar"));

        assertThat(requestHandler.hasBeenInvokedYet(), is(false));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatRequestFilterChainRetainsFilters() {
        final RequestFilter requestFilter1 = mock(RequestFilter.class);
        final RequestFilter requestFilter2 = mock(RequestFilter.class);

        verify(requestFilter1, never()).refer();
        verify(requestFilter2, never()).refer();
        final ResourceReference reference1 = mock(ResourceReference.class);
        final ResourceReference reference2 = mock(ResourceReference.class);
        when(requestFilter1.refer()).thenReturn(reference1);
        when(requestFilter2.refer()).thenReturn(reference2);
        final RequestFilter chain = RequestFilterChain.newInstance(requestFilter1, requestFilter2);
        verify(requestFilter1, times(1)).refer();
        verify(requestFilter2, times(1)).refer();

        verify(reference1, never()).close();
        verify(reference2, never()).close();
        chain.release();
        verify(reference1, times(1)).close();
        verify(reference2, times(1)).close();
    }

    @Test
    public void requireThatRequestFilterChainIsRun() throws Exception {
        final RequestFilter requestFilter1 = mock(RequestFilter.class);
        final RequestFilter requestFilter2 = mock(RequestFilter.class);
        final RequestFilter requestFilterChain = RequestFilterChain.newInstance(requestFilter1, requestFilter2);
        final HttpRequest request = null;
        final ResponseHandler responseHandler = null;
        requestFilterChain.filter(request, responseHandler);
        verify(requestFilter1).filter(isNull(), any(ResponseHandler.class));
        verify(requestFilter2).filter(isNull(), any(ResponseHandler.class));
    }

    @Test
    public void requireThatRequestFilterChainCallsFilterWithOriginalRequest() throws Exception {
        final RequestFilter requestFilter = mock(RequestFilter.class);
        final RequestFilter requestFilterChain = RequestFilterChain.newInstance(requestFilter);
        final HttpRequest request = mock(HttpRequest.class);
        final ResponseHandler responseHandler = null;
        requestFilterChain.filter(request, responseHandler);

        // Check that the filter is called with the same request argument as the chain was,
        // in a manner that allows the request object to be wrapped.
        final ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(requestFilter).filter(requestCaptor.capture(), isNull());
        verify(request, never()).getUri();
        requestCaptor.getValue().getUri();
        verify(request, times(1)).getUri();
    }

    @Test
    public void requireThatRequestFilterChainCallsFilterWithOriginalResponseHandler() throws Exception {
        final RequestFilter requestFilter = mock(RequestFilter.class);
        final RequestFilter requestFilterChain = RequestFilterChain.newInstance(requestFilter);
        final HttpRequest request = null;
        final ResponseHandler responseHandler = mock(ResponseHandler.class);
        requestFilterChain.filter(request, responseHandler);

        // Check that the filter is called with the same response handler argument as the chain was,
        // in a manner that allows the handler object to be wrapped.
        final ArgumentCaptor<ResponseHandler> responseHandlerCaptor = ArgumentCaptor.forClass(ResponseHandler.class);
        verify(requestFilter).filter(isNull(), responseHandlerCaptor.capture());
        verify(responseHandler, never()).handleResponse(any(Response.class));
        responseHandlerCaptor.getValue().handleResponse(mock(Response.class));
        verify(responseHandler, times(1)).handleResponse(any(Response.class));
    }

    @Test
    public void requireThatRequestFilterCanTerminateChain() throws Exception {
        final RequestFilter requestFilter1 = new RespondForbiddenFilter();
        final RequestFilter requestFilter2 = mock(RequestFilter.class);
        final RequestFilter requestFilterChain = RequestFilterChain.newInstance(requestFilter1, requestFilter2);
        final HttpRequest request = null;
        final ResponseHandler responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handleResponse(any(Response.class))).thenReturn(mock(ContentChannel.class));

        requestFilterChain.filter(request, responseHandler);

        verify(requestFilter2, never()).filter(any(HttpRequest.class), any(ResponseHandler.class));

        final ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(responseHandler).handleResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getStatus(), is(Response.Status.FORBIDDEN));
    }

    @Test
    public void requireThatResponseFilterChainRetainsFilters() {
        final ResponseFilter responseFilter1 = mock(ResponseFilter.class);
        final ResponseFilter responseFilter2 = mock(ResponseFilter.class);

        verify(responseFilter1, never()).refer();
        verify(responseFilter2, never()).refer();
        final ResourceReference reference1 = mock(ResourceReference.class);
        final ResourceReference reference2 = mock(ResourceReference.class);
        when(responseFilter1.refer()).thenReturn(reference1);
        when(responseFilter2.refer()).thenReturn(reference2);
        final ResponseFilter chain = ResponseFilterChain.newInstance(responseFilter1, responseFilter2);
        verify(responseFilter1, times(1)).refer();
        verify(responseFilter2, times(1)).refer();

        verify(reference1, never()).close();
        verify(reference2, never()).close();
        chain.release();
        verify(reference1, times(1)).close();
        verify(reference2, times(1)).close();
    }

    @Test
    public void requireThatResponseFilterChainIsRun() {
        final ResponseFilter responseFilter1 = new ResponseHeaderFilter("foo", "bar");
        final ResponseFilter responseFilter2 = mock(ResponseFilter.class);
        final int statusCode = Response.Status.BAD_GATEWAY;
        final Response response = new Response(statusCode);
        final Request request = null;

        ResponseFilterChain.newInstance(responseFilter1, responseFilter2).filter(response, request);

        final ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(responseFilter2).filter(responseCaptor.capture(), isNull());
        assertThat(responseCaptor.getValue().getStatus(), is(statusCode));
        assertThat(responseCaptor.getValue().headers().getFirst("foo"), is("bar"));

        assertThat(response.getStatus(), is(statusCode));
        assertThat(response.headers().getFirst("foo"), is("bar"));
    }

    @Test
    public void requireThatDefaultRequestFilterChainIsRunIfNoOtherFilterChainMatches() throws IOException, InterruptedException {
        RequestFilter filterWithBinding = mock(RequestFilter.class);
        RequestFilter defaultFilter = mock(RequestFilter.class);
        String defaultFilterId = "default-request-filter";
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filterWithBinding)
                .addRequestFilterBinding("my-request-filter", "http://*/filtered/*")
                .addRequestFilter(defaultFilterId, defaultFilter)
                .setRequestFilterDefaultForPort(defaultFilterId, 0)
                .build();
        MyRequestHandler requestHandler = new MyRequestHandler();
        TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(defaultFilter, times(1)).filter(any(HttpRequest.class), any(ResponseHandler.class));
        verify(filterWithBinding, never()).filter(any(HttpRequest.class), any(ResponseHandler.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatDefaultResponseFilterChainIsRunIfNoOtherFilterChainMatches() throws IOException, InterruptedException {
        ResponseFilter filterWithBinding = mock(ResponseFilter.class);
        ResponseFilter defaultFilter = mock(ResponseFilter.class);
        String defaultFilterId = "default-response-filter";
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", filterWithBinding)
                .addResponseFilterBinding("my-response-filter", "http://*/filtered/*")
                .addResponseFilter(defaultFilterId, defaultFilter)
                .setResponseFilterDefaultForPort(defaultFilterId, 0)
                .build();
        MyRequestHandler requestHandler = new MyRequestHandler();
        TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(defaultFilter, times(1)).filter(any(Response.class), any(Request.class));
        verify(filterWithBinding, never()).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatRequestFilterWithBindingMatchHasPrecedenceOverDefaultFilter() throws IOException, InterruptedException {
        RequestFilterMockBase filterWithBinding = mock(RequestFilterMockBase.class);
        RequestFilterMockBase defaultFilter = mock(RequestFilterMockBase.class);
        String defaultFilterId = "default-request-filter";
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filterWithBinding)
                .addRequestFilterBinding("my-request-filter", "http://*/filtered/*")
                .addRequestFilter(defaultFilterId, defaultFilter)
                .setRequestFilterDefaultForPort(defaultFilterId, 0)
                .build();
        MyRequestHandler requestHandler = new MyRequestHandler();
        TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/filtered/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(defaultFilter, never()).filter(any(HttpRequest.class), any(ResponseHandler.class));
        verify(filterWithBinding).filter(any(HttpRequest.class), any(ResponseHandler.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatResponseFilterWithBindingMatchHasPrecedenceOverDefaultFilter() throws IOException, InterruptedException {
        ResponseFilter filterWithBinding = mock(ResponseFilter.class);
        ResponseFilter defaultFilter = mock(ResponseFilter.class);
        String defaultFilterId = "default-response-filter";
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", filterWithBinding)
                .addResponseFilterBinding("my-response-filter", "http://*/filtered/*")
                .addResponseFilter(defaultFilterId, defaultFilter)
                .setResponseFilterDefaultForPort(defaultFilterId, 0)
                .build();
        MyRequestHandler requestHandler = new MyRequestHandler();
        TestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/filtered/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(defaultFilter, never()).filter(any(Response.class), any(Request.class));
        verify(filterWithBinding, times(1)).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatMetricAreReported() throws IOException, InterruptedException {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", mock(RequestFilter.class))
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        MetricConsumerMock metricConsumerMock = new MetricConsumerMock();
        MyRequestHandler requestHandler = new MyRequestHandler();
        TestDriver testDriver = newDriver(requestHandler, filterBindings, metricConsumerMock, false);

        testDriver.client().get("/status.html");
        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(metricConsumerMock.mockitoMock())
                .add(MetricDefinitions.FILTERING_REQUEST_HANDLED, 1L, MetricConsumerMock.STATIC_CONTEXT);
        verify(metricConsumerMock.mockitoMock(), never())
                .add(MetricDefinitions.FILTERING_REQUEST_UNHANDLED, 1L, MetricConsumerMock.STATIC_CONTEXT);
        verify(metricConsumerMock.mockitoMock(), never())
                .add(MetricDefinitions.FILTERING_RESPONSE_HANDLED, 1L, MetricConsumerMock.STATIC_CONTEXT);
        verify(metricConsumerMock.mockitoMock())
                .add(MetricDefinitions.FILTERING_RESPONSE_UNHANDLED, 1L, MetricConsumerMock.STATIC_CONTEXT);
        assertThat(testDriver.close(), is(true));
    }

    @Test
    public void requireThatStrictFilteringRejectsRequestsNotMatchingFilterChains() throws IOException {
        RequestFilter filter = mock(RequestFilter.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filter)
                .addRequestFilterBinding("my-request-filter", "http://*/filtered/*")
                .build();
        MyRequestHandler requestHandler = new MyRequestHandler();
        TestDriver testDriver = newDriver(requestHandler, filterBindings, new MetricConsumerMock(), true);

        testDriver.client().get("/unfiltered/")
                .expectStatusCode(is(Response.Status.FORBIDDEN))
                .expectContent(containsString("Request did not match any request filter chain"));
        verify(filter, never()).filter(any(), any());
        assertThat(testDriver.close(), is(true));
    }

    private static TestDriver newDriver(MyRequestHandler requestHandler, FilterBindings filterBindings) {
        return newDriver(requestHandler, filterBindings, new MetricConsumerMock(), false);
    }

    private static TestDriver newDriver(
            MyRequestHandler requestHandler,
            FilterBindings filterBindings,
            MetricConsumerMock metricConsumer,
            boolean strictFiltering) {
        return TestDriver.newInstance(
                JettyHttpServer.class,
                requestHandler,
                newFilterModule(filterBindings, metricConsumer, strictFiltering));
    }

    private static com.google.inject.Module newFilterModule(
            FilterBindings filterBindings, MetricConsumerMock metricConsumer, boolean strictFiltering) {
        return Modules.combine(
                new AbstractModule() {
                    @Override
                    protected void configure() {

                        bind(FilterBindings.class).toInstance(filterBindings);
                        bind(ServerConfig.class).toInstance(new ServerConfig(new ServerConfig.Builder().strictFiltering(strictFiltering)));
                        bind(ConnectorConfig.class).toInstance(new ConnectorConfig(new ConnectorConfig.Builder()));
                        bind(ServletPathsConfig.class).toInstance(new ServletPathsConfig(new ServletPathsConfig.Builder()));
                        bind(ConnectionLog.class).toInstance(new VoidConnectionLog());
                        bind(RequestLog.class).toInstance(new VoidRequestLog());
                    }
                },
                new ConnectorFactoryRegistryModule(),
                metricConsumer.asGuiceModule());
    }

    private static abstract class RequestFilterMockBase extends AbstractResource implements RequestFilter {}
    private static abstract class ResponseFilterMockBase extends AbstractResource implements ResponseFilter {}

    private static class MyRequestHandler extends AbstractRequestHandler {
        private final CountDownLatch invocationLatch = new CountDownLatch(1);
        private final AtomicReference<Map<String, List<String>>> headerCopy = new AtomicReference<>(null);

        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            try {
                headerCopy.set(new HashMap<String, List<String>>(request.headers()));
                ResponseDispatch.newInstance(Response.Status.OK).dispatch(handler);
                return null;
            } finally {
                invocationLatch.countDown();
            }
        }

        public boolean hasBeenInvokedYet() {
            return invocationLatch.getCount() == 0L;
        }

        public boolean awaitInvocation() throws InterruptedException {
            return invocationLatch.await(60, TimeUnit.SECONDS);
        }

        public Map<String, List<String>> getHeaderMap() {
            return headerCopy.get();
        }
    }

    private static class RespondForbiddenFilter extends AbstractResource implements RequestFilter {
        @Override
        public void filter(final HttpRequest request, final ResponseHandler handler) {
            ResponseDispatch.newInstance(Response.Status.FORBIDDEN).dispatch(handler);
        }
    }

    private static class ThrowingRequestFilter extends AbstractResource implements RequestFilter {
        @Override
        public void filter(final HttpRequest request, final ResponseHandler handler) {
            throw new RuntimeException();
        }
    }

    private static class ThrowingResponseFilter extends AbstractResource implements ResponseFilter {
        @Override
        public void filter(final Response response, final Request request) {
            throw new RuntimeException();
        }
    }

    private static class HeaderRequestFilter extends AbstractResource implements RequestFilter {
        private final String key;
        private final String val;

        public HeaderRequestFilter(final String key, final String val) {
            this.key = key;
            this.val = val;
        }

        @Override
        public void filter(final HttpRequest request, final ResponseHandler handler) {
            request.headers().add(key, val);
        }
    }

    private static class HeaderResponseFilter extends AbstractResource implements ResponseFilter {
        private final String key;
        private final String val;

        public HeaderResponseFilter(final String key, final String val) {
            this.key = key;
            this.val = val;
        }

        @Override
        public void filter(final Response response, final Request request) {
            response.headers().add(key, val);
        }
    }

    public class NullCompletionHandlerFilter extends AbstractResource implements RequestFilter {
        private final int responseStatus;
        private final String responseMessage;

        public NullCompletionHandlerFilter(final int responseStatus, final String responseMessage) {
            this.responseStatus = responseStatus;
            this.responseMessage = responseMessage;
        }

        @Override
        public void filter(final HttpRequest request, final ResponseHandler responseHandler) {
            final HttpResponse response = HttpResponse.newInstance(responseStatus);
            final ContentChannel channel = responseHandler.handleResponse(response);
            final CompletionHandler completionHandler = null;
            channel.write(ByteBuffer.wrap(responseMessage.getBytes()), completionHandler);
            channel.close(null);
        }
    }
}
