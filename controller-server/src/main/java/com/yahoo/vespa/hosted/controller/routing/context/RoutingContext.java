// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing.context;

import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;

/**
 * Top-level interface for a routing context, which provides control of routing status for a deployment or zone.
 *
 * @author mpolden
 */
public interface RoutingContext {

    /** Change the routing status for the zone or deployment represented by this context */
    void setRoutingStatus(RoutingStatus.Value value, RoutingStatus.Agent agent);

    /** Get the current routing status for the zone or deployment represented by this context */
    RoutingStatus routingStatus();

    /** Routing method used in this context */
    RoutingMethod routingMethod();

}
