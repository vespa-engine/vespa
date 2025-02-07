// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Metric.Context;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.OverloadException;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpRequest;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.yahoo.jdisc.http.server.jetty.RequestUtils.getConnector;

/**
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
class HttpRequestDispatch {

    public static final String ACCESS_LOG_STATUS_CODE_OVERRIDE = "com.yahoo.jdisc.http.server.jetty.ACCESS_LOG_STATUS_CODE_OVERRIDE";

    private static final Logger log = Logger.getLogger(HttpRequestDispatch.class.getName());
    private static final String CHARSET_ANNOTATION = ";charset=";

    private final JDiscContext jDiscContext;
    private final Request jettyRequest;

    private final ServletResponseController servletResponseController;
    private final RequestHandler requestHandler;
    private final RequestMetricReporter metricReporter;

    HttpRequestDispatch(JDiscContext jDiscContext,
                        AccessLogEntry accessLogEntry,
                        Context metricContext,
                        HttpServletRequest servletRequest,
                        HttpServletResponse servletResponse) throws IOException {
        this.jDiscContext = jDiscContext;

        requestHandler = newRequestHandler(jDiscContext, accessLogEntry, servletRequest);

        this.jettyRequest = (Request) servletRequest;
        this.metricReporter = new RequestMetricReporter(jDiscContext.metric(), metricContext, jettyRequest.getTimeStamp());
        this.servletResponseController = new ServletResponseController(servletRequest,
                                                                       servletResponse,
                                                                       jDiscContext.janitor(),
                                                                       metricReporter,
                                                                       jDiscContext.developerMode());
        shutdownConnectionGracefullyIfThresholdReached(jettyRequest);
        metricReporter.uriLength(jettyRequest.getOriginalURI().length());
    }

    void dispatchRequest() {
        CompletableFuture<Void> requestCompletion = startServletAsyncExecution();
        ServletRequestReader servletRequestReader;
        try {
            servletRequestReader = handleRequest();
        } catch (Throwable t) {
            servletResponseController.finishedFuture()
                    .whenComplete((__, ___) -> requestCompletion.completeExceptionally(t));
            servletResponseController.trySendErrorResponse(t);
            return;
        }

        var readCompleted = servletRequestReader.finishedFuture().whenComplete((__, t) -> {
            if (t != null) servletResponseController.trySendErrorResponse(t);
        });
        var writeCompleted = servletResponseController.finishedFuture().whenComplete((__, t) -> {
            if (t != null) servletRequestReader.fail(t);
        });
        CompletableFuture.allOf(readCompleted, writeCompleted)
                .whenComplete((r, t) -> {
                    if (t != null) requestCompletion.completeExceptionally(t);
                    else requestCompletion.complete(null);
                });
        // Start the reader after wiring of "finished futures" are complete
        servletRequestReader.start();
    }

    private CompletableFuture<Void> startServletAsyncExecution() {
        CompletableFuture<Void> requestCompletion = new CompletableFuture<>();
        AsyncContext asyncCtx = jettyRequest.startAsync();
        asyncCtx.setTimeout(0);
        asyncCtx.addListener(new AsyncListener() {
            @Override public void onStartAsync(AsyncEvent event) {}
            @Override public void onComplete(AsyncEvent event) { requestCompletion.complete(null); }
            @Override public void onTimeout(AsyncEvent event) {
                requestCompletion.completeExceptionally(new TimeoutException("Timeout from AsyncContext"));
            }
            @Override public void onError(AsyncEvent event) {
                requestCompletion.completeExceptionally(event.getThrowable());
            }
        });
        requestCompletion.whenComplete((__, t) -> onRequestFinished(asyncCtx, t));
        return requestCompletion;
    }

    private void onRequestFinished(AsyncContext asyncCtx, Throwable error) {
        boolean reportedError = false;
        if (error != null) {
            // It's too late to write any error response and response writer must therefore be forcefully closed
            servletResponseController.forceClose(error);

            if (isErrorOfType(error, EofException.class, IOException.class)) {
                log.log(Level.FINE,
                        error,
                        () -> "Network connection was unexpectedly terminated: " + jettyRequest.getRequestURI());
                metricReporter.prematurelyClosed();
                asyncCtx.getRequest().setAttribute(ACCESS_LOG_STATUS_CODE_OVERRIDE, 499);
            } else if (isErrorOfType(error, TimeoutException.class)) {
                log.log(Level.FINE,
                        error,
                        () -> "Request/stream was timed out by Jetty: " + jettyRequest.getRequestURI());
                asyncCtx.getRequest().setAttribute(ACCESS_LOG_STATUS_CODE_OVERRIDE, 408);
            } else if (!isErrorOfType(error, OverloadException.class, BindingNotFoundException.class, RequestException.class)) {
                log.log(Level.WARNING, "Request failed: " + jettyRequest.getRequestURI(), error);
            }
            reportedError = true;
            metricReporter.failedResponse();
        } else {
            metricReporter.successfulResponse();
        }

        try {
            asyncCtx.complete();
            log.finest(() -> "Request completed successfully: " + jettyRequest.getRequestURI());
        } catch (Throwable throwable) {
            Level level = reportedError ? Level.FINE: Level.WARNING;
            log.log(level, "Async.complete failed", throwable);
        }
    }

    private static void shutdownConnectionGracefullyIfThresholdReached(Request request) {
        ConnectorConfig connectorConfig = getConnector(request).connectorConfig();
        int maxRequestsPerConnection = connectorConfig.maxRequestsPerConnection();
        Connection connection = RequestUtils.getConnection(request);
        if (maxRequestsPerConnection > 0) {
            if (connection.getMessagesIn() >= maxRequestsPerConnection) {
                gracefulShutdown(connection);
            }
        }
        double maxConnectionLifeInSeconds = connectorConfig.maxConnectionLife();
        if (maxConnectionLifeInSeconds > 0) {
            long createdAt = connection.getCreatedTimeStamp();
            long tenPctVariance = connection.hashCode() % 10; // should be random enough, and must be consistent for a given connection
            Instant expiredAt = Instant.ofEpochMilli((long) (createdAt + maxConnectionLifeInSeconds * 10 * (100 - tenPctVariance)));
            boolean isExpired = Instant.now().isAfter(expiredAt);
            if (isExpired) {
                gracefulShutdown(connection);
            }
        }
    }

    private static void gracefulShutdown(Connection connection) {
        if (connection instanceof HttpConnection http1) {
            http1.getGenerator().setPersistent(false);
        } else if (connection instanceof HTTP2ServerConnection http2) {
            // Signal Jetty to do a graceful shutdown of HTTP/2 connection.
            // Graceful shutdown implies a GOAWAY frame with 'Error Code' = 'NO_ERROR' and 'Last-Stream-ID' = 2^31-1.
            // In-flight requests will be allowed to complete before connection is terminated.
            // See https://datatracker.ietf.org/doc/html/rfc9113#name-goaway for details
            http2.getSession().shutdown();
        }
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static boolean isErrorOfType(Throwable throwable, Class<? extends Throwable>... handledTypes) {
        return Arrays.stream(handledTypes)
                .anyMatch(
                        exceptionType -> exceptionType.isInstance(throwable)
                                || throwable instanceof CompletionException && exceptionType.isInstance(throwable.getCause()));
    }

    @SuppressWarnings("try")
    private ServletRequestReader handleRequest() throws IOException {
        HttpRequest jdiscRequest = HttpRequestFactory.newJDiscRequest(jDiscContext.container(), jettyRequest);
        ContentChannel requestContentChannel;
        try (ResourceReference ref = References.fromResource(jdiscRequest)) {
            HttpRequestFactory.copyHeaders(jettyRequest, jdiscRequest);
            requestContentChannel = requestHandler.handleRequest(jdiscRequest, servletResponseController.responseHandler());
        }
        return new ServletRequestReader(jettyRequest, requestContentChannel, jDiscContext.janitor(), metricReporter);
    }

    private static RequestHandler newRequestHandler(JDiscContext context,
                                                    AccessLogEntry accessLogEntry,
                                                    HttpServletRequest servletRequest) {
        RequestHandler requestHandler = wrapHandlerIfFormPost(
                new FilteringRequestHandler(context.filterResolver(), (Request)servletRequest),
                servletRequest, context.removeRawPostBodyForWwwUrlEncodedPost());

        return new AccessLoggingRequestHandler(
                (Request) servletRequest, requestHandler, accessLogEntry);
    }

    private static RequestHandler wrapHandlerIfFormPost(RequestHandler requestHandler,
                                                        HttpServletRequest servletRequest,
                                                        boolean removeBodyForFormPost) {
        if (!servletRequest.getMethod().equals("POST")) {
            return requestHandler;
        }
        String contentType = servletRequest.getHeader(HttpHeaders.Names.CONTENT_TYPE);
        if (contentType == null) {
            return requestHandler;
        }
        if (!contentType.startsWith(APPLICATION_X_WWW_FORM_URLENCODED)) {
            return requestHandler;
        }
        return new FormPostRequestHandler(requestHandler, getCharsetName(contentType), removeBodyForFormPost);
    }

    private static String getCharsetName(String contentType) {
        if (!contentType.startsWith(CHARSET_ANNOTATION, APPLICATION_X_WWW_FORM_URLENCODED.length())) {
            return StandardCharsets.UTF_8.name();
        }
        return contentType.substring(APPLICATION_X_WWW_FORM_URLENCODED.length() + CHARSET_ANNOTATION.length());
    }
}
