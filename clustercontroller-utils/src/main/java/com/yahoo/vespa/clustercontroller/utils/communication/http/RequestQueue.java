// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncCallback;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;

import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * Utility class to schedule HTTP requests and keeping a maximum amount of them pending at a time.
 */
public class RequestQueue<V extends HttpResult> {
    private static final Logger log = Logger.getLogger(RequestQueue.class.getName());
    private final AsyncHttpClient<V> httpClient;
    private final LinkedList<Request<V>> requestQueue = new LinkedList<>();
    private final int maxPendingRequests;
    private int pendingRequests = 0;

    public RequestQueue(AsyncHttpClient<V> httpClient, int maxPendingRequests) {
        this.httpClient = httpClient;
        this.maxPendingRequests = maxPendingRequests;
    }

    public boolean empty() {
        synchronized (requestQueue) {
            return (requestQueue.isEmpty() && pendingRequests == 0);
        }
    }

    public void waitUntilEmpty() throws InterruptedException {
        synchronized (requestQueue) {
            while (!empty()) {
                requestQueue.wait();
            }
        }
    }

    public void schedule(HttpRequest request, AsyncCallback<V> callback) {
        log.fine("Scheduling " + request + " call");
        synchronized (requestQueue) {
            requestQueue.addLast(new Request<>(request, callback));
            sendMore();
        }
    }

    private void sendMore() {
        while (pendingRequests < maxPendingRequests && !requestQueue.isEmpty()) {
            Request<V> call = requestQueue.removeFirst();
            log.fine("Sending " + call.getRequest() + ".");
            ++pendingRequests;
            AsyncOperation<V> op = httpClient.execute(call.getRequest());
            op.register(call);
        }
    }

    private class Request<V extends HttpResult> implements AsyncCallback<V> {
        private final HttpRequest request;
        private final AsyncCallback<V> callback;

        Request(HttpRequest request, AsyncCallback<V> callback) {
            this.request = request;
            this.callback = callback;
        }

        public HttpRequest getRequest() { return request; }

        @Override
        public void done(AsyncOperation<V> op) {
            if (op.isSuccess()) {
                log.fine("Operation " + op.getName() + " completed successfully");
            } else {
                log.fine("Operation " + op.getName() + " failed: " + op.getCause());
            }
            synchronized (requestQueue) {
                --pendingRequests;
            }
            callback.done(op);
            synchronized (requestQueue) {
                requestQueue.notifyAll();
                sendMore();
            }
        }
    }
}
