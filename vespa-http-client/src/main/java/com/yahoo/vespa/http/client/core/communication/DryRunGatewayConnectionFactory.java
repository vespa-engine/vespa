// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.config.Endpoint;

import java.time.Clock;

/**
 * @author bratseth
 */
public class DryRunGatewayConnectionFactory implements GatewayConnectionFactory {

    private final Endpoint endpoint;
    private final Clock clock;

    public DryRunGatewayConnectionFactory(Endpoint endpoint, Clock clock) {
        this.endpoint = endpoint;
        this.clock = clock;
    }

    @Override
    public GatewayConnection newConnection() {
        return new DryRunGatewayConnection(endpoint, clock);
    }

}
