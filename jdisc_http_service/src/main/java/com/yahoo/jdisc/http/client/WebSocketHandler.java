// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpResponse;

import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;

/**
 * @author <a href="mailto:vikasp@yahoo-inc.com">Vikas Panwar</a>
 */
class WebSocketHandler implements WebSocketByteListener {

    private final CompletionHandler abortOnFailure = new AbortOnFailure();
    private final Metric metric;
    private final Metric.Context metricCtx;
    private final Request request;
    private final ResponseHandler responseHandler;
    private ContentChannel content;
    private boolean aborted = false;

    public WebSocketHandler(Request request, ResponseHandler responseHandler, Metric metric, Metric.Context ctx) {
        this.request = request;
        this.responseHandler = responseHandler;
        this.metric = metric;
        this.metricCtx = ctx;
    }

    @Override
    public synchronized void onOpen(WebSocket webSocket) {
        // ignore, open on first fragment to allow failures to propagate
    }

    @Override
    public synchronized void onMessage(byte[] bytes) {
        if (aborted) {
            return;
        }
        if (content == null) {
            dispatchResponse();
        }
        // need to copy the bytes into a new buffer since there is no declared ownership of the array
        content.write((ByteBuffer)ByteBuffer.allocate(bytes.length).put(bytes).flip(), abortOnFailure);
    }

    @Override
    public synchronized void onFragment(byte[] bytes, boolean last) {
        // ignore, write messages instead
    }

    @Override
    public synchronized void onClose(WebSocket webSocket) {
        if (aborted) {
            return;
        }
        if (content == null) {
            dispatchResponse();
        }
        content.close(abortOnFailure);
    }

    @Override
    public synchronized void onError(Throwable t) {
        abort(t);
    }

    private void dispatchResponse() {
        content = responseHandler.handleResponse(HttpResponse.newInstance(Response.Status.OK));
    }

    private synchronized void abort(Throwable t) {
        if (aborted) {
            return;
        }
        aborted = true;
        updateErrorMetric(t);
        if (content == null) {
            dispatchErrorResponse(t);
        }
        if (content != null) {
            terminateContent();
        }
    }

    private void updateErrorMetric(Throwable t) {
        try {
            if (t instanceof ConnectException) {
                metric.add(HttpClient.Metrics.CONNECTION_EXCEPTIONS, 1, metricCtx);
            } else if (t instanceof TimeoutException) {
                metric.add(HttpClient.Metrics.TIMEOUT_EXCEPTIONS, 1, metricCtx);
            } else {
                metric.add(HttpClient.Metrics.OTHER_EXCEPTIONS, 1, metricCtx);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void dispatchErrorResponse(Throwable t) {
        int status;
        if (t instanceof ConnectException) {
            status = com.yahoo.jdisc.Response.Status.SERVICE_UNAVAILABLE;
        } else if (t instanceof TimeoutException) {
            status = com.yahoo.jdisc.Response.Status.REQUEST_TIMEOUT;
        } else {
            status = com.yahoo.jdisc.Response.Status.BAD_REQUEST;
        }
        try {
            content = responseHandler.handleResponse(HttpResponse.newError(request, status, t));
        } catch (Exception e) {
            // ignore
        }
    }

    private void terminateContent() {
        try {
            content.close(IgnoreFailure.INSTANCE);
        } catch (Exception e) {
            // ignore
        }
    }

    private class AbortOnFailure implements CompletionHandler {

        @Override
        public void completed() {

        }

        @Override
        public void failed(Throwable t) {
            abort(t);
        }
    }

    private static class IgnoreFailure implements CompletionHandler {

        final static IgnoreFailure INSTANCE = new IgnoreFailure();

        @Override
        public void completed() {

        }

        @Override
        public void failed(Throwable t) {

        }
    }
}