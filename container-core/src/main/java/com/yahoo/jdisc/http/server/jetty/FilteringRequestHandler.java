// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.base.Preconditions;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.jdisc.HttpRequestHandler;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.DelegatedRequestHandler;
import com.yahoo.jdisc.handler.RequestDeniedException;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Request handler that invokes request and response filters in addition to the bound request handler.
 *
 * @author Øyvind Bakksjø
 */
class FilteringRequestHandler extends AbstractRequestHandler {

    private static final ContentChannel COMPLETING_CONTENT_CHANNEL = new ContentChannel() {

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            CompletionHandlers.tryComplete(handler);
        }

        @Override
        public void close(CompletionHandler handler) {
            CompletionHandlers.tryComplete(handler);
        }

    };

    private final FilterResolver filterResolver;
    private final org.eclipse.jetty.server.Request jettyRequest;

    public FilteringRequestHandler(FilterResolver filterResolver, org.eclipse.jetty.server.Request jettyRequest) {
        this.filterResolver = filterResolver;
        this.jettyRequest = jettyRequest;
    }

    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler originalResponseHandler) {
        Preconditions.checkArgument(request instanceof HttpRequest, "Expected HttpRequest, got " + request);
        Objects.requireNonNull(originalResponseHandler, "responseHandler");

        RequestFilter requestFilter = filterResolver.resolveRequestFilter(jettyRequest, request.getUri())
                .orElse(null);
        ResponseFilter responseFilter = filterResolver.resolveResponseFilter(jettyRequest, request.getUri())
                .orElse(null);

        // Not using request.connect() here - it adds logic for error handling that we'd rather leave to the framework.
        RequestHandler resolvedRequestHandler = request.container().resolveHandler(request);

        if (resolvedRequestHandler == null) {
            throw new BindingNotFoundException(request.getUri());
        }

        getRequestHandlerSpec(resolvedRequestHandler)
                .ifPresent(requestHandlerSpec -> request.context().put(RequestHandlerSpec.ATTRIBUTE_NAME, requestHandlerSpec));

        RequestHandler requestHandler = new ReferenceCountingRequestHandler(
                new CapabilityEnforcingRequestHandler(resolvedRequestHandler));

        ResponseHandler responseHandler;
        if (responseFilter != null) {
            responseHandler = new FilteringResponseHandler(originalResponseHandler, responseFilter, request);
        } else {
            responseHandler = originalResponseHandler;
        }

        if (requestFilter != null) {
            InterceptingResponseHandler interceptingResponseHandler = new InterceptingResponseHandler(responseHandler);
            requestFilter.filter(HttpRequest.class.cast(request), interceptingResponseHandler);
            if (interceptingResponseHandler.hasProducedResponse()) {
                return COMPLETING_CONTENT_CHANNEL;
            }
        }

        ContentChannel contentChannel = requestHandler.handleRequest(request, responseHandler);
        if (contentChannel == null) {
            throw new RequestDeniedException(request);
        }
        return contentChannel;
    }

    private Optional<RequestHandlerSpec> getRequestHandlerSpec(RequestHandler resolvedRequestHandler) {
        RequestHandler delegate = resolvedRequestHandler;
        if (delegate instanceof DelegatedRequestHandler) {
            delegate = ((DelegatedRequestHandler) delegate).getDelegateRecursive();
        }
        if(delegate instanceof HttpRequestHandler) {
            return Optional.ofNullable(((HttpRequestHandler) delegate).requestHandlerSpec());
        } else {
            return Optional.empty();
        }
    }

    private static class FilteringResponseHandler implements ResponseHandler {

        private final ResponseHandler delegate;
        private final ResponseFilter responseFilter;
        private final Request request;

        public FilteringResponseHandler(ResponseHandler delegate, ResponseFilter responseFilter, Request request) {
            this.delegate = Objects.requireNonNull(delegate);
            this.responseFilter = Objects.requireNonNull(responseFilter);
            this.request = request;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            responseFilter.filter(response, request);
            return delegate.handleResponse(response);
        }

    }

    private static class InterceptingResponseHandler implements ResponseHandler {

        private final ResponseHandler delegate;
        private AtomicBoolean hasResponded = new AtomicBoolean(false);

        public InterceptingResponseHandler(ResponseHandler delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            ContentChannel content = delegate.handleResponse(response);
            hasResponded.set(true);
            return content;
        }

        public boolean hasProducedResponse() {
            return hasResponded.get();
        }
    }

}
