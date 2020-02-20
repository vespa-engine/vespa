// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.OverloadException;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.log.LogLevel;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A request handler which assigns a worker thread to handle each request.
 * This is mean to be subclasses by handlers who does work by executing each
 * request in a separate thread.
 * <p>
 * Note that this means that subclass handlers are synchronous - the request io can
 * continue after completion of the worker thread.
 *
 * @author Simon Thoresen Hult
 */
public abstract class ThreadedRequestHandler extends AbstractRequestHandler {

    private static final Logger log = Logger.getLogger(ThreadedRequestHandler.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(Integer.parseInt(System.getProperty("ThreadedRequestHandler.timeout", "300")));
    private final Executor executor;
    protected final Metric metric;
    private final boolean allowAsyncResponse;

    private static final Object rejectedExecutionsLock = new Object();

    // GuardedBy("rejectedExecutionsLock")
    private static volatile int numRejectedRequests = 0;

    // GuardedBy("rejectedExecutionsLock")
    private static long currentFailureIntervalStartedMillis = 0L;

    protected ThreadedRequestHandler(Executor executor) {
        this(executor, new NullRequestMetric());
    }

    @Inject
    protected ThreadedRequestHandler(Executor executor, Metric metric) {
        this(executor, metric, false);
    }

    /**
     * Creates a threaded request handler
     *
     * @param executor the executor to use to execute requests
     * @param metric the metric object to which event in this are to be collected
     * @param allowAsyncResponse true to allow the response header to be created asynchronously.
     *                           If false (default), this will create an error response if the response header
     *                           is not returned by the subclass of this before handleRequest returns.
     */
    @Inject
    protected ThreadedRequestHandler(Executor executor, Metric metric, boolean allowAsyncResponse) {
        executor.getClass(); // throws NullPointerException
        this.executor = executor;
        this.metric = (metric == null) ? new NullRequestMetric() : metric;
        this.allowAsyncResponse = allowAsyncResponse;
    }

    private Metric.Context contextFor(Request request) {
        BindingMatch match = request.getBindingMatch();
        if (match == null) return null;
        UriPattern matched = match.matched();
        if (matched == null) return null;
        String name = matched.toString();
        String endpoint = request.headers().containsKey("Host") ? request.headers().get("Host").get(0) : null;

        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("handler", name);
        if (endpoint != null) {
            dimensions.put("endpoint", endpoint);
        }
        URI uri = request.getUri();
        dimensions.put("scheme", uri.getScheme());
        dimensions.put("port", Integer.toString(uri.getPort()));
        String handlerClassName = getClass().getName();
        dimensions.put("handler-name", handlerClassName);
        return this.metric.createContext(dimensions);
    }

    /**
     * Handles a request by assigning a worker thread to it.
     *
     * @throws OverloadException if thread pool has no available thread
     */
    @Override
    public final ContentChannel handleRequest(Request request, ResponseHandler responseHandler) {
        metric.add("handled.requests", 1, contextFor(request));
        if (request.getTimeout(TimeUnit.SECONDS) == null) {
            Duration timeout = getTimeout();
            if (timeout != null) {
                request.setTimeout(timeout.getSeconds(), TimeUnit.SECONDS);
            }
        }
        BufferedContentChannel content = new BufferedContentChannel();
        final RequestTask command = new RequestTask(request, content, responseHandler);
        try {
            executor.execute(command);
        } catch (RejectedExecutionException e) {
            command.failOnOverload();
            throw new OverloadException("No available threads for " + getClass().getSimpleName(), e);
        } finally {
            logRejectedRequests();
        }
        return content;
    }

    public Duration getTimeout() {
        return TIMEOUT;
    }

    private void logRejectedRequests() {
        if (numRejectedRequests == 0) {
            return;
        }
        final int numRejectedRequestsSnapshot;
        synchronized (rejectedExecutionsLock) {
            if (System.currentTimeMillis() - currentFailureIntervalStartedMillis < 1000)
                return;

            numRejectedRequestsSnapshot = numRejectedRequests;
            currentFailureIntervalStartedMillis = 0L;
            numRejectedRequests = 0;
        }
        log.log(LogLevel.WARNING, "Rejected " + numRejectedRequestsSnapshot + " requests on cause of no available worker threads.");
    }

    private void incrementRejectedRequests() {
        synchronized (rejectedExecutionsLock) {
            if (numRejectedRequests == 0) {
                currentFailureIntervalStartedMillis = System.currentTimeMillis();
            }
            numRejectedRequests += 1;
        }
    }

    protected abstract void handleRequest(Request request, BufferedContentChannel requestContent,
                                          ResponseHandler responseHandler);

    private class RequestTask implements ResponseHandler, Runnable {

        final Request request;
        private final ResourceReference requestReference;
        final BufferedContentChannel content;
        final ResponseHandler responseHandler;
        private boolean hasResponded = false;

        RequestTask(Request request, BufferedContentChannel content, ResponseHandler responseHandler) {
            this.request = request;
            this.requestReference = request.refer();
            this.content = content;
            this.responseHandler = responseHandler;
        }

        @Override
        public void run() {
            try (ResourceReference reference = requestReference) {
                processRequest();
            }
        }

        private void processRequest() {
            try {
                ThreadedRequestHandler.this.handleRequest(request, content, this);
            } catch (Exception e) {
                log.log(Level.WARNING, "Uncaught exception in " + ThreadedRequestHandler.this.getClass().getName() +
                                       ".", e);
            }
            consumeRequestContent();

            // Unless the response is generated asynchronously, it should be generated before getting here,
            // so respond with status 500 if we get here and no response has been generated.
            if ( ! allowAsyncResponse)
                respondWithErrorIfNotResponded();
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            if ( tryHasResponded()) throw new IllegalStateException("Response already handled");
            ContentChannel cc = responseHandler.handleResponse(response);
            long millis = request.timeElapsed(TimeUnit.MILLISECONDS);
            metric.set("handled.latency", millis, contextFor(request));
            return cc;
        }

        private boolean tryHasResponded() {
            synchronized (this) {
                if (hasResponded) return true;
                hasResponded = true;
            }
            return false;
        }

        private void respondWithErrorIfNotResponded() {
            if ( tryHasResponded() ) return;
            ResponseDispatch.newInstance(Response.Status.INTERNAL_SERVER_ERROR).dispatch(responseHandler);
            log.warning("This handler is not async but did not produce a response. Responding with status 500." +
                        "(If this handler is async, pass a boolean true in the super constructor to avoid this.)");
        }

        private void consumeRequestContent() {
            if (content.isConnected()) return;
            ReadableContentChannel requestContent = new ReadableContentChannel();
            try {
                content.connectTo(requestContent);
            } catch (IllegalStateException e) {
                return;
            }
            while (requestContent.read() != null) {
                // consume all ignored content
            }
        }

        /**
         * Clean up when the task can not be executed because no worker thread is available.
         */
        public void failOnOverload() {
            try (ResourceReference reference = requestReference) {
                incrementRejectedRequests();
                logRejectedRequests();
                ResponseDispatch.newInstance(Response.Status.SERVICE_UNAVAILABLE).dispatch(responseHandler);
            }
        }
    }

    private static class NullRequestMetric implements Metric {
        @Override
        public void set(String key, Number val, Context ctx) {
        }

        @Override
        public void add(String key, Number val, Context ctx) {
        }

        @Override
        public Context createContext(Map<String, ?> properties) {
            return NullFeedContext.INSTANCE;
        }

        private static class NullFeedContext implements Context {
            private static final NullFeedContext INSTANCE = new NullFeedContext();
        }
    }

}
