// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.inject.Inject;
import com.yahoo.container.handler.Timing;
import com.yahoo.container.http.AccessLogUtil;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.server.jetty.AccessLoggingRequestHandler;
import com.yahoo.log.LogLevel;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A request handler base class extending the features of
 * ThreadedHttpRequestHandler with access logging.
 *
 * @author Steinar Knutsen
 */
public abstract class LoggingRequestHandler extends ThreadedHttpRequestHandler {

    private AccessLog accessLog;
    private final AtomicReference<LoggingHandler> loggingHandler = new AtomicReference<>();
    private final AtomicBoolean hasLogged = new AtomicBoolean(false);

    public LoggingRequestHandler(Executor executor, AccessLog accessLog) {
        this(executor, accessLog, null);
    }

    public static class Context {
        final Executor executor;
        final AccessLog accessLog;
        final Metric metric;
        @Inject
        public Context(Executor executor, AccessLog accessLog, Metric metric) {
            this.executor = executor;
            this.accessLog = accessLog;
            this.metric = metric;
        }
        public Context(Context other) {
            this.executor = other.executor;
            this.accessLog = other.accessLog;
            this.metric = other.metric;
        }
        public Executor getExecutor() { return executor; }
        public AccessLog getAccessLog() { return accessLog; }
        public Metric getMetric() { return metric; }
    }
    public static Context testOnlyContext() {
        return new Context(new Executor() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            },
            AccessLog.voidAccessLog(),
            null);
    }

    @Inject
    public LoggingRequestHandler(Context ctx) {
        this(ctx.executor, ctx.accessLog, ctx.metric);
    }

    public LoggingRequestHandler(Context ctx, boolean allowAsyncResponse) {
        this(ctx.executor, ctx.accessLog, ctx.metric, allowAsyncResponse);
    }

    public LoggingRequestHandler(Executor executor, AccessLog accessLog, Metric metric) {
        this(executor, accessLog, metric, false);
    }

    public LoggingRequestHandler(Executor executor, AccessLog accessLog, Metric metric, boolean allowAsyncResponse) {
        super(executor, metric, allowAsyncResponse);
        this.accessLog = accessLog;
    }

    @Override
    protected LoggingCompletionHandler createLoggingCompletionHandler(long startTime,
                                                                      long renderStartTime,
                                                                      HttpResponse response,
                                                                      HttpRequest httpRequest,
                                                                      ContentChannelOutputStream rendererWiring) {
        LoggingHandler loggingHandler = new LoggingHandler(startTime, renderStartTime, httpRequest, response, rendererWiring);
        this.loggingHandler.set(loggingHandler);
        return loggingHandler;
    }

    private static String getClientIP(com.yahoo.jdisc.http.HttpRequest httpRequest) {
        SocketAddress clientAddress = httpRequest.getRemoteAddress();
        if (clientAddress == null) return "0.0.0.0";

        return clientAddress.toString();
    }

    private static long getEvalStart(Timing timing, long startTime) {
        if (timing == null || timing.getQueryStartTime() == 0L) {
            return startTime;
        } else {
            return timing.getQueryStartTime();
        }
    }

    private static String remoteHostAddress(com.yahoo.jdisc.http.HttpRequest httpRequest) {
        SocketAddress remoteAddress = httpRequest.getRemoteAddress();
        if (remoteAddress == null) return "0.0.0.0";

        if (remoteAddress instanceof InetSocketAddress) {
            return ((InetSocketAddress) remoteAddress).getAddress().getHostAddress();
        } else {
            throw new RuntimeException("Expected remote address of type InetSocketAddress, got " +
                                       remoteAddress.getClass().getName());
        }
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
            requestOverhead = t.getQueryStartTime() - startTime;
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

        log.log(LogLevel.WARNING, "Slow execution. " + b);
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
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Got exception when writing to client: " + Exceptions.toMessageString(throwable));
            }
        }

        private void writeToLogs(long endTime) {
            if (hasLogged.getAndSet(true)) {
                return;
            }
            com.yahoo.jdisc.http.HttpRequest jdiscRequest = httpRequest.getJDiscRequest();

            logTimes(startTime,
                     getClientIP(jdiscRequest),
                     renderStartTime,
                     commitStartTime,
                     endTime,
                     jdiscRequest.getUri().toString(),
                     extendedResponse.getParsedQuery(),
                     extendedResponse.getTiming());

            Optional<AccessLogEntry> jdiscRequestAccessLogEntry =
                    AccessLoggingRequestHandler.getAccessLogEntry(jdiscRequest);

            if (jdiscRequestAccessLogEntry.isPresent()) {
                // This means we are running with Jetty, not Netty.
                // Actual logging will be done by the Jetty integration; here, we just need to populate.
                httpResponse.populateAccessLogEntry(jdiscRequestAccessLogEntry.get());
                return;
            }

            // We are running without Jetty. No access logging will be done at container level, so we do it here.
            // TODO: Remove when netty support is removed.

            AccessLogEntry accessLogEntry = new AccessLogEntry();

            populateAccessLogEntryNotCreatedByHttpServer(
                    accessLogEntry,
                    jdiscRequest,
                    extendedResponse.getTiming(),
                    httpRequest.getUri().toString(),
                    commitStartTime,
                    startTime,
                    rendererWiring.written(),
                    httpResponse.getStatus());
            httpResponse.populateAccessLogEntry(accessLogEntry);

            accessLog.log(accessLogEntry);
        }
    }

    private void populateAccessLogEntryNotCreatedByHttpServer(AccessLogEntry logEntry,
                                                              com.yahoo.jdisc.http.HttpRequest httpRequest,
                                                              Timing timing,
                                                              String fullRequest,
                                                              long commitStartTime,
                                                              long startTime,
                                                              long written,
                                                              int status) {
        try {
            InetSocketAddress remoteAddress = AccessLogUtil.getRemoteAddress(httpRequest);
            long evalStartTime = getEvalStart(timing, startTime);
            logEntry.setIpV4Address(remoteHostAddress(httpRequest));
            logEntry.setTimeStamp(evalStartTime);
            logEntry.setDurationBetweenRequestResponse(commitStartTime - evalStartTime);
            logEntry.setReturnedContentSize(written);
            logEntry.setStatusCode(status);
            if (remoteAddress != null) {
                logEntry.setRemoteAddress(remoteAddress);
                logEntry.setRemotePort(remoteAddress.getPort());
            }
            logEntry.setURI(AccessLogUtil.getUri(httpRequest));
            logEntry.setUserAgent(AccessLogUtil.getUserAgentHeader(httpRequest));
            logEntry.setReferer(AccessLogUtil.getReferrerHeader(httpRequest));
            logEntry.setHttpMethod(AccessLogUtil.getHttpMethod(httpRequest));
            logEntry.setHttpVersion(AccessLogUtil.getHttpVersion(httpRequest));
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Could not populate the access log [" + fullRequest + "]", e);
        }
    }

    @Override
    public void handleTimeout(Request request, ResponseHandler responseHandler) {
        LoggingHandler loggingHandler = this.loggingHandler.get();
        if (loggingHandler != null) {
            loggingHandler.writeToLogs(System.currentTimeMillis());
        }
        super.handleTimeout(request, responseHandler);
    }
}
