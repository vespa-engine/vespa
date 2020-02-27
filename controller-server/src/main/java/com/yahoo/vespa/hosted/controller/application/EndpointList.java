// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import com.yahoo.vespa.hosted.controller.routing.RoutingId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


/**
 * A list of endpoints for an application.
 *
 * @author mpolden
 */
public class EndpointList extends AbstractFilteringList<Endpoint, EndpointList> {

    private EndpointList(Collection<? extends Endpoint> endpoints, boolean negate) {
        super(endpoints, negate, EndpointList::new);
        if (endpoints.stream().distinct().count() != endpoints.size()) {
            throw new IllegalArgumentException("Expected all endpoints to be distinct, got " + endpoints);
        }
    }

    private EndpointList(Collection<? extends Endpoint> endpoints) {
        this(endpoints, false);
    }

    /** Returns the primary (non-legacy) endpoint, if any */
    public Optional<Endpoint> primary() {
        return not().matching(Endpoint::legacy).asList().stream().findFirst();
    }

    /** Returns the subset of endpoints named according to given ID */
    public EndpointList named(EndpointId id) {
        return matching(endpoint -> endpoint.name().equals(id.id()));
    }

    /** Returns the subset of endpoints are either legacy or not */
    public EndpointList legacy(boolean legacy) {
        return matching(endpoint -> endpoint.legacy() == legacy);
    }

    /** Returns the subset of endpoints that require a rotation */
    public EndpointList requiresRotation() {
        return matching(Endpoint::requiresRotation);
    }

    /** Returns the subset of endpoints with given scope */
    public EndpointList scope(Endpoint.Scope scope) {
        return matching(endpoint -> endpoint.scope() == scope);
    }

    /** Returns all global endpoints for given routing ID and system provided by given routing methods */
    public static EndpointList global(RoutingId routingId, SystemName system, List<RoutingMethod> routingMethods) {
        var endpoints = new ArrayList<Endpoint>();
        for (var method : routingMethods) {
            endpoints.add(Endpoint.of(routingId.application())
                                  .named(routingId.endpointId())
                                  .on(Port.fromRoutingMethod(method))
                                  .routingMethod(method)
                                  .in(system));
            // TODO(mpolden): Remove this once all applications have migrated away from legacy endpoints
            if (method == RoutingMethod.shared) {
                endpoints.add(Endpoint.of(routingId.application())
                                      .named(routingId.endpointId())
                                      .on(Port.plain(4080))
                                      .legacy()
                                      .routingMethod(method)
                                      .in(system));
                endpoints.add(Endpoint.of(routingId.application())
                                      .named(routingId.endpointId())
                                      .on(Port.tls(4443))
                                      .legacy()
                                      .routingMethod(method)
                                      .in(system));
            }
        }
        return new EndpointList(endpoints);
    }

    public static EndpointList global(RoutingId routingId, SystemName system, RoutingMethod routingMethod) {
        return global(routingId, system, List.of(routingMethod));
    }

    public static EndpointList copyOf(Collection<Endpoint> endpoints) {
        return new EndpointList(endpoints);
    }

}
