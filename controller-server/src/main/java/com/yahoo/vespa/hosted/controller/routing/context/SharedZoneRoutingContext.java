// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing.context;

import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * An implementation of {@link RoutingContext} for a zone, using {@link RoutingMethod#sharedLayer4} routing.
 *
 * @author mpolden
 */
public class SharedZoneRoutingContext implements RoutingContext {

    private final ConfigServer configServer;
    private final ZoneId zone;

    public SharedZoneRoutingContext(ZoneId zone, ConfigServer configServer) {
        this.configServer = Objects.requireNonNull(configServer);
        this.zone = Objects.requireNonNull(zone);
    }

    @Override
    public void setRoutingStatus(RoutingStatus.Value value, RoutingStatus.Agent agent) {
        boolean in = value == RoutingStatus.Value.in;
        configServer.setGlobalRotationStatus(zone, in);
    }

    @Override
    public RoutingStatus routingStatus() {
        boolean in = configServer.getGlobalRotationStatus(zone);
        RoutingStatus.Value newValue = in ? RoutingStatus.Value.in : RoutingStatus.Value.out;
        return new RoutingStatus(newValue,
                                 RoutingStatus.Agent.operator,
                                 Instant.EPOCH); // API does not support time of change
    }

    @Override
    public RoutingMethod routingMethod() {
        return RoutingMethod.sharedLayer4;
    }

}
