// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.OverloadException;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.container.core.HandlerMetricContextUtil;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A request handler which assigns a worker thread to handle each request.
 * This is meant to be subclassed by handlers who do work by executing each
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
        this.executor = Objects.requireNonNull(executor);
        this.metric = (metric == null) ? new NullRequestMetric() : metric;
        this.allowAsyncResponse = allowAsyncResponse;
    }

    Metric.Context contextFor(Request request, Map<String, String> extraDimensions) {
        return HandlerMetricContextUtil.contextFor(request, extraDimensions, metric, getClass());
    }

    /**
     * Handles a request by assigning a worker thread to it.
     *
     * @throws OverloadException if thread pool has no available thread
     */
    @Override
    public final ContentChannel handleRequest(Request request, ResponseHandler responseHandler) {
        HandlerMetricContextUtil.onHandle(request, metric, getClass());
        if (request.getTimeout(TimeUnit.SECONDS) == null) {
            Duration timeout = getTimeout();
            if (timeout != null) {
                request.setTimeout(timeout.getSeconds(), TimeUnit.SECONDS);
            }
        }
        BufferedContentChannel content = new BufferedContentChannel();
        RequestTask command = new RequestTask(request, content, responseHandler);
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

    /**
     * <p>Returns the request type classification to use for requests to this handler.
     * This overrides the default classification based on request method, and can in turn
     * be overridden by setting a request type on individual responses in handleRequest
     * whenever it is invoked (i.e not for requests that are rejected early e.g due to overload).</p>
     *
     * <p>This default implementation returns empty.</p>
     *
     * @return the request type to set, or empty to not override the default classification based on request method
     */
    protected Optional<Request.RequestType> getRequestType() { return Optional.empty(); }

    public Duration getTimeout() {
        return TIMEOUT;
    }

    public Executor executor() { return executor; }

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
        log.log(Level.WARNING, "Rejected " + numRejectedRequestsSnapshot + " requests on cause of no available worker threads.");
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

    /**
     * Invoked to write an error response when the worker pool is overloaded.
     * A subclass may override this method to define a custom response.
     */
    protected void writeErrorResponseOnOverload(Request request, ResponseHandler responseHandler) {
        Response response = new Response(Response.Status.SERVICE_UNAVAILABLE);
        if (getRequestType().isPresent() && response.getRequestType() == null)
            response.setRequestType(getRequestType().get());
        ResponseDispatch.newInstance(response).dispatch(responseHandler);
    }

    private class RequestTask implements ResponseHandler, Runnable {

        final Request request;
        private final ResourceReference requestReference;
        final BufferedContentChannel content;
        final ResponseHandler responseHandler;
        private boolean hasResponded = false;

        RequestTask(Request request, BufferedContentChannel content, ResponseHandler responseHandler) {
            this.request = request;
            this.requestReference = request.refer(this);
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
            if (getRequestType().isPresent() && response.getRequestType() == null)
                response.setRequestType(getRequestType().get());
            ContentChannel cc = responseHandler.handleResponse(response);
            HandlerMetricContextUtil.onHandled(request, metric, getClass());
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
        void failOnOverload() {
            try (ResourceReference reference = requestReference) {
                incrementRejectedRequests();
                logRejectedRequests();
                writeErrorResponseOnOverload(request, responseHandler);
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
