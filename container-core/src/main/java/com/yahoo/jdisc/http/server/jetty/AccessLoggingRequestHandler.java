// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.base.Preconditions;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;

import java.util.Map;
import java.util.Optional;

/**
 * A wrapper RequestHandler that enables access logging. By wrapping the request handler, we are able to wrap the
 * response handler as well. Hence, we can populate the access log entry with information from both the request
 * and the response. This wrapper also adds the access log entry to the request context, so that request handlers
 * may add information to it.
 *
 * Does not otherwise interfere with the request processing of the delegate request handler.
 *
 * @author bakksjo
 */
public class AccessLoggingRequestHandler extends AbstractRequestHandler {
    public static final String CONTEXT_KEY_ACCESS_LOG_ENTRY
            = AccessLoggingRequestHandler.class.getName() + "_access-log-entry";

    public static Optional<AccessLogEntry> getAccessLogEntry(final HttpRequest jdiscRequest) {
        final Map<String, Object> requestContextMap = jdiscRequest.context();
        return getAccessLogEntry(requestContextMap);
    }

    public static Optional<AccessLogEntry> getAccessLogEntry(final Map<String, Object> requestContextMap) {
        return Optional.ofNullable(
                (AccessLogEntry) requestContextMap.get(CONTEXT_KEY_ACCESS_LOG_ENTRY));
    }

    private final RequestHandler delegate;
    private final AccessLogEntry accessLogEntry;

    public AccessLoggingRequestHandler(
            final RequestHandler delegateRequestHandler,
            final AccessLogEntry accessLogEntry) {
        this.delegate = delegateRequestHandler;
        this.accessLogEntry = accessLogEntry;
    }

    @Override
    public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
        Preconditions.checkArgument(request instanceof HttpRequest, "Expected HttpRequest, got " + request);
        final HttpRequest httpRequest = (HttpRequest) request;
        httpRequest.context().put(CONTEXT_KEY_ACCESS_LOG_ENTRY, accessLogEntry);
        return delegate.handleRequest(request, handler);
    }


}
