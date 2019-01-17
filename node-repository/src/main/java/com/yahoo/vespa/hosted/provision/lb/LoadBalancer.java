// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.provision.maintenance.LoadBalancerExpirer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a load balancer for an application.
 *
 * @author mpolden
 */
public class LoadBalancer {

    private final LoadBalancerId id;
    private final HostName hostname;
    private final Set<Integer> ports;
    private final Set<String> networks;
    private final Set<Real> reals;
    private final boolean inactive;

    // TODO: Remove this when no longer used by internal code
    public LoadBalancer(LoadBalancerId id, HostName hostname, List<Integer> ports, List<Real> reals, boolean inactive) {
        this(id, hostname, ImmutableSortedSet.copyOf(ports), Collections.emptySet(), ImmutableSortedSet.copyOf(reals), inactive);
    }

    public LoadBalancer(LoadBalancerId id, HostName hostname, Set<Integer> ports, Set<String> networks, Set<Real> reals, boolean inactive) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
        this.ports = ImmutableSortedSet.copyOf(requirePorts(ports));
        this.networks = ImmutableSortedSet.copyOf(Objects.requireNonNull(networks, "networks must be non-null"));
        this.reals = ImmutableSortedSet.copyOf(Objects.requireNonNull(reals, "targets must be non-null"));
        this.inactive = inactive;
    }

    /** An identifier for this load balancer. The ID is unique inside the zone */
    public LoadBalancerId id() {
        return id;
    }

    /** Fully-qualified domain name of this load balancer. This hostname can be used for query and feed */
    public HostName hostname() {
        return hostname;
    }

    /** Listening port(s) of this load balancer */
    public Set<Integer> ports() {
        return ports;
    }

    /** Networks (CIDR blocks) of this load balancer */
    public Set<String> networks() {
        return networks;
    }

    /** Real servers behind this load balancer */
    public Set<Real> reals() {
        return reals;
    }

    /**
     * Returns whether this load balancer is inactive. Inactive load balancers cannot be re-activated, and are
     * eventually removed by {@link LoadBalancerExpirer}.
     */
    public boolean inactive() {
        return inactive;
    }

    /** Return a copy of this that is set inactive */
    public LoadBalancer deactivate() {
        return new LoadBalancer(id, hostname, ports, networks, reals, true);
    }

    private static Set<Integer> requirePorts(Set<Integer> ports) {
        Objects.requireNonNull(ports, "ports must be non-null");
        if (ports.isEmpty()) {
            throw new IllegalArgumentException("ports must be non-empty");
        }
        if (!ports.stream().allMatch(port -> port >= 1 && port <= 65535)) {
            throw new IllegalArgumentException("all ports must be >= 1 and <= 65535");
        }
        return ports;
    }

}
