// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpResponse;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 * @since 2.0
 */
class AsyncResponseHandler implements AsyncHandler<Response> {

    private final CompletionHandler abortionHandler = new AbortionHandler();
    private final Request request;
    private final ResponseHandler responseHandler;
    private final Metric metric;
    private final Metric.Context metricCtx;
    private final Timer timer;
    private int statusCode;
    private String statusText;
    private ContentChannel content;
    private boolean aborted = false;
    private long requestCreationTime;
    private long transferStartTime;

    public AsyncResponseHandler(Request request, ResponseHandler responseHandler, Metric metric,
                                Metric.Context metricCtx)
    {
        this.request = request;
        this.responseHandler = responseHandler;
        this.metric = metric;
        this.metricCtx = metricCtx;
        this.timer = request.container().getInstance(Timer.class);
        metric.add(HttpClient.Metrics.NUM_REQUESTS, 1, metricCtx);
        this.requestCreationTime = timer.currentTimeMillis();
    }

    @Override
    public void onThrowable(Throwable t) {
        abort(t);
    }

    @Override
    public STATE onStatusReceived(HttpResponseStatus status) throws Exception {
        if (aborted) {
            return STATE.ABORT;
        }
        long latency = timer.currentTimeMillis() - request.creationTime(TimeUnit.MILLISECONDS);
        metric.set(HttpClient.Metrics.REQUEST_LATENCY, latency, metricCtx);
        metric.add(HttpClient.Metrics.NUM_RESPONSES, 1, metricCtx);
        statusCode = status.getStatusCode();
        statusText = status.getStatusText();

        metric.add(HttpClient.Metrics.NUM_BYTES_RECEIVED, ((Integer.SIZE)/8) + statusText.getBytes().length, metricCtx); // status code is an integer
        return STATE.CONTINUE;
    }

    @Override
    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
        this.transferStartTime = timer.currentTimeMillis();

        if (aborted) {
            return STATE.ABORT;
        }
        HttpResponse response = HttpResponse.newInstance(statusCode, statusText);

        FluentCaseInsensitiveStringsMap headerMap = headers.getHeaders();
        response.headers().addAll(headerMap);
        content = responseHandler.handleResponse(response);

        metric.add(HttpClient.Metrics.NUM_BYTES_RECEIVED, headerMap.size(), metricCtx);

        return STATE.CONTINUE;
    }

    @Override
    public STATE onBodyPartReceived(HttpResponseBodyPart part) throws Exception {
        if (aborted) {
            return STATE.ABORT;
        }
        metric.add(HttpClient.Metrics.NUM_BYTES_RECEIVED, part.getBodyPartBytes().length, metricCtx);

        content.write(part.getBodyByteBuffer(), abortionHandler);
        return STATE.CONTINUE;
    }

    @Override
    public Response onCompleted() throws Exception {
        long now = timer.currentTimeMillis();
        metric.set(HttpClient.Metrics.TRANSFER_LATENCY, now - transferStartTime, metricCtx);
        metric.set(HttpClient.Metrics.TOTAL_LATENCY, now - requestCreationTime, metricCtx);

        if (aborted) {
            return null;
        }
        content.close(abortionHandler);
        return EmptyResponse.INSTANCE;
    }

    /**
     * Returns the original request associated with this handler. Note: It is the caller's responsibility to ensure
     * that the request is properly retained and released.
     */
    public Request getRequest() {
        return request;
    }

    private void abort(Throwable t) {
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
            content.close(null);
        } catch (Exception e) {
            // ignore
        }
    }

    private class AbortionHandler implements CompletionHandler {

        @Override
        public void completed() {

        }

        @Override
        public void failed(Throwable t) {
            abort(t);
        }
    }
}
