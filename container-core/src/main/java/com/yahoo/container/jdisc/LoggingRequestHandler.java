// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.handler.Timing;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.http.server.jetty.AccessLoggingRequestHandler;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * A request handler base class extending the features of
 * ThreadedHttpRequestHandler with access logging.
 *
 * @author Steinar Knutsen
 *
 * @deprecated  Use {@link ThreadedHttpRequestHandler}, which provides the same level of functionality.
 */
@Deprecated
public abstract class LoggingRequestHandler extends ThreadedHttpRequestHandler {

    // TODO: Deprecate
    public static class Context {

        final Executor executor;
        final Metric metric;

        @Inject
        public Context(Executor executor, Metric metric) {
            this.executor = executor;
            this.metric = metric;
        }

        public Context(Context other) {
            this.executor = other.executor;
            this.metric = other.metric;
        }

        public Executor getExecutor() { return executor; }
        public Metric getMetric() { return metric; }

    }

    public static Context testOnlyContext() {
        return new Context(new Executor() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            },
            null);
    }

    @Inject
    public LoggingRequestHandler(Context ctx) {
        this(ctx.executor, ctx.metric);
    }

    public LoggingRequestHandler(Executor executor) {
        this(executor, (Metric)null);
    }

    public LoggingRequestHandler(Context ctx, boolean allowAsyncResponse) {
        this(ctx.executor, ctx.metric, allowAsyncResponse);
    }

    public LoggingRequestHandler(Executor executor, Metric metric) {
        this(executor, metric, false);
    }

    public LoggingRequestHandler(Executor executor, Metric metric, boolean allowAsyncResponse) {
        super(executor, metric, allowAsyncResponse);
    }

    @Override
    protected LoggingCompletionHandler createLoggingCompletionHandler(long startTime,
                                                                      long renderStartTime,
                                                                      HttpResponse response,
                                                                      HttpRequest httpRequest,
                                                                      ContentChannelOutputStream rendererWiring) {
        return new LoggingHandler(startTime, renderStartTime, httpRequest, response, rendererWiring);
    }

    private static String getClientIP(com.yahoo.jdisc.http.HttpRequest httpRequest) {
        SocketAddress clientAddress = httpRequest.getRemoteAddress();
        if (clientAddress == null) return "0.0.0.0";

        return clientAddress.toString();
    }

    private void logTimes(long startTime, String sourceIP,
                          long renderStartTime, long commitStartTime, long endTime,
                          String req, String normalizedQuery, Timing t) {

        // note: intentionally only taking time since request was received
        long totalTime = endTime - startTime;

        long timeoutInterval = Long.MAX_VALUE;
        long requestOverhead = 0;
        long summaryStartTime = 0;
        if (t != null) {
            timeoutInterval = t.getTimeout();
            long queryStartTime = t.getQueryStartTime();
            if (queryStartTime > 0) {
                requestOverhead = queryStartTime - startTime;
            }
            summaryStartTime = t.getSummaryStartTime();
        }

        if (totalTime <= timeoutInterval) {
            return;
        }

        StringBuilder b = new StringBuilder();
        b.append(normalizedQuery);
        b.append(" from ").append(sourceIP).append(". ");

        if (requestOverhead > 0) {
            b.append("Time from HTTP connection open to request reception ");
            b.append(requestOverhead).append(" ms. ");
        }
        if (summaryStartTime != 0) {
            b.append("Request time: ");
            b.append(summaryStartTime - startTime).append(" ms. ");
            b.append("Summary fetch time: ");
            b.append(renderStartTime - summaryStartTime).append(" ms. ");
        } else {
            long spentSearching = renderStartTime - startTime;
            b.append("Processing time: ").append(spentSearching).append(" ms. ");
        }

        b.append("Result rendering/transfer: ");
        b.append(commitStartTime - renderStartTime).append(" ms. ");
        b.append("End transaction: ");
        b.append(endTime - commitStartTime).append(" ms. ");
        b.append("Total: ").append(totalTime).append(" ms. ");
        b.append("Timeout: ").append(timeoutInterval).append(" ms. ");
        b.append("Request string: ").append(req);

        log.log(Level.WARNING, "Slow execution. " + b);
    }

    private static class NullResponse extends ExtendedResponse {
        NullResponse(int status) {
            super(status);
        }

        @Override
        public void render(OutputStream output, ContentChannel networkChannel, CompletionHandler handler)
                throws IOException {
            // NOP
        }

    }

    private class LoggingHandler implements LoggingCompletionHandler {

        private final long startTime;
        private final long renderStartTime;
        private long commitStartTime;
        private final HttpRequest httpRequest;
        private final HttpResponse httpResponse;
        private final ContentChannelOutputStream rendererWiring;
        private final ExtendedResponse extendedResponse;

        LoggingHandler(long startTime, long renderStartTime, HttpRequest httpRequest, HttpResponse httpResponse,
                       ContentChannelOutputStream rendererWiring) {
            this.startTime = startTime;
            this.renderStartTime = renderStartTime;
            this.commitStartTime = renderStartTime;
            this.httpRequest = httpRequest;
            this.httpResponse = httpResponse;
            this.rendererWiring = rendererWiring;
            this.extendedResponse = actualOrNullObject(httpResponse);
        }

        /** Set the commit start time to the current time */
        @Override
        public void markCommitStart() {
            this.commitStartTime = System.currentTimeMillis();
        }

        private ExtendedResponse actualOrNullObject(HttpResponse response) {
            if (response instanceof ExtendedResponse) {
                return (ExtendedResponse) response;
            } else {
                return new NullResponse(Response.Status.OK);
            }
        }

        @Override
        public void completed() {
            long endTime = System.currentTimeMillis();
            writeToLogs(endTime);
        }

        @Override
        public void failed(Throwable throwable) {
            long endTime = System.currentTimeMillis();
            writeToLogs(endTime);
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Got exception when writing to client: " + Exceptions.toMessageString(throwable));
            }
        }

        private void writeToLogs(long endTime) {
            com.yahoo.jdisc.http.HttpRequest jdiscRequest = httpRequest.getJDiscRequest();

            logTimes(startTime,
                     getClientIP(jdiscRequest),
                     renderStartTime,
                     commitStartTime,
                     endTime,
                     getUri(jdiscRequest),
                     extendedResponse.getParsedQuery(),
                     extendedResponse.getTiming());

            Optional<AccessLogEntry> jdiscRequestAccessLogEntry =
                    AccessLoggingRequestHandler.getAccessLogEntry(jdiscRequest);
            AccessLogEntry entry;
            if (jdiscRequestAccessLogEntry.isPresent()) {
                // The request is created by JDisc http layer (Jetty)
                // Actual logging will be done by the Jetty integration; here, we just need to populate.
                entry = jdiscRequestAccessLogEntry.get();
                httpResponse.populateAccessLogEntry(entry);
            }
        }

        private String getUri(com.yahoo.jdisc.http.HttpRequest jdiscRequest) {
            URI uri = jdiscRequest.getUri();
            StringBuilder builder = new StringBuilder(uri.getPath());
            String query = uri.getQuery();
            if (query != null && !query.isBlank()) {
                builder.append('?').append(query);
            }
            return builder.toString();
        }
    }

}
