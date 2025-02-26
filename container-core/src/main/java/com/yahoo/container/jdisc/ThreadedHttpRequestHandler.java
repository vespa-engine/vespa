// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.handler.UnsafeContentInputStream;
import com.yahoo.jdisc.http.server.jetty.AccessLoggingRequestHandler;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple HTTP request handler, using the {@link HttpRequest} and
 * {@link HttpResponse} classes. Users need to override the
 * {@link #handle(HttpRequest)} method in this class and the
 * {@link HttpResponse#render(java.io.OutputStream)} method.
 *
 * @author hmusum
 * @author Steinar Knutsen
 * @author bratseth
 */
public abstract class ThreadedHttpRequestHandler extends ThreadedRequestHandler implements HttpRequestHandler  {

    public static final String CONTENT_TYPE = "Content-Type";
    private static final String RENDERING_ERRORS = "rendering_errors";
    private static final String UNHANDLED_EXCEPTIONS_METRIC = ContainerMetrics.JDISC_HTTP_HANDLER_UNHANDLED_EXCEPTIONS.baseName();

    /** Logger for subclasses */
    protected final Logger log;

    public ThreadedHttpRequestHandler(Executor executor) {
        this(executor, null);
    }

    // TODO: deprecate this and other overloads taking executor as argument
    public ThreadedHttpRequestHandler(Executor executor, Metric metric) {
        this(executor, metric, false);
    }

    @Inject
    public ThreadedHttpRequestHandler(ContainerThreadPool pool, Metric metric) {
        this(pool.executor(), metric, false);
    }

    // TODO: deprecate this and the Context class. The context component set up in the model does not get a dedicated thread pool.
    public ThreadedHttpRequestHandler(Context context) {
        this(context.executor, context.metric);
    }

    public ThreadedHttpRequestHandler(Executor executor, Metric metric, boolean allowAsyncResponse) {
        super(executor, metric, allowAsyncResponse);
        log = Logger.getLogger(this.getClass().getName());
    }

    /**
     * Override this to implement a synchronous style handler.
     *
     * @param request incoming HTTP request
     * @return a valid HTTP response for presentation to the user
     */
    public abstract HttpResponse handle(HttpRequest request);

    /**
     * Override this rather than handle(request) to be able to write to the channel before returning from this method.
     * This default implementation calls handle(request)
     */
    public HttpResponse handle(HttpRequest request, ContentChannel channel) {
        return handle(request);
    }

    @Override
    public final void handleRequest(Request request, BufferedContentChannel requestContent, ResponseHandler responseHandler) {
        log.log(Level.FINE, () -> "In " + this.getClass() + ".handleRequest()");
        HttpRequest httpRequest = new HttpRequest(asHttpRequest(request), new UnsafeContentInputStream(requestContent.toReadable()));
        LazyContentChannel channel = null;
        try {
            channel = new LazyContentChannel(httpRequest, responseHandler, metric, log);
            HttpResponse httpResponse = handle(httpRequest, channel);
            channel.setHttpResponse(httpResponse); // may or may not have already been done
            render(httpRequest, httpResponse, channel, httpRequest.creationTime(TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            metric.add(UNHANDLED_EXCEPTIONS_METRIC, 1L, contextFor(request, Map.of("exception", e.getClass().getSimpleName())));
            metric.add(RENDERING_ERRORS, 1, null);
            log.log(Level.SEVERE, "Uncaught exception handling request", e);
            if (channel != null) {
                channel.setHttpResponse(null);
                channel.close(null);
            }
        } catch (Error e) {
            // To make absolutely sure the VM exits on Error.
            com.yahoo.protect.Process.logAndDie("java.lang.Error handling request", e);
        }
    }

    /** Render and return whether the channel was closed */
    private void render(HttpRequest request, HttpResponse httpResponse, LazyContentChannel channel, long startTime) {
        LoggingCompletionHandler logOnCompletion = null;
        ContentChannelOutputStream output = null;
        try {
            output = httpResponse.maxPendingBytes() > 0 ? new MaxPendingContentChannelOutputStream(channel, httpResponse.maxPendingBytes())
                                                        : new ContentChannelOutputStream(channel);
            logOnCompletion = createLoggingCompletionHandler(startTime, System.currentTimeMillis(),
                                                             httpResponse, request, output);

            addResponseHeaders(httpResponse, startTime);

            if (httpResponse instanceof AsyncHttpResponse) {
                ((AsyncHttpResponse) httpResponse).render(output, channel, logOnCompletion);
            } else {
                httpResponse.render(output);
                if (logOnCompletion != null)
                    logOnCompletion.markCommitStart();
                output.flush();
            }
        }
        catch (IOException e) {
            metric.add(RENDERING_ERRORS, 1, null);
            long time = System.currentTimeMillis() - startTime;
            log.log(time < 900 ? Level.INFO : Level.WARNING,
                    "IO error while responding to  [" + request.getUri() + "] (total time " + time + " ms) ", e);
            try { output.flush(); } catch (Exception ignored) { }
        } finally {
            if (channel != null && ! (httpResponse instanceof AsyncHttpResponse)) {
                channel.close(logOnCompletion);
            }
        }
    }

    /**
     * A content channel which will return the header and create the proper channel the first time content data needs
     * to be written to it.
     */
    public static class LazyContentChannel implements ContentChannel {

        /** The lazily created channel this wraps */
        private ContentChannel channel = null;
        private boolean closed = false;

        // Fields needed to lazily create or close the channel */
        private final HttpRequest httpRequest;
        private HttpResponse httpResponse;
        private final ResponseHandler responseHandler;
        private final Metric metric;
        private final Logger log;

        public LazyContentChannel(HttpRequest httpRequest, ResponseHandler responseHandler, Metric metric, Logger log) {
            this.httpRequest = httpRequest;
            this.responseHandler = responseHandler;
            this.metric = metric;
            this.log = log;
        }

        /** This must be called before writing to this */
        public void setHttpResponse(HttpResponse httpResponse) {
            if (httpResponse == null && this.httpResponse == null) // the handler in use returned a null response
                httpResponse = new EmptyResponse(500);
            this.httpResponse = httpResponse;
        }

        @Override
        public void write(ByteBuffer byteBuffer, CompletionHandler completionHandler) {
            if (channel == null)
                channel = handleResponse();
            channel.write(byteBuffer, completionHandler);
        }

        @Override
        public void close(CompletionHandler completionHandler) {
            if ( closed ) return;
            try { httpRequest.getData().close(); } catch (IOException e) {}
            if (channel == null)
                channel = handleResponse();
            try {
                channel.close(completionHandler);
            }
            catch (IllegalStateException e) {
                // Ignore: Known to be thrown when the other party closes
            }
            closed = true;
        }

        private ContentChannel handleResponse() {
            try {
                if (httpResponse == null)
                    throw new NullPointerException("Writing to a lazy content channel without calling setHttpResponse first");
                httpResponse.complete();
                return responseHandler.handleResponse(httpResponse.getJdiscResponse());
            } catch (Exception e) {
                metric.add(RENDERING_ERRORS, 1, null);
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "Error writing response to client - connection probably terminated " +
                                            "from client side.", e);
                }
                return new DevNullChannel(); // Ignore further operations on this
            }
        }

        private static class DevNullChannel implements ContentChannel {

            @Override
            public void write(ByteBuffer byteBuffer, CompletionHandler completionHandler) { }

            @Override
            public void close(CompletionHandler completionHandler) { }

        }

    }

    private void addResponseHeaders(HttpResponse httpResponse, long startTime) {
        if ( ! httpResponse.headers().containsKey(CONTENT_TYPE) && httpResponse.getContentType() != null) {
            StringBuilder s = new StringBuilder(httpResponse.getContentType());
            if (httpResponse.getCharacterEncoding() != null) {
                s.append("; charset=").append(httpResponse.getCharacterEncoding());
            }
            httpResponse.headers().put(CONTENT_TYPE, s.toString());
        }
        addDateHeader(httpResponse, startTime);
    }

    // Can be overridden to add Date HTTP response header. See bugs 3729021 and 6160137.
    protected void addDateHeader(HttpResponse httpResponse, long startTime) {
    }

    /**
     * Override this to implement custom access logging.
     *
     * @param startTime execution start
     * @param renderStartTime start of output rendering
     * @param response the response which the log entry regards
     * @param httpRequest the incoming HTTP request
     * @param rendererWiring the stream the rendered response is written to, used for
     *                       fetching length of rendered response
     */
    protected LoggingCompletionHandler createLoggingCompletionHandler(long startTime,
                                                                      long renderStartTime,
                                                                      HttpResponse response,
                                                                      HttpRequest httpRequest,
                                                                      ContentChannelOutputStream rendererWiring) {
        return new ResponseLogger(httpRequest, response);
    }

    protected com.yahoo.jdisc.http.HttpRequest asHttpRequest(Request request) {
        if (!(request instanceof com.yahoo.jdisc.http.HttpRequest)) {
            throw new IllegalArgumentException("Expected " + com.yahoo.jdisc.http.HttpRequest.class.getName() +
                                               ", got " + request.getClass().getName());
        }
        return (com.yahoo.jdisc.http.HttpRequest) request;
    }

    public static Context testContext() {
        return new Context(Runnable::run, null);
    }

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


    private class ResponseLogger implements LoggingCompletionHandler {

        private final com.yahoo.jdisc.http.HttpRequest jdiscRequest;
        private final HttpResponse httpResponse;

        ResponseLogger(HttpRequest httpRequest, HttpResponse httpResponse) {
            this.jdiscRequest = httpRequest.getJDiscRequest();
            this.httpResponse = httpResponse;
        }

        @Override
        public void markCommitStart() { }

        @Override
        public void completed() {
            writeToAccessLog();
        }

        @Override
        public void failed(Throwable throwable) {
            writeToAccessLog();
            log.log(Level.FINE, () -> "Got exception when writing to client: " + Exceptions.toMessageString(throwable));
        }

        private void writeToAccessLog() {
            Optional<AccessLogEntry> jdiscReqAccessLogEntry = AccessLoggingRequestHandler.getAccessLogEntry(jdiscRequest);
            AccessLogEntry entry;
            if (jdiscReqAccessLogEntry.isPresent()) {
                entry = jdiscReqAccessLogEntry.get();
                httpResponse.populateAccessLogEntry(entry);
            }
        }
    }

}
