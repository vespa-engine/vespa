// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;

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
public class EndpointList {

    public static final EndpointList EMPTY = new EndpointList(List.of());

    private final List<Endpoint> endpoints;

    private EndpointList(List<Endpoint> endpoints) {
        long mainEndpoints = endpoints.stream()
                                      .filter(endpoint -> endpoint.scope() == Endpoint.Scope.global)
                                      .filter(Predicate.not(Endpoint::directRouting))
                                      .filter(Predicate.not(Endpoint::legacy)).count();
        if (mainEndpoints > 1) {
            throw new IllegalArgumentException("Can have only 1 non-legacy global endpoint, got " + endpoints);
        }
        if (endpoints.stream().distinct().count() != endpoints.size()) {
            throw new IllegalArgumentException("Expected all endpoints to be distinct, got " + endpoints);
        }
        this.endpoints = List.copyOf(endpoints);
    }

    public List<Endpoint> asList() {
        return endpoints;
    }

    /** Returns the main endpoint, if any */
    public Optional<Endpoint> main() {
        return endpoints.stream().filter(Predicate.not(Endpoint::legacy)).findFirst();
    }

    /** Returns the subset of endpoints are either legacy or not */
    public EndpointList legacy(boolean legacy) {
        return of(endpoints.stream().filter(endpoint -> endpoint.legacy() == legacy));
    }

    /** Returns the subset of endpoints with given scope */
    public EndpointList scope(Endpoint.Scope scope) {
        return of(endpoints.stream().filter(endpoint -> endpoint.scope() == scope));
    }

    /** Returns the union of this and given endpoints */
    public EndpointList and(EndpointList endpoints) {
        return of(Stream.concat(asList().stream(), endpoints.asList().stream()));
    }

    public static EndpointList of(Stream<Endpoint> endpoints) {
        return new EndpointList(endpoints.collect(Collectors.toUnmodifiableList()));
    }

    /** Returns the default global endpoints in given system. Default endpoints are served by a pre-provisioned routing layer */
    public static EndpointList defaultGlobal(ApplicationId application, SystemName system) {
        // Rotation name is always default in the routing layer
        RotationName rotation = RotationName.from("default");
        switch (system) {
            case cd:
            case main:
                return new EndpointList(List.of(
                        Endpoint.of(application).target(rotation).on(Port.plain(4080)).legacy().in(system),
                        Endpoint.of(application).target(rotation).on(Port.tls(4443)).legacy().in(system),
                        Endpoint.of(application).target(rotation).on(Port.tls(4443)).in(system)
                ));
        }
        return EMPTY;
    }

}
