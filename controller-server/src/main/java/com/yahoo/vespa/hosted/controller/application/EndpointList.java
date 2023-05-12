// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


/**
 * A list of endpoints for an application.
 *
 * @author mpolden
 */
public class EndpointList extends AbstractFilteringList<Endpoint, EndpointList> {

    public static final EndpointList EMPTY = EndpointList.copyOf(List.of());

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

    /** Returns the subset of endpoints named according to given ID and scope */
    public EndpointList named(EndpointId id, Endpoint.Scope scope) {
        return matching(endpoint -> endpoint.scope() == scope && // ID is only unique within a scope
                                    endpoint.name().equals(id.id()));
    }

    /** Returns the subset of endpoints pointing to given cluster */
    public EndpointList cluster(ClusterSpec.Id cluster) {
        return matching(endpoint -> endpoint.cluster().equals(cluster));
    }

    /** Returns the subset of endpoints pointing to given instance */
    public EndpointList instance(InstanceName instance) {
        return matching(endpoint -> endpoint.instance().isPresent() &&
                                    endpoint.instance().get().equals(instance));
    }

    /** Returns the subset of endpoints which target all of the given deployments */
    public EndpointList targets(List<DeploymentId> deployments) {
        return matching(endpoint -> endpoint.deployments().containsAll(deployments));
    }

    /** Returns the subset of endpoints which target the given deployment */
    public EndpointList targets(DeploymentId deployment) {
        return targets(List.of(deployment));
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

    /** Returns the subset of endpoints that use direct routing */
    public EndpointList direct() {
        return matching(endpoint -> endpoint.routingMethod().isDirect());
    }

    /** Returns the subset of endpoints that use shared routing */
    public EndpointList shared() {
        return matching(endpoint -> endpoint.routingMethod().isShared());
    }

    public static EndpointList copyOf(Collection<Endpoint> endpoints) {
        return new EndpointList(endpoints, false);
    }

}
