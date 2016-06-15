// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;

public class AsyncHttpClientWithBase<V extends HttpResult> implements AsyncHttpClient<V> {
    protected final AsyncHttpClient<V> client;
    private HttpRequest baseRequest = new HttpRequest();

    public AsyncHttpClientWithBase(AsyncHttpClient<V> client) {
        if (client == null) throw new IllegalArgumentException("HTTP client must be set.");
        this.client = client;
    }

    /**
     * If all your http requests have common features you want to set once, you can provide those values in a base
     * request. For instance, if you specify a host and a port using this function, all your requests will use that
     * host and port unless specified in the request you execute.
     */
    public void setHttpRequestBase(HttpRequest r) {
        this.baseRequest = (r == null ? new HttpRequest() : r.clone());
    }

    public HttpRequest getHttpRequestBase() {
        return baseRequest;
    }

    @Override
    public AsyncOperation<V> execute(HttpRequest r) {
        return client.execute(baseRequest.merge(r));
    }

    @Override
    public void close() {
        client.close();
    }
}
