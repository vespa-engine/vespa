// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import org.apache.http.conn.socket.ConnectionSocketFactory;

import javax.net.ssl.HostnameVerifier;
import java.net.URL;
import java.util.Collections;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakon
 */
public interface HealthEndpoint {

    static HealthEndpoint forHttp(HostName hostname, int port) {
        URL url = uncheck(() -> new URL("http", hostname.value(), port, "/state/v1/health"));
        return new HttpHealthEndpoint(url);
    }

    static HealthEndpoint forHttps(HostName hostname,
                                   int port,
                                   ServiceIdentityProvider serviceIdentityProvider,
                                   AthenzIdentity remoteIdentity) {
        URL url = uncheck(() -> new URL("https", hostname.value(), port, "/state/v1/health"));
        HostnameVerifier peerVerifier = new AthenzIdentityVerifier(Collections.singleton(remoteIdentity));
        return new HttpsHealthEndpoint(url, serviceIdentityProvider, peerVerifier);
    }

    URL getStateV1HealthUrl();
    ConnectionSocketFactory getConnectionSocketFactory();
    void registerListener(ServiceIdentityProvider.Listener listener);
    void removeListener(ServiceIdentityProvider.Listener listener);
    String toString();
}
