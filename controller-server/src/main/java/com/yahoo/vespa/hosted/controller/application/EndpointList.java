// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.config.provision.zone.ZoneId;

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

    /** Returns the primary (non-legacy) endpoint, if any */
    public Optional<Endpoint> primary() {
        return not().legacy().asList().stream().findFirst();
    }

    /** Returns the subset of endpoints named according to given ID */
    public EndpointList named(EndpointId id) {
        return matching(endpoint -> endpoint.name().equals(id.id()));
    }

    /** Returns the subset of endpoints which target all of the given zones */
    public EndpointList targets(List<ZoneId> zones) {
        return matching(endpoint -> endpoint.zones().containsAll(zones));
    }

    /** Returns the subset of endpoints which target the given zones */
    public EndpointList targets(ZoneId zone) {
        return targets(List.of(zone));
    }

    /** Returns the subset of endpoints that are considered legacy */
    public EndpointList legacy() {
        return matching(Endpoint::legacy);
    }

    /** Returns the subset of endpoints that require a rotation */
    public EndpointList requiresRotation() {
        return matching(Endpoint::requiresRotation);
    }

    /** Returns the subset of endpoints with given scope */
    public EndpointList scope(Endpoint.Scope scope) {
        return matching(endpoint -> endpoint.scope() == scope);
    }

    public static EndpointList copyOf(Collection<Endpoint> endpoints) {
        return new EndpointList(endpoints, false);
    }

}
