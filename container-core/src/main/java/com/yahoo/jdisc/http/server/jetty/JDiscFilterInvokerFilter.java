// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.RequestFilter;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.yahoo.jdisc.http.server.jetty.JDiscHttpServlet.getConnector;
import static com.yahoo.yolean.Exceptions.throwUnchecked;

/**
 * Runs JDisc security filters for Servlets
 * This component is split in two:
 *   1) JDiscFilterInvokerFilter, which uses package private methods to support JDisc APIs
 *   2) SecurityFilterInvoker, which is intended for use in a servlet context.
 *
 * @author Tony Vaagenes
 */
class JDiscFilterInvokerFilter implements Filter {
    private final JDiscContext jDiscContext;
    private final FilterInvoker filterInvoker;

    public JDiscFilterInvokerFilter(JDiscContext jDiscContext,
                                    FilterInvoker filterInvoker) {
        this.jDiscContext = jDiscContext;
        this.filterInvoker = filterInvoker;
    }


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

        URI uri;
        try {
            uri = HttpRequestFactory.getUri(httpRequest);
        } catch (RequestException e) {
            httpResponse.sendError(e.getResponseStatus(), e.getMessage());
            return;
        }

        AtomicReference<Boolean> responseReturned = new AtomicReference<>(null);

        HttpServletRequest newRequest = runRequestFilterWithMatchingBinding(responseReturned, uri, httpRequest, httpResponse);
        assert newRequest != null;
        responseReturned.compareAndSet(null, false);

        if (!responseReturned.get()) {
            runChainAndResponseFilters(uri, newRequest, httpResponse, chain);
        }
    }

    private void runChainAndResponseFilters(URI uri, HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        Optional<OneTimeRunnable> responseFilterInvoker =
                jDiscContext.filterResolver.resolveResponseFilter(request, uri)
                        .map(responseFilter ->
                                new OneTimeRunnable(() ->
                                        filterInvoker.invokeResponseFilterChain(responseFilter, uri, request, response)));


        HttpServletResponse responseForServlet = responseFilterInvoker
                .<HttpServletResponse>map(invoker ->
                        new FilterInvokingResponseWrapper(response, invoker))
                .orElse(response);

        HttpServletRequest requestForServlet = responseFilterInvoker
                .<HttpServletRequest>map(invoker ->
                        new FilterInvokingRequestWrapper(request, invoker, responseForServlet))
                .orElse(request);

        chain.doFilter(requestForServlet, responseForServlet);

        responseFilterInvoker.ifPresent(invoker -> {
            boolean requestHandledSynchronously = !request.isAsyncStarted();

            if (requestHandledSynchronously) {
                invoker.runIfFirstInvocation();
            }
            // For async requests, response filters will be invoked on AsyncContext.complete().
        });
    }

    private HttpServletRequest runRequestFilterWithMatchingBinding(AtomicReference<Boolean> responseReturned, URI uri, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            RequestFilter requestFilter = jDiscContext.filterResolver.resolveRequestFilter(request, uri).orElse(null);
            if (requestFilter == null)
                return request;

            ResponseHandler responseHandler = createResponseHandler(responseReturned, request, response);
            return filterInvoker.invokeRequestFilterChain(requestFilter, uri, request, responseHandler);
        } catch (Exception e) {
            throw new RuntimeException("Failed running request filter chain for uri " + uri, e);
        }
    }

    private ResponseHandler createResponseHandler(AtomicReference<Boolean> responseReturned, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return jdiscResponse -> {
            boolean oldValueWasNull = responseReturned.compareAndSet(null, true);
            if (!oldValueWasNull)
                throw new RuntimeException("Can't return response from filter asynchronously");

            HttpRequestDispatch requestDispatch = createRequestDispatch(httpRequest, httpResponse);
            return requestDispatch.handleRequestFilterResponse(jdiscResponse);
        };
    }

    private HttpRequestDispatch createRequestDispatch(HttpServletRequest request, HttpServletResponse response) {
        try {
            final AccessLogEntry accessLogEntry = null; // Not used in this context.
            return new HttpRequestDispatch(jDiscContext,
                                           accessLogEntry,
                                           getConnector(request).createRequestMetricContext(request, Map.of()),
                                           request, response);
        } catch (IOException e) {
            throw throwUnchecked(e);
        }
    }

    @Override
    public void destroy() {}

    // ServletRequest wrapper that is necessary because we need to wrap AsyncContext.
    private static class FilterInvokingRequestWrapper extends HttpServletRequestWrapper {
        private final OneTimeRunnable filterInvoker;
        private final HttpServletResponse servletResponse;

        public FilterInvokingRequestWrapper(
                HttpServletRequest request,
                OneTimeRunnable filterInvoker,
                HttpServletResponse servletResponse) {
            super(request);
            this.filterInvoker = filterInvoker;
            this.servletResponse = servletResponse;
        }

        @Override
        public AsyncContext startAsync() {
            final AsyncContext asyncContext = super.startAsync();
            return new FilterInvokingAsyncContext(asyncContext, filterInvoker, this, servletResponse);
        }

        @Override
        public AsyncContext startAsync(
                final ServletRequest wrappedRequest,
                final ServletResponse wrappedResponse) {
            // According to the documentation, the passed request/response parameters here must either
            // _be_ or _wrap_ the original request/response objects passed to the servlet - which are
            // our wrappers, so no need to wrap again - we can use the user-supplied objects.
            final AsyncContext asyncContext = super.startAsync(wrappedRequest, wrappedResponse);
            return new FilterInvokingAsyncContext(asyncContext, filterInvoker, this, wrappedResponse);
        }

        @Override
        public AsyncContext getAsyncContext() {
            final AsyncContext asyncContext = super.getAsyncContext();
            return new FilterInvokingAsyncContext(asyncContext, filterInvoker, this, servletResponse);
        }
    }

    // AsyncContext wrapper that is necessary for two reasons:
    // 1) Run response filters when AsyncContext.complete() is called.
    // 2) Eliminate paths where application code can get its hands on un-wrapped response object, circumventing
    //    running of response filters.
    private static class FilterInvokingAsyncContext implements AsyncContext {
        private final AsyncContext delegate;
        private final OneTimeRunnable filterInvoker;
        private final ServletRequest servletRequest;
        private final ServletResponse servletResponse;

        public FilterInvokingAsyncContext(
                AsyncContext delegate,
                OneTimeRunnable filterInvoker,
                ServletRequest servletRequest,
                ServletResponse servletResponse) {
            this.delegate = delegate;
            this.filterInvoker = filterInvoker;
            this.servletRequest = servletRequest;
            this.servletResponse = servletResponse;
        }

        @Override
        public ServletRequest getRequest() {
            return servletRequest;
        }

        @Override
        public ServletResponse getResponse() {
            return servletResponse;
        }

        @Override
        public boolean hasOriginalRequestAndResponse() {
            return delegate.hasOriginalRequestAndResponse();
        }

        @Override
        public void dispatch() {
            delegate.dispatch();
        }

        @Override
        public void dispatch(String s) {
            delegate.dispatch(s);
        }

        @Override
        public void dispatch(ServletContext servletContext, String s) {
            delegate.dispatch(servletContext, s);
        }

        @Override
        public void complete() {
            // Completing may commit the response, so this is the last chance to run response filters.
            filterInvoker.runIfFirstInvocation();
            delegate.complete();
        }

        @Override
        public void start(Runnable runnable) {
            delegate.start(runnable);
        }

        @Override
        public void addListener(AsyncListener asyncListener) {
            delegate.addListener(asyncListener);
        }

        @Override
        public void addListener(AsyncListener asyncListener, ServletRequest servletRequest, ServletResponse servletResponse) {
            delegate.addListener(asyncListener, servletRequest, servletResponse);
        }

        @Override
        public <T extends AsyncListener> T createListener(Class<T> aClass) throws ServletException {
            return delegate.createListener(aClass);
        }

        @Override
        public void setTimeout(long l) {
            delegate.setTimeout(l);
        }

        @Override
        public long getTimeout() {
            return delegate.getTimeout();
        }
    }

    private static class FilterInvokingResponseWrapper extends HttpServletResponseWrapper {
        private final OneTimeRunnable filterInvoker;

        public FilterInvokingResponseWrapper(HttpServletResponse response, OneTimeRunnable filterInvoker) {
            super(response);
            this.filterInvoker = filterInvoker;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            ServletOutputStream delegate = super.getOutputStream();
            return new FilterInvokingServletOutputStream(delegate, filterInvoker);
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            PrintWriter delegate = super.getWriter();
            return new FilterInvokingPrintWriter(delegate, filterInvoker);
        }
    }
}
