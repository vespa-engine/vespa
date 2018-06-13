// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.net.URL;

/**
 * @author hakon
 */
public class HttpsHealthEndpoint implements HealthEndpoint {
    private final URL url;
    private final HostnameVerifier hostnameVerifier;
    private final ServiceIdentityProvider serviceIdentityProvider;

    HttpsHealthEndpoint(URL url,
                        ServiceIdentityProvider serviceIdentityProvider,
                        HostnameVerifier hostnameVerifier) {
        this.url = url;
        this.serviceIdentityProvider = serviceIdentityProvider;
        this.hostnameVerifier = hostnameVerifier;
    }

    @Override
    public URL getStateV1HealthUrl() {
        return url;
    }

    @Override
    public ConnectionSocketFactory getConnectionSocketFactory() {
        SSLContext sslContext = serviceIdentityProvider.getIdentitySslContext();
        return new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
    }

    @Override
    public void registerListener(ServiceIdentityProvider.Listener listener) {
        serviceIdentityProvider.addIdentityListener(listener);
    }

    @Override
    public void removeListener(ServiceIdentityProvider.Listener listener) {
        serviceIdentityProvider.removeIdentityListener(listener);
    }

    @Override
    public String toString() {
        return url.toString();
    }
}
