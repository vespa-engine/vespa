// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.BindingSetNotFoundException;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ResponseHandler} that writes the response to Jetty ({@link org.eclipse.jetty.server.Response}).
 *
 * @author bjorncs
 */
class JettyResponseWriter implements ResponseHandler {

    private static final Logger log = Logger.getLogger(JettyResponseWriter.class.getName());

    private final Object monitor = new Object();
    private final Request jettyRequest;
    private final org.eclipse.jetty.server.Response jettyResponse;
    private final RequestMetricReporter metricReporter;
    private final CompletableFuture<Void> responseCompletion = new CompletableFuture<>();

    private ResponseContentChannel contentChannel;
    private Response jdiscResponse;

    JettyResponseWriter(Request jettyRequest, org.eclipse.jetty.server.Response jettyResponse,
                        RequestMetricReporter metricReporter) {
        this.jettyRequest = jettyRequest;
        this.jettyResponse = jettyResponse;
        this.metricReporter = metricReporter;
    }

    CompletableFuture<Void> writeCompletion() {
        return responseCompletion;
    }

    @Override
    public ContentChannel handleResponse(Response jdiscResponse) {
        Objects.requireNonNull(jdiscResponse, "Response is null");
        synchronized (monitor) {
            // If response is already committed, return a no-op content channel
            if (jettyResponse.isCommitted()) return ContentChannels.noop();
            // Multiple invocations of handleResponse() are allowed
            if (contentChannel != null) {
                // Invalidate previous content channel to prevent concurrent writes
                contentChannel.invalidated = true;
            }
            this.jdiscResponse = jdiscResponse;
            contentChannel = new ResponseContentChannel();
            return contentChannel;
        }
    }

    void tryWriteErrorResponse(Throwable error, Callback callback, boolean developerMode) {
        synchronized (monitor) {
            if (jettyResponse.isCommitted()) {
                callback.succeeded();
                return;
            }
            // Ignore failure if it does not succeed
            org.eclipse.jetty.server.Response.writeError(
                    jettyRequest, jettyResponse, Callback.from(callback::succeeded), getStatusCode(error), getReasonPhrase(error, developerMode));
        }
    }

    private static int getStatusCode(Throwable t) {
        if (t instanceof BindingNotFoundException || t instanceof BindingSetNotFoundException) {
            return HttpStatus.NOT_FOUND_404;
        } else if (t instanceof RequestException) {
            return ((RequestException) t).getResponseStatus();
        } else if (t instanceof TimeoutException) {
            // E.g stream idle timeout for HTTP/2
            return HttpStatus.SERVICE_UNAVAILABLE_503;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR_500;
        }
    }

    private static String getReasonPhrase(Throwable t, boolean developerMode) {
        if (developerMode) {
            var out = new StringWriter();
            t.printStackTrace(new PrintWriter(out));
            return out.toString();
        } else if (t.getMessage() != null) {
            return t.getMessage();
        } else {
            return t.toString();
        }
    }

    private class ResponseContentChannel implements ContentChannel {
        private record WriteTask(ByteBuffer buf, CompletionHandler handler) {}

        boolean invalidated = false;
        private final Queue<WriteTask> writeQueue = new LinkedList<>();
        private boolean canWrite = true;

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            Objects.requireNonNull(buf, "Buffer is null");
            enqueueTask(new WriteTask(buf, handler));
        }

        @Override
        public void close(CompletionHandler handler) {
            enqueueTask(new WriteTask(null, handler));
        }

        private void enqueueTask(WriteTask task) {
            synchronized (monitor) {
                writeQueue.add(task);
                tryProcessQueue();
            }
        }

        private void tryProcessQueue() {
            if (canWrite && !writeQueue.isEmpty()) {
                performWrite(writeQueue.poll());
            }
        }

        private void performWrite(WriteTask task) {
            try {
                synchronized (monitor) {
                    if (invalidated) {
                        task.handler.failed(new IllegalStateException("Content channel is invalidated"));
                        return;
                    }
                    if (!jettyResponse.isCommitted()) {
                        jettyResponse.setStatus(jdiscResponse.getStatus());
                        var jettyHeaders = jettyResponse.getHeaders();
                        jdiscResponse.headers().forEach((name, values) -> {
                            values.stream()
                                    .filter(Objects::nonNull)
                                    .forEach(value -> jettyHeaders.add(name, value));
                        });
                        if (jettyHeaders.get(HttpHeader.CONTENT_TYPE) == null) {
                            jettyHeaders.add(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
                        }
                        jettyRequest.setAttribute(ResponseMetricAggregator.requestTypeAttribute,
                                jdiscResponse.getRequestType());
                    }
                }

                canWrite = false;
                jettyResponse.write(task.buf == null, task.buf, new Callback() {
                    @Override
                    public void succeeded() {
                        if (task.buf == null) responseCompletion.complete(null);
                        completeAndFinishTask(task.handler, CompletionHandler::completed);
                        if (task.buf != null) metricReporter.successfulWrite(task.buf.remaining());
                    }

                    @Override
                    public void failed(Throwable x) {
                        responseCompletion.completeExceptionally(x);
                        completeAndFinishTask(task.handler, h -> h.failed(x));
                        metricReporter.failedWrite();
                    }
                });
            } catch (Throwable t) {
                responseCompletion.completeExceptionally(t);
                completeAndFinishTask(task.handler, h -> h.failed(t));
                metricReporter.failedWrite();
            }
        }

        private void completeAndFinishTask(CompletionHandler handler, Consumer<CompletionHandler> invocation) {
            try {
                invocation.accept(handler != null ? handler : CompletionHandlers.noop());
            } catch (Throwable ex) {
                log.log(Level.WARNING, "Failed to call completion handler", ex);
            } finally {
                synchronized (monitor) {
                    canWrite = true;
                    tryProcessQueue();
                }
            }
        }
    }
}
