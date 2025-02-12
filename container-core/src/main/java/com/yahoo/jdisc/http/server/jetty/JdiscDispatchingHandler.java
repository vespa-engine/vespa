// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.OverloadException;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.service.BindingSetNotFoundException;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http2.server.internal.HTTP2ServerConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.internal.HttpConnection;
import org.eclipse.jetty.util.Callback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.jdisc.http.HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED;

/**
 * Implementation of a Jetty {@link Handler} that dispatches requests to JDisc's {@link com.yahoo.jdisc.handler.RequestHandler}.
 *
 * @author bjorncs
 */
class JdiscDispatchingHandler extends Handler.Abstract.NonBlocking {

    public static final String ATTRIBUTE_NAME_ACCESS_LOG_ENTRY = "ai.vespa.jetty.ACCESS_LOG_ENTRY";
    public static final String ACCESS_LOG_STATUS_CODE_OVERRIDE = "ai.vespa.jetty.ACCESS_LOG_STATUS_CODE_OVERRIDE";

    private static final Logger log = Logger.getLogger(JdiscDispatchingHandler.class.getName());

    private static final Set<String> SUPPORTED_METHODS =
            Stream.of(HttpRequest.Method.OPTIONS, HttpRequest.Method.GET, HttpRequest.Method.HEAD,
                            HttpRequest.Method.POST, HttpRequest.Method.PUT, HttpRequest.Method.DELETE,
                            HttpRequest.Method.TRACE, HttpRequest.Method.PATCH)
                    .map(HttpRequest.Method::name)
                    .collect(Collectors.toSet());

    private final Supplier<JDiscContext> contextSupplier;

    JdiscDispatchingHandler(Supplier<JDiscContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    @Override
    public boolean handle(Request jettyRequest, Response jettyResponse, Callback callback) throws Exception {
        try {
            processJettyRequest(jettyRequest, jettyResponse, callback);
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Unexpected error when processing request", t);
            callback.failed(t);
        }
        return true;
    }

    private void processJettyRequest(Request jettyRequest, Response jettyResponse, Callback callback) {
        var context = contextSupplier.get();
        var connector = RequestUtils.getConnector(jettyRequest);
        jettyRequest.setAttribute(JDiscServerConnector.REQUEST_ATTRIBUTE, connector);

        var metricContext = connector.createRequestMetricContext(jettyRequest, Map.of());
        context.metric().add(MetricDefinitions.NUM_REQUESTS, 1, metricContext);
        context.metric().add(MetricDefinitions.JDISC_HTTP_REQUESTS, 1, metricContext);

        var accessLogEntry = new AccessLogEntry();
        jettyRequest.setAttribute(ATTRIBUTE_NAME_ACCESS_LOG_ENTRY, accessLogEntry);

        if (!SUPPORTED_METHODS.contains(jettyRequest.getMethod().toUpperCase())) {
            Response.writeError(jettyRequest, jettyResponse, callback, HttpStatus.METHOD_NOT_ALLOWED_405);
            return;
        }
        var requestHandler = newRequestHandler(context, accessLogEntry, jettyRequest);
        var metricReporter = new RequestMetricReporter(context.metric(), metricContext, Request.getTimeStamp(jettyRequest));
        var responseWriter = new JettyResponseWriter(jettyRequest, jettyResponse, metricReporter);
        shutdownConnectionGracefullyIfThresholdReached(connector, jettyRequest);
        metricReporter.uriLength(jettyRequest.getHttpURI().getPath().length());

        JettyRequestContentReader contentReader;

        // Prepare async wiring of request completion (success and failure)
        var requestCompletion = new CompletableFuture<Void>();
        requestCompletion
                .whenComplete((result, error) -> {
                    if (error != null) {
                        onFailure(metricReporter, responseWriter, jettyRequest, callback, error, context.developerMode());
                    } else {
                        onSuccess(metricReporter, callback);
                    }
                });

        // Invoke request handler
        try {
            var jdiscRequest = HttpRequestFactory.newJDiscRequest(context.container(), jettyRequest);
            try (var ignored = References.fromResource(jdiscRequest)) {
                var requestContentChannel = requestHandler.handleRequest(jdiscRequest, responseWriter);
                contentReader = new JettyRequestContentReader(
                        metricReporter, context.janitor(), jettyRequest, requestContentChannel);
            }
        } catch (Throwable error) {
            // Fail if an exception is thrown when constructing the jdisc request or in the request handler
            requestCompletion.completeExceptionally(error);
            return;
        }

        // Propagate exceptions when either write or read pipeline fails
        var readCompletion = contentReader.readCompletion().exceptionally(error -> {
            requestCompletion.completeExceptionally(error);
            return null;
        });
        var writeCompletion = responseWriter.writeCompletion().exceptionally(error -> {
            requestCompletion.completeExceptionally(error);
            return null;
        });

        // Complete when both request reading and response writing are done
        CompletableFuture.allOf(readCompletion, writeCompletion)
                .whenComplete((result, error) -> {
                    if (error == null) requestCompletion.complete(null);
                });

        // Start content reading
        contentReader.start();
    }

    private void onFailure(RequestMetricReporter metricReporter, JettyResponseWriter responseWriter,
                           Request jettyRequest, Callback callback, Throwable error, boolean developerMode) {
        // tryWriteErrorResponse is responsible for invoking the callback
        responseWriter.tryWriteErrorResponse(unwrapException(error), callback, developerMode);

        HttpServerConformanceTestHooks.markAsProcessed(unwrapException(error));

        var uriPath = jettyRequest.getHttpURI().getPath();
        if (isErrorOfType(error, EofException.class, IOException.class)) {
            log.log(Level.FINE,
                    error,
                    () -> "Network connection was unexpectedly terminated: " + uriPath);
            metricReporter.prematurelyClosed();
            jettyRequest.setAttribute(ACCESS_LOG_STATUS_CODE_OVERRIDE, 499);
        } else if (isErrorOfType(error, TimeoutException.class)) {
            log.log(Level.FINE,
                    error,
                    () -> "Request/stream was timed out by Jetty: " + uriPath);
            jettyRequest.setAttribute(ACCESS_LOG_STATUS_CODE_OVERRIDE, 408);
        } else if (!isErrorOfType(error,
                OverloadException.class, BindingNotFoundException.class, BindingSetNotFoundException.class,
                RequestException.class)) {
            log.log(Level.WARNING, "Request failed: " + uriPath, error);
        }
        metricReporter.failedResponse();
    }

    private static Throwable unwrapException(Throwable t) {
        return t instanceof CompletionException || t instanceof ExecutionException ? t.getCause() : t;
    }

    private void onSuccess(RequestMetricReporter metricReporter, Callback callback) {
        callback.succeeded();
        metricReporter.successfulResponse();
    }

    private static void shutdownConnectionGracefullyIfThresholdReached(JDiscServerConnector connector, Request jettyRequest) {
        ConnectorConfig connectorConfig = connector.connectorConfig();
        int maxRequestsPerConnection = connectorConfig.maxRequestsPerConnection();
        Connection connection = RequestUtils.getConnection(jettyRequest);
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

    private RequestHandler newRequestHandler(JDiscContext context, AccessLogEntry accessLogEntry, Request jettyRequest) {
        RequestHandler requestHandler = wrapHandlerIfFormPost(
                new FilteringRequestHandler(context.filterResolver(), jettyRequest),
                jettyRequest, context.removeRawPostBodyForWwwUrlEncodedPost());

        return new AccessLoggingRequestHandler(jettyRequest, requestHandler, accessLogEntry);
    }

    private static RequestHandler wrapHandlerIfFormPost(RequestHandler requestHandler,
                                                        Request request,
                                                        boolean removeBodyForFormPost) {
        if (!request.getMethod().equals("POST")) {
            return requestHandler;
        }
        String contentType = request.getHeaders().get(HttpHeaders.Names.CONTENT_TYPE);
        if (contentType == null) {
            return requestHandler;
        }
        if (!contentType.startsWith(APPLICATION_X_WWW_FORM_URLENCODED)) {
            return requestHandler;
        }
        return new FormPostRequestHandler(requestHandler, getCharsetName(contentType), removeBodyForFormPost);
    }

    private static String getCharsetName(String contentType) {
        var charsetAnnotation = ";charset=";
        if (!contentType.startsWith(charsetAnnotation, APPLICATION_X_WWW_FORM_URLENCODED.length())) {
            return StandardCharsets.UTF_8.name();
        }
        return contentType.substring(APPLICATION_X_WWW_FORM_URLENCODED.length() + charsetAnnotation.length());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static boolean isErrorOfType(Throwable throwable, Class<? extends Throwable>... handledTypes) {
        return Arrays.stream(handledTypes)
                .anyMatch(
                        exceptionType -> exceptionType.isInstance(unwrapException(throwable)));
    }
}
