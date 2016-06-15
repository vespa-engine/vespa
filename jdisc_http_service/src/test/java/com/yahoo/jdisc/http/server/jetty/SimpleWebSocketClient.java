// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;

import javax.net.ssl.SSLContext;
import java.io.IOException;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
class SimpleWebSocketClient {

    private final AsyncHttpClient client;
    private final String scheme;
    private final int listenPort;

    public SimpleWebSocketClient(final TestDriver driver) {
        this(driver.newSslContext(), driver.server().getListenPort());
    }

    public SimpleWebSocketClient(final SSLContext sslContext, final int listenPort) {
        final AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setSSLContext(sslContext).build();
        this.client = new AsyncHttpClient(new GrizzlyAsyncHttpProvider(config), config);
        this.scheme = sslContext != null ? "wss" : "ws";
        this.listenPort = listenPort;
    }

    public ListenableFuture<WebSocket> executeRequest(final String path, final WebSocketListener listener)
            throws IOException {
        return client.prepareGet(scheme + "://localhost:" + listenPort + path)
                     .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(listener).build());
    }

    public boolean close() {
        client.close();
        return true;
    }
}
