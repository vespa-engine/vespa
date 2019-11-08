// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * A list of endpoints for an application.
 *
 * @author mpolden
 */
public class EndpointList extends AbstractFilteringList<Endpoint, EndpointList> {

    public static final EndpointList EMPTY = new EndpointList(List.of());

    private EndpointList(Collection<? extends Endpoint> endpoints, boolean negate) {
        super(endpoints, negate, EndpointList::new);
        if (endpoints.stream().distinct().count() != endpoints.size()) {
            throw new IllegalArgumentException("Expected all endpoints to be distinct, got " + endpoints);
        }
    }

    private EndpointList(Collection<? extends Endpoint> endpoints) {
        this(endpoints, false);
    }

    /** Returns the main endpoint, if any */
    public Optional<Endpoint> main() {
        return asList().stream().filter(Predicate.not(Endpoint::legacy)).findFirst();
    }

    /** Returns the subset of endpoints are either legacy or not */
    public EndpointList legacy(boolean legacy) {
        return matching(endpoint -> endpoint.legacy() == legacy);
    }

    /** Returns the subset of endpoints with given scope */
    public EndpointList scope(Endpoint.Scope scope) {
        return matching(endpoint -> endpoint.scope() == scope);
    }

    public static EndpointList of(Stream<Endpoint> endpoints) {
        return new EndpointList(endpoints.collect(Collectors.toUnmodifiableList()));
    }

    /** Returns the default global endpoints in given system. Default endpoints are served by a pre-provisioned routing layer */
    public static EndpointList create(ApplicationId application, EndpointId endpointId, SystemName system) {
        switch (system) {
            case cd:
            case main:
                return new EndpointList(List.of(
                        Endpoint.of(application).named(endpointId).on(Port.plain(4080)).legacy().in(system),
                        Endpoint.of(application).named(endpointId).on(Port.tls(4443)).legacy().in(system),
                        Endpoint.of(application).named(endpointId).on(Port.tls(4443)).in(system)
                ));
        }
        return EMPTY;
    }

}
