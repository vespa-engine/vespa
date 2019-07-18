// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import ai.vespa.util.http.VespaHttpClientBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.util.concurrent.TimeUnit;

/**
 * A static factory for VespaHttpClientBuilder. The main purpose of this class is to isolate references to classes targeting JDK11+.
 *
 * @author bjorncs
 */
// TODO Inline once vespa-http-client no longer targets JDK8
@SuppressWarnings("unused") // used through reflection from ApacheGatewayConnection
public class VespaTlsAwareClientBuilder {

    private VespaTlsAwareClientBuilder() {}

    @SuppressWarnings("unused") // used through reflection from ApacheGatewayConnection
    public static HttpClientBuilder createHttpClientBuilder() {
        return VespaHttpClientBuilder.create(socketFactoryRegistry -> {
            PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(
                    socketFactoryRegistry,
                    null, null, null,
                    15, TimeUnit.SECONDS);
            manager.setDefaultMaxPerRoute(1);
            manager.setMaxTotal(1);
            return manager;
        });
    }
}
