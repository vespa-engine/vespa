// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.base.Preconditions;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.service.CurrentContainer;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;

import javax.annotation.concurrent.GuardedBy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.server.jetty.CompletionHandlerUtils.NOOP_COMPLETION_HANDLER;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.17.0
 */
class WebSocketRequestDispatch extends WebSocketAdapter {

    private final static Logger log = Logger.getLogger(WebSocketRequestDispatch.class.getName());
    private final static ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final AtomicReference<Object> responseRef = new AtomicReference<>();
    private final CurrentContainer container;
    private final Executor janitor;
    private final RequestHandler requestHandler;
    private final Metric metric;
    private final Metric.Context metricCtx;
    private final Object lock = new Object();
    private final CompletionHandler failureHandlingCompletionHandler = new CompletionHandler() {
        @Override
        public void completed() {
        }

        @Override
        public void failed(final Throwable t) {
            synchronized (lock) {
                fail_holdingLock(t);
            }
        }
    };

    @GuardedBy("lock")
    private final Deque<ResponseContentPart> responseContentQueue = new ArrayDeque<>();
    @GuardedBy("lock")
    private ContentChannel requestContent;
    @GuardedBy("lock")
    private Throwable failure;
    @GuardedBy("lock")
    private boolean writingResponse = false;
    @GuardedBy("lock")
    private boolean connected;

    public WebSocketRequestDispatch(
            final CurrentContainer container,
            final Executor janitor,
            final Metric metric,
            final Metric.Context metricCtx) {
        Objects.requireNonNull(janitor, "janitor");
        Objects.requireNonNull(metric, "metric");
        this.container = container;
        this.requestHandler = new AbstractRequestHandler() {
            @Override
            public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
                return request.connect(handler);
            }
        };
        this.janitor = janitor;
        this.metric = metric;
        this.metricCtx = metricCtx;
    }

    @SuppressWarnings("try")
    public WebSocketRequestDispatch dispatch(final ServletUpgradeRequest servletRequest,
                                             final ServletUpgradeResponse servletResponse) {
        final HttpRequest jdiscRequest = WebSocketRequestFactory.newJDiscRequest(container, servletRequest);
        try (final ResourceReference ref = References.fromResource(jdiscRequest)) {
            WebSocketRequestFactory.copyHeaders(servletRequest, jdiscRequest);
            dispatchRequestWithoutThrowing(jdiscRequest);
        }
        final Response jdiscResponse = (Response)responseRef.getAndSet(new Object());
        if (jdiscResponse != null) {
            log.finer("Applying sync " + jdiscResponse.getStatus() + " response to websocket response.");
            servletResponse.setStatus(jdiscResponse.getStatus());
            WebSocketRequestFactory.copyHeaders(jdiscResponse, servletResponse);
        }
        return this;
    }

    @Override
    public void onWebSocketBinary(final byte[] arr, final int off, final int len) {
        writeRequestContentWithoutThrowing(ByteBuffer.wrap(arr, off, len));
    }

    @Override
    public void onWebSocketText(final String message) {
        writeRequestContentWithoutThrowing(StandardCharsets.UTF_8.encode(message));
    }

    @Override
    public void onWebSocketConnect(final Session session) {
        super.onWebSocketConnect(session);
        synchronized (lock) {
            connected = true;
            if (writingResponse) {
                return;
            }
            writingResponse = true;
        }
        writeNextResponseContent();
    }

    /**
     * This is ALWAYS called.
     * ...if the remote side closes the connection
     * ...if we c*ck up ourselves and throw an exception out of onWebSocketBinary() or onWebSocketText(),
     * Jetty calls Session.close on our behalf (later followed by a call to onWebSocketError)
     *
     * TODO: Test below
     * ...and also whenever we call Session.close() ourselves??
     *
     * @param statusCode The {@link StatusCode} of the close.
     * @param reason     The reason text for the close.
     */
    @Override
    public void onWebSocketClose(final int statusCode, final String reason) {
        super.onWebSocketClose(statusCode, reason);
        final ContentChannel requestContentChannel;
        synchronized (lock) {
            Preconditions.checkState(requestContent != null || failure != null,
                    "requestContent should be non-null if we haven't had a failure");
            if (requestContent == null) {
                return;
            }
            if (failure != null) {
                // Request content will be closed as a result of the failure handling.
                return;
            }
            requestContentChannel = requestContent;
            requestContent = null;
        }
        try {
            requestContentChannel.close(failureHandlingCompletionHandler);
        } catch (final Throwable t) {
            fail(t);
        }
    }

    /**
     * <p>No need to call Session.close() here, that has been done or will be done by Jetty.</p>
     *
     * @param t The cause of the error.
     */
    @Override
    public void onWebSocketError(final Throwable t) {
        fail(t);
    }

    private void dispatchRequestWithoutThrowing(final Request request) {
        final ContentChannel returnedContentChannel;
        try {
            returnedContentChannel = requestHandler.handleRequest(request, new GatedResponseHandler());
        } catch (final Throwable t) {
            fail(t);
            throw new IllegalStateException(t);
        }
        synchronized (lock) {
            Preconditions.checkState(requestContent == null, "requestContent should be null");
            if (failure != null) {
                // This means that request.connect() caused a synchronous failure. in this case
                // the cleanup happened before requestContent was assigned, so we must clean it explicitly here
                closeLater(returnedContentChannel);
                throw new IllegalStateException(failure);
            }
            requestContent = returnedContentChannel;
        }
    }

    private void writeRequestContentWithoutThrowing(final ByteBuffer buf) {
        int bytes_received = buf.remaining();
        metric.set(JettyHttpServer.Metrics.NUM_BYTES_RECEIVED, bytes_received, metricCtx);
        metric.set(JettyHttpServer.Metrics.MANHATTAN_NUM_BYTES_RECEIVED, bytes_received, metricCtx);
        final ContentChannel requestContentChannel;
        synchronized (lock) {
            Preconditions.checkState(requestContent != null, "requestContent should be non-null");
            if (failure != null) {
                return;
            }
            requestContentChannel = requestContent;
        }
        try {
            requestContentChannel.write(buf, failureHandlingCompletionHandler);
        } catch (final Throwable t) {
            fail(t);
        }
    }

    private void fail(final Throwable t) {
        synchronized (lock) {
            fail_holdingLock(t);
        }
    }

    private void tryWriteResponseContent(final ByteBuffer buf, final CompletionHandler handler) {
        synchronized (lock) {
            if (failure != null) {
                failLater(handler, failure);
                return;
            }
            responseContentQueue.addLast(new ResponseContentPart(buf, handler));
            if (writingResponse) {
                return;
            }
            writingResponse = true;
        }
        writeNextResponseContent();
    }

    private void writeNextResponseContent() {
        while (true) {
            final ResponseContentPart part;
            synchronized (lock) {
                if (!connected) {
                    // We expect a later invocation of onWebSocketConnect(). That will invoke this method again.
                    writingResponse = false;
                    return;
                }
                if (responseContentQueue.isEmpty()) {
                    writingResponse = false;
                    return; // application will call later
                }
                part = responseContentQueue.poll();
            }
            if (part.handler != null) {
                try {
                    part.handler.completed();
                } catch (final Throwable t) {
                    fail(t);
                    return;
                }
            }
            final boolean isClosePart = part.buf == null;
            if (isClosePart) {
                return;
            }
            try {
                getRemote().sendBytesByFuture(part.buf);
            } catch (final Throwable t) {
                fail(t);
            }
        }
    }

    private void fail_holdingLock(final Throwable failure) {
        if (this.failure != null) {
            return;
        }
        this.failure = failure;
        if (requestContent != null) {
            closeLater(requestContent);
        }
        requestContent = null;
        for (ResponseContentPart part = responseContentQueue.poll(); part != null; part = responseContentQueue.poll()) {
            failLater(part.handler, failure);
        }
        janitor.execute(() -> {
            try {
                getSession().close(StatusCode.SERVER_ERROR, failure.toString());
            } catch (final Throwable ignored) {
            }
        });
    }

    private void closeLater(final ContentChannel content) {
        janitor.execute(() -> {
            try {
                content.close(NOOP_COMPLETION_HANDLER);
            } catch (final Throwable ignored) {
            }
        });
    }

    private void failLater(final CompletionHandler handler, final Throwable failure) {
        if (handler == null) {
            return;
        }

        final Throwable failureWithStack = new IllegalStateException(failure);
        janitor.execute(() -> {
            try {
                handler.failed(failureWithStack);
            } catch (final Throwable t) {
                log.log(Level.WARNING, "Failure handling of " + failure +
                        " in application threw an exception.", t);
            }
        });
    }

    private class GatedResponseHandler implements ResponseHandler {

        @Override
        public ContentChannel handleResponse(final Response response) {
            synchronized (lock) {
                if (failure != null) {
                    return new FailedResponseContent(new IllegalStateException(failure));
                }
            }
            final boolean firstToSetResponse = responseRef.compareAndSet(null, response);
            if (!firstToSetResponse) {
                log.finer("Ignoring async " + response.getStatus() + " response because sync websocket response has " +
                        "already been returned to client.");
                // TODO(bakksjo): The message above is not necessarily correct. Getting here does not necessarily
                // mean that a sync response has been returned to the client. It may just mean that dispatch() is
                // finished, and the request handler's handleRequest() has been run. That does not mean that the
                // request handler actually produced a sync response. If a response is produced asynchronously, we
                // may get here and ignore that response. TODO: Analyze wire traffic. Maybe Jetty produces a response
                // after dispatch(), even if we don't do it in our code. Besides, is the status code used for anything
                // by the client anyway? Is it even available in client WebSocket implementations?
            }
            return new GatedResponseContent();
        }
    }

    private class GatedResponseContent implements ContentChannel {

        @Override
        public void write(final ByteBuffer raw, final CompletionHandler handler) {
            final ByteBuffer buf = raw != null ? raw : EMPTY_BUFFER;
            int bytesSent = buf.remaining();
            metric.set(JettyHttpServer.Metrics.NUM_BYTES_SENT, bytesSent, metricCtx);
            metric.set(JettyHttpServer.Metrics.MANHATTAN_NUM_BYTES_SENT, bytesSent, metricCtx);
            tryWriteResponseContent(buf, new MetricCompletionHandler(handler));
        }

        @Override
        public void close(final CompletionHandler handler) {
            // The only reason to let this synthetic 'part' go into the queue is to have the completion handler
            // for close() invoked in order (after the completion handlers for enqueued parts.
            tryWriteResponseContent(null, new MetricCompletionHandler(handler));
        }
    }

    private class FailedResponseContent implements ContentChannel {

        final Throwable failure;

        FailedResponseContent(final Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void write(final ByteBuffer buf, final CompletionHandler handler) {
            failLater(new MetricCompletionHandler(handler), failure);
        }

        @Override
        public void close(final CompletionHandler handler) {
            failLater(new MetricCompletionHandler(handler), failure);
        }
    }

    private class MetricCompletionHandler implements CompletionHandler {

        final CompletionHandler delegate;

        MetricCompletionHandler(CompletionHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void completed() {
            metric.add(JettyHttpServer.Metrics.NUM_SUCCESSFUL_WRITES, 1, metricCtx);
            if (delegate != null)
                delegate.completed();
        }

        @Override
        public void failed(Throwable t) {
            metric.add(JettyHttpServer.Metrics.NUM_FAILED_WRITES, 1, metricCtx);
            if (delegate != null)
                delegate.failed(t);
        }
    }

    private static class ResponseContentPart {

        final ByteBuffer buf;
        final CompletionHandler handler;

        ResponseContentPart(final ByteBuffer buf, final CompletionHandler handler) {
            this.buf = buf;
            this.handler = handler;
        }
    }
}
