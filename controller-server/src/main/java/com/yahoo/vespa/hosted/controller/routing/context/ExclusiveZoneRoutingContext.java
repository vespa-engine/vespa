// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing.context;

import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicies;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;

import java.util.Objects;

/**
 * An implementation of {@link RoutingContext} for a zone using {@link RoutingMethod#exclusive} routing.
 *
 * @author mpolden
 */
public class ExclusiveZoneRoutingContext implements RoutingContext {

    private final RoutingPolicies policies;
    private final ZoneId zone;

    public ExclusiveZoneRoutingContext(ZoneId zone, RoutingPolicies policies) {
        this.policies = Objects.requireNonNull(policies);
        this.zone = Objects.requireNonNull(zone);
    }

    @Override
    public void setRoutingStatus(RoutingStatus.Value value, RoutingStatus.Agent agent) {
        policies.setRoutingStatus(zone, value);
    }

    @Override
    public RoutingStatus routingStatus() {
        return policies.read(zone).routingStatus();
    }

    @Override
    public RoutingMethod routingMethod() {
        return RoutingMethod.exclusive;
    }

}
