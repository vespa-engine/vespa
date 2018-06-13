// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;

import java.net.URL;

/**
 * @author hakon
 */
class HttpHealthEndpoint implements HealthEndpoint {
    private final URL url;
    private final ConnectionSocketFactory socketFactory;

    HttpHealthEndpoint(URL url) {
        this.url = url;
        this.socketFactory = PlainConnectionSocketFactory.getSocketFactory();
    }

    @Override
    public URL getStateV1HealthUrl() {
        return url;
    }

    @Override
    public ConnectionSocketFactory getConnectionSocketFactory() {
        return socketFactory;
    }

    @Override
    public void registerListener(ServiceIdentityProvider.Listener listener) {
    }

    @Override
    public void removeListener(ServiceIdentityProvider.Listener listener) {
    }

    @Override
    public String description() {
        return url.toString();
    }
}
