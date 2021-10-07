// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.Objects;

/**
 * @author bratseth
 */
public class ApacheGatewayConnectionFactory implements GatewayConnectionFactory {

    private final Endpoint endpoint;
    private final FeedParams feedParams;
    private final String clusterSpecificRoute;
    private final ConnectionParams connectionParams;
    private final ApacheGatewayConnection.HttpClientFactory httpClientFactory;
    private final String clientId;
    private final Clock clock;

    public ApacheGatewayConnectionFactory(Endpoint endpoint,
                                          FeedParams feedParams,
                                          String clusterSpecificRoute,
                                          ConnectionParams connectionParams,
                                          ApacheGatewayConnection.HttpClientFactory httpClientFactory,
                                          String clientId,
                                          Clock clock) {
        this.endpoint = validate(endpoint);
        this.feedParams = feedParams;
        this.clusterSpecificRoute = clusterSpecificRoute;
        this.httpClientFactory = httpClientFactory;
        this.connectionParams = connectionParams;
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.clock = clock;
    }

    private static Endpoint validate(Endpoint endpoint) {
        try {
            InetAddress.getByName(endpoint.getHostname());
            return endpoint;
        }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unknown host: " + endpoint);
        }
    }

    @Override
    public GatewayConnection newConnection() {
        return new ApacheGatewayConnection(endpoint,
                                           feedParams,
                                           clusterSpecificRoute,
                                           connectionParams,
                                           httpClientFactory,
                                           clientId,
                                           clock);
    }

}
