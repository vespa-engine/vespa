// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import ai.vespa.utils.BytesQuantity;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.DelegatedRequestHandler;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpRequest;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private final List<String> pathPrefixes;
    private final List<Double> samplingRate;
    private final List<Long> maxSize;
    private final Random rng = new Random();

    public AccessLoggingRequestHandler(
            org.eclipse.jetty.server.Request jettyRequest,
            RequestHandler delegateRequestHandler,
            AccessLogEntry accessLogEntry) {
        this.jettyRequest = jettyRequest;
        this.delegateRequestHandler = delegateRequestHandler;
        this.accessLogEntry = accessLogEntry;
        var cfg = getConnector(jettyRequest).connectorConfig().accessLog().content();
        this.pathPrefixes = cfg.stream().map(e -> e.pathPrefix()).toList();
        this.samplingRate = cfg.stream().map(e -> e.sampleRate()).toList();
        this.maxSize = cfg.stream().map(e -> e.maxSize()).toList();
    }

    @Override
    public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
        final HttpRequest httpRequest = (HttpRequest) request;
        httpRequest.context().put(CONTEXT_KEY_ACCESS_LOG_ENTRY, accessLogEntry);
        var methodsWithEntity = List.of(HttpRequest.Method.POST, HttpRequest.Method.PUT, HttpRequest.Method.PATCH);
        var originalContentChannel = delegateRequestHandler.handleRequest(request, handler);
        var uriPath = request.getUri().getPath();
        if (methodsWithEntity.contains(httpRequest.getMethod())) {
            for (int i = 0; i < pathPrefixes.size(); i++) {
                if (uriPath.startsWith(pathPrefixes.get(i))) {
                    if (samplingRate.get(i) > rng.nextDouble()) {
                        return new ContentLoggingContentChannel(originalContentChannel, maxSize.get(i));
                    }
                }
            }
        }
        return originalContentChannel;
    }

    @Override
    public RequestHandler getDelegate() {
        return delegateRequestHandler;
    }

    private class ContentLoggingContentChannel implements ContentChannel {
        final AtomicLong length = new AtomicLong();
        final ByteArrayOutputStream accumulatedRequestContent;
        final ContentChannel originalContentChannel;
        final long contentLoggingMaxSize;

        public ContentLoggingContentChannel(ContentChannel originalContentChannel, long contentLoggingMaxSize) {
            this.originalContentChannel = originalContentChannel;
            this.contentLoggingMaxSize = contentLoggingMaxSize;
            var contentLength = jettyRequest.getContentLength();
            this.accumulatedRequestContent = new ByteArrayOutputStream(contentLength == -1 ? 128 : contentLength);
        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            length.addAndGet(buf.remaining());
            var bytesToLog = Math.min(buf.remaining(), contentLoggingMaxSize - accumulatedRequestContent.size());
            if (bytesToLog > 0) accumulatedRequestContent.write(buf.array(), buf.arrayOffset() + buf.position(), (int)bytesToLog);
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
