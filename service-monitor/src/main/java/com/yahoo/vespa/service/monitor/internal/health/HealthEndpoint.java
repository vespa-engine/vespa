// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;

import javax.net.ssl.HostnameVerifier;
import java.net.URL;
import java.util.Collections;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakon
 */
class HealthEndpoint {
    private final URL url;
    private final HostnameVerifier hostnameVerifier;

    static HealthEndpoint forHttps(HostName hostname, int port, AthenzIdentity remoteIdentity) {
        URL url = uncheck(() -> new URL("https", hostname.value(), port, "/state/v1/health"));
        HostnameVerifier peerVerifier = new AthenzIdentityVerifier(Collections.singleton(remoteIdentity));
        return new HealthEndpoint(url, peerVerifier);
    }

    private HealthEndpoint(URL url, HostnameVerifier hostnameVerifier) {
        this.url = url;
        this.hostnameVerifier = hostnameVerifier;
    }

    public URL getStateV1HealthUrl() {
        return url;
    }

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }
}
