// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.AbstractModule;
import com.google.inject.util.Modules;
import com.yahoo.container.jdisc.HttpRequestHandler;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.DelegatedRequestHandler;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.filter.ResponseHeaderFilter;
import com.yahoo.jdisc.http.filter.chain.RequestFilterChain;
import com.yahoo.jdisc.http.filter.chain.ResponseFilterChain;
import com.yahoo.jdisc.http.server.jetty.testutils.ConnectorFactoryRegistryModule;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void requireThatRequestFilterIsNotRunOnUnboundPath() throws Exception {
        RequestFilterMockBase filter = mock(RequestFilterMockBase.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filter)
                .addRequestFilterBinding("my-request-filter", "http://*/filtered/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(filter, never()).filter(any(HttpRequest.class), any(ResponseHandler.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatRequestFilterIsRunOnBoundPath() throws Exception {
        final RequestFilter filter = mock(RequestFilterMockBase.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filter)
                .addRequestFilterBinding("my-request-filter", "http://*/filtered/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/filtered/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(filter, times(1)).filter(any(HttpRequest.class), any(ResponseHandler.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatRequestFilterChangesAreSeenByRequestHandler() throws Exception {
        final RequestFilter filter = new HeaderRequestFilter("foo", "bar");
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filter)
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        assertThat(requestHandler.getHeaderMap().get("foo").get(0), is("bar"));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatRequestFilterCanRespond() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", new RespondForbiddenFilter())
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html").expectStatusCode(is(Response.Status.FORBIDDEN));

        assertThat(requestHandler.hasBeenInvokedYet(), is(false));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatFilterCanHaveNullCompletionHandler() throws Exception {
        final int responseStatus = Response.Status.OK;
        final String responseMessage = "Excellent";
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", new NullCompletionHandlerFilter(responseStatus, responseMessage))
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html")
                .expectStatusCode(is(responseStatus))
                .expectContent(is(responseMessage));

        assertThat(requestHandler.hasBeenInvokedYet(), is(false));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatRequestFilterExecutionIsExceptionSafe() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", new ThrowingRequestFilter())
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html").expectStatusCode(is(Response.Status.INTERNAL_SERVER_ERROR));

        assertThat(requestHandler.hasBeenInvokedYet(), is(false));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatResponseFilterIsNotRunOnUnboundPath() throws Exception {
        final ResponseFilter filter = mock(ResponseFilterMockBase.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", filter)
                .addResponseFilterBinding("my-response-filter", "http://*/filtered/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(filter, never()).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatResponseFilterIsRunOnBoundPath() throws Exception {
        final ResponseFilter filter = mock(ResponseFilterMockBase.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", filter)
                .addResponseFilterBinding("my-response-filter", "http://*/filtered/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/filtered/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(filter, times(1)).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatResponseFilterChangesAreWrittenToResponse() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", new HeaderResponseFilter("foo", "bar"))
                .addResponseFilterBinding("my-response-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html")
                .expectHeader("foo", is("bar"));

        assertThat(requestHandler.awaitInvocation(), is(true));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatResponseFilterExecutionIsExceptionSafe() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addResponseFilter("my-response-filter", new ThrowingResponseFilter())
                .addResponseFilterBinding("my-response-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html").expectStatusCode(is(Response.Status.INTERNAL_SERVER_ERROR));

        assertThat(requestHandler.awaitInvocation(), is(true));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatRequestFilterAndResponseFilterCanBindToSamePath() throws Exception {
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
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(requestFilter, times(1)).filter(any(HttpRequest.class), any(ResponseHandler.class));
        verify(responseFilter, times(1)).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatResponseFromRequestFilterGoesThroughResponseFilter() throws Exception {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", new RespondForbiddenFilter())
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .addResponseFilter("my-response-filter", new HeaderResponseFilter("foo", "bar"))
                .addResponseFilterBinding("my-response-filter", "http://*/*")
                .build();
        final MyRequestHandler requestHandler = new MyRequestHandler();
        final JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html")
                .expectStatusCode(is(Response.Status.FORBIDDEN))
                .expectHeader("foo", is("bar"));

        assertThat(requestHandler.hasBeenInvokedYet(), is(false));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatRequestFilterChainRetainsFilters() {
        final RequestFilter requestFilter1 = mock(RequestFilter.class);
        final RequestFilter requestFilter2 = mock(RequestFilter.class);

        final ResourceReference reference1 = mock(ResourceReference.class);
        final ResourceReference reference2 = mock(ResourceReference.class);
        when(requestFilter1.refer(any())).thenReturn(reference1);
        when(requestFilter2.refer(any())).thenReturn(reference2);
        final RequestFilter chain = RequestFilterChain.newInstance(requestFilter1, requestFilter2);
        verify(requestFilter1, times(1)).refer(any());
        verify(requestFilter2, times(1)).refer(any());

        verify(reference1, never()).close();
        verify(reference2, never()).close();
        chain.release();
        verify(reference1, times(1)).close();
        verify(reference2, times(1)).close();
    }

    @Test
    void requireThatRequestFilterChainIsRun() throws Exception {
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
    void requireThatRequestFilterChainCallsFilterWithOriginalRequest() throws Exception {
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
    void requireThatRequestFilterChainCallsFilterWithOriginalResponseHandler() throws Exception {
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
    void requireThatRequestFilterCanTerminateChain() throws Exception {
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
    void requireThatResponseFilterChainRetainsFilters() {
        final ResponseFilter responseFilter1 = mock(ResponseFilter.class);
        final ResponseFilter responseFilter2 = mock(ResponseFilter.class);

        final ResourceReference reference1 = mock(ResourceReference.class);
        final ResourceReference reference2 = mock(ResourceReference.class);
        when(responseFilter1.refer(any())).thenReturn(reference1);
        when(responseFilter2.refer(any())).thenReturn(reference2);
        final ResponseFilter chain = ResponseFilterChain.newInstance(responseFilter1, responseFilter2);
        verify(responseFilter1, times(1)).refer(any());
        verify(responseFilter2, times(1)).refer(any());

        verify(reference1, never()).close();
        verify(reference2, never()).close();
        chain.release();
        verify(reference1, times(1)).close();
        verify(reference2, times(1)).close();
    }

    @Test
    void requireThatResponseFilterChainIsRun() {
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
    void requireThatDefaultRequestFilterChainIsRunIfNoOtherFilterChainMatches() throws IOException, InterruptedException {
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
        JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(defaultFilter, times(1)).filter(any(HttpRequest.class), any(ResponseHandler.class));
        verify(filterWithBinding, never()).filter(any(HttpRequest.class), any(ResponseHandler.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatDefaultResponseFilterChainIsRunIfNoOtherFilterChainMatches() throws IOException, InterruptedException {
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
        JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(defaultFilter, times(1)).filter(any(Response.class), any(Request.class));
        verify(filterWithBinding, never()).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatRequestFilterWithBindingMatchHasPrecedenceOverDefaultFilter() throws IOException, InterruptedException {
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
        JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/filtered/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(defaultFilter, never()).filter(any(HttpRequest.class), any(ResponseHandler.class));
        verify(filterWithBinding).filter(any(HttpRequest.class), any(ResponseHandler.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatResponseFilterWithBindingMatchHasPrecedenceOverDefaultFilter() throws IOException, InterruptedException {
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
        JettyTestDriver testDriver = newDriver(requestHandler, filterBindings);

        testDriver.client().get("/filtered/status.html");

        assertThat(requestHandler.awaitInvocation(), is(true));
        verify(defaultFilter, never()).filter(any(Response.class), any(Request.class));
        verify(filterWithBinding, times(1)).filter(any(Response.class), any(Request.class));

        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatMetricAreReported() throws IOException, InterruptedException {
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", mock(RequestFilter.class))
                .addRequestFilterBinding("my-request-filter", "http://*/*")
                .build();
        MetricConsumerMock metricConsumerMock = new MetricConsumerMock();
        MyRequestHandler requestHandler = new MyRequestHandler();
        JettyTestDriver testDriver = newDriver(requestHandler, filterBindings, metricConsumerMock);

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
    void requireThatStrictFilteringRejectsRequestsNotMatchingFilterChains() throws IOException {
        RequestFilter filter = mock(RequestFilter.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filter)
                .addRequestFilterBinding("my-request-filter", "http://*/filtered/*")
                .setStrictFiltering(true)
                .build();
        MyRequestHandler requestHandler = new MyRequestHandler();
        JettyTestDriver testDriver = newDriver(requestHandler, filterBindings, new MetricConsumerMock());

        testDriver.client().get("/unfiltered/")
                .expectStatusCode(is(Response.Status.FORBIDDEN))
                .expectContent(containsString("Request did not match any request filter chain"));
        verify(filter, never()).filter(any(), any());
        assertThat(testDriver.close(), is(true));
    }

    @Test
    void requireThatRequestHandlerSpecIsAvailableThroughDelegate() throws IOException, InterruptedException {
        MyRequestHandler requestHandler = new MyHttpRequestHandler();
        MyDelegatedHandler delegateHandler1 = new MyDelegatedHandler(requestHandler);
        MyDelegatedHandler delegateHandler2 = new MyDelegatedHandler(delegateHandler1);
        requestHandlerSpecTest(delegateHandler2);
    }

    @Test
    void requireThatRequestHandlerSpecIsAvailable() throws IOException, InterruptedException {
        MyRequestHandler requestHandler = new MyHttpRequestHandler();
        requestHandlerSpecTest(requestHandler);
    }

    private void requestHandlerSpecTest(MyRequestHandler requestHandler) throws IOException, InterruptedException {
        RequestFilter filter = mock(RequestFilter.class);
        FilterBindings filterBindings = new FilterBindings.Builder()
                .addRequestFilter("my-request-filter", filter)
                .addRequestFilterBinding("my-request-filter", "http://*/filtered/*")
                .build();

        JettyTestDriver testDriver = newDriver(requestHandler, filterBindings, new MetricConsumerMock());

        testDriver.client().get("/filtered/")
                .expectStatusCode(is(Response.Status.OK));
        ArgumentCaptor<HttpRequest> requestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(filter).filter(requestArgumentCaptor.capture(), any(ResponseHandler.class));
        assertTrue(requestArgumentCaptor.getValue().context().containsKey(RequestHandlerSpec.ATTRIBUTE_NAME));
    }

    private static JettyTestDriver newDriver(MyRequestHandler requestHandler, FilterBindings filterBindings) {
        return newDriver(requestHandler, filterBindings, new MetricConsumerMock());
    }

    private static JettyTestDriver newDriver(
            MyRequestHandler requestHandler,
            FilterBindings filterBindings,
            MetricConsumerMock metricConsumer) {
        return JettyTestDriver.newInstance(
                requestHandler,
                newFilterModule(filterBindings, metricConsumer));
    }

    private static com.google.inject.Module newFilterModule(
            FilterBindings filterBindings, MetricConsumerMock metricConsumer) {
        return Modules.combine(
                new AbstractModule() {
                    @Override
                    protected void configure() {

                        bind(FilterBindings.class).toInstance(filterBindings);
                        bind(ServerConfig.class).toInstance(new ServerConfig(new ServerConfig.Builder()));
                        bind(ConnectorConfig.class).toInstance(new ConnectorConfig(new ConnectorConfig.Builder()));
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
                headerCopy.set(new HashMap<>(request.headers()));
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

    private static class MyDelegatedHandler extends MyRequestHandler implements DelegatedRequestHandler {

        private final RequestHandler delegate;

        public MyDelegatedHandler(RequestHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public RequestHandler getDelegate() {
            return delegate;
        }
        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            return delegate.handleRequest(request, handler);
        }
        @Override
        public void handleTimeout(Request request, ResponseHandler handler) {
            delegate.handleTimeout(request, handler);
        }
    }

    private static class MyHttpRequestHandler extends MyRequestHandler implements HttpRequestHandler {
        @Override
        public RequestHandlerSpec requestHandlerSpec() {
            return RequestHandlerSpec.DEFAULT_INSTANCE;
        }
    }
}
