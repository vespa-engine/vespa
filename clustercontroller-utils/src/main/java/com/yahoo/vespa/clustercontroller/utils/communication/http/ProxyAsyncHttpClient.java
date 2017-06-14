// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;

public class ProxyAsyncHttpClient<V extends HttpResult> extends AsyncHttpClientWithBase<V> {
    private final String proxyHost;
    private final int proxyPort;

    public ProxyAsyncHttpClient(AsyncHttpClient<V> client, String proxyHost, int proxyPort) {
        super(client);
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    @Override
    public AsyncOperation<V> execute(HttpRequest r) {
        r = getHttpRequestBase().merge(r);
        if (r.getHost() == null || r.getPath() == null) {
            throw new IllegalStateException("Host and path must be set prior to being able to proxy an HTTP request");
        }
        StringBuilder path = new StringBuilder().append(r.getHost());
        if (r.getPort() != 0) path.append(':').append(r.getPort());
        if (r.getPath().isEmpty() || r.getPath().charAt(0) != '/') path.append('/');
        path.append(r.getPath());
        return client.execute(r.setHost(proxyHost).setPort(proxyPort).setPath(path.toString()));
    }
}
