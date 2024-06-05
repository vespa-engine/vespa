// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.DelegatedRequestHandler;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpRequest;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yahoo.jdisc.http.server.jetty.RequestUtils.getConnector;

/**
 * A wrapper RequestHandler that enables access logging. By wrapping the request handler, we are able to wrap the
 * response handler as well. Hence, we can populate the access log entry with information from both the request
 * and the response. This wrapper also adds the access log entry to the request context, so that request handlers
 * may add information to it.
 *
 * Does not otherwise interfere with the request processing of the delegate request handler.
 *
 * @author bakksjo
 * @author bjorncs
 */
public class AccessLoggingRequestHandler extends AbstractRequestHandler implements DelegatedRequestHandler {
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

    private final org.eclipse.jetty.server.Request jettyRequest;
    private final RequestHandler delegateRequestHandler;
    private final AccessLogEntry accessLogEntry;
    private final Predicate<String> contentLoggingEnabledMatcher;

    public AccessLoggingRequestHandler(
            org.eclipse.jetty.server.Request jettyRequest,
            RequestHandler delegateRequestHandler,
            AccessLogEntry accessLogEntry) {
        this.jettyRequest = jettyRequest;
        this.delegateRequestHandler = delegateRequestHandler;
        this.accessLogEntry = accessLogEntry;
        var contentPathPrefixes = getConnector(jettyRequest).connectorConfig().accessLog().contentPathPrefixes();
        this.contentLoggingEnabledMatcher = contentPathPrefixes.isEmpty()
                ? __ -> false
                : Pattern.compile(
                        contentPathPrefixes.stream()
                                .map(Pattern::quote)
                                .collect(Collectors.joining("|", "^(", ")")))
                .asMatchPredicate();

    }

    @Override
    public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
        final HttpRequest httpRequest = (HttpRequest) request;
        httpRequest.context().put(CONTEXT_KEY_ACCESS_LOG_ENTRY, accessLogEntry);
        var acceptedMethods = List.of(HttpRequest.Method.POST, HttpRequest.Method.PUT, HttpRequest.Method.PATCH);
        var originalContentChannel = delegateRequestHandler.handleRequest(request, handler);
        if (acceptedMethods.contains(httpRequest.getMethod())
                && contentLoggingEnabledMatcher.test(request.getUri().getPath())) {
            return new ContentLoggingContentChannel(originalContentChannel);
        } else {
            return originalContentChannel;
        }
    }

    @Override
    public RequestHandler getDelegate() {
        return delegateRequestHandler;
    }

    private class ContentLoggingContentChannel implements ContentChannel {
        private static final int CONTENT_LOGGING_MAX_SIZE = 16 * 1024 * 1024;

        final AtomicLong length = new AtomicLong();
        final ByteArrayOutputStream accumulatedRequestContent;
        final ContentChannel originalContentChannel;

        public ContentLoggingContentChannel(ContentChannel originalContentChannel) {
            this.originalContentChannel = originalContentChannel;
            var contentLength = jettyRequest.getContentLength();
            this.accumulatedRequestContent = new ByteArrayOutputStream(contentLength == -1 ? 128 : contentLength);
        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            length.addAndGet(buf.remaining());
            var bytesToLog = Math.min(buf.remaining(), CONTENT_LOGGING_MAX_SIZE - accumulatedRequestContent.size());
            if (bytesToLog > 0) accumulatedRequestContent.write(buf.array(), buf.arrayOffset() + buf.position(), bytesToLog);
            if (originalContentChannel != null) originalContentChannel.write(buf, handler);
        }

        @Override
        public void close(CompletionHandler handler) {
            var bytes = accumulatedRequestContent.toByteArray();
            accessLogEntry.setContent(new AccessLogEntry.Content(
                    Objects.requireNonNullElse(jettyRequest.getHeader(HttpHeaders.Names.CONTENT_TYPE), ""),
                    length.get(),
                    bytes));
            accumulatedRequestContent.reset();
            length.set(0);
            if (originalContentChannel != null) originalContentChannel.close(handler);
        }

        @Override
        public void onError(Throwable error) {
            if (originalContentChannel != null) originalContentChannel.onError(error);
        }
    }
}
