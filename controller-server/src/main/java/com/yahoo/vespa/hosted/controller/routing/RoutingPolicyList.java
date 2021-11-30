// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.application.EndpointId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A filterable list of {@link RoutingPolicy}'s.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class RoutingPolicyList extends AbstractFilteringList<RoutingPolicy, RoutingPolicyList> {

    private final Map<RoutingPolicyId, RoutingPolicy> policiesById;

    protected RoutingPolicyList(Collection<RoutingPolicy> items, boolean negate) {
        super(items, negate, RoutingPolicyList::new);
        this.policiesById = items.stream().collect(Collectors.collectingAndThen(
                Collectors.toMap(RoutingPolicy::id,
                                 Function.identity(),
                                 (p1, p2) -> {
                                     throw new IllegalArgumentException("Duplicate key " + p1.id());
                                 },
                                 LinkedHashMap::new),
                Collections::unmodifiableMap)
        );
    }

    /** Returns the subset of policies owned by given instance */
    public RoutingPolicyList instance(ApplicationId instance) {
        return matching(policy -> policy.id().owner().equals(instance));
    }

    /** Returns the subset of policies applying to given deployment */
    public RoutingPolicyList deployment(DeploymentId deployment) {
        return matching(policy -> policy.appliesTo(deployment));
    }

    /** Returns the policy with given ID, if any */
    public Optional<RoutingPolicy> of(RoutingPolicyId id) {
        return Optional.ofNullable(policiesById.get(id));
    }

    /** Returns this grouped by policy ID */
    public Map<RoutingPolicyId, RoutingPolicy> asMap() {
        return policiesById;
    }

    /** Returns a copy of this with all policies for instance replaced with given policies */
    public RoutingPolicyList replace(ApplicationId instance, RoutingPolicyList policies) {
        List<RoutingPolicy> copy = new ArrayList<>(asList());
        copy.removeIf(policy -> policy.id().owner().equals(instance));
        policies.forEach(copy::add);
        return copyOf(copy);
    }

    /** Create a routing table for instance-level endpoints backed by routing policies in this */
    Map<RoutingId, List<RoutingPolicy>> asInstanceRoutingTable() {
        return asRoutingTable(false);
    }

    /** Create a routing table for application-level endpoints backed by routing policies in this */
    Map<RoutingId, List<RoutingPolicy>> asApplicationRoutingTable() {
        return asRoutingTable(true);
    }

    private Map<RoutingId, List<RoutingPolicy>> asRoutingTable(boolean applicationLevel) {
        Map<RoutingId, List<RoutingPolicy>> routingTable = new LinkedHashMap<>();
        for (var policy : this) {
            Set<EndpointId> endpoints = applicationLevel ? policy.applicationEndpoints() : policy.instanceEndpoints();
            for (var endpoint : endpoints) {
                RoutingId id = RoutingId.of(policy.id().owner(), endpoint);
                routingTable.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(policy);
            }
        }
        return Collections.unmodifiableMap(routingTable);
    }

    public static RoutingPolicyList copyOf(Collection<RoutingPolicy> policies) {
        return new RoutingPolicyList(policies, false);
    }

}
