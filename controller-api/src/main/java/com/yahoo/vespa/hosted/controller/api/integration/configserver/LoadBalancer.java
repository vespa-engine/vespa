// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 *  Represents an exclusive load balancer, assigned to an application's cluster.
 *
 * @author mortent
 */
public class LoadBalancer {

    private final String id;
    private final ApplicationId application;
    private final ClusterSpec.Id cluster;
    private final HostName hostname;
    private final Optional<String> dnsZone;
    private final Set<RotationName> rotations;

    public LoadBalancer(String id, ApplicationId application, ClusterSpec.Id cluster, HostName hostname,
                        Optional<String> dnsZone, Set<RotationName> rotations) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.application = Objects.requireNonNull(application, "application must be non-null");
        this.cluster = Objects.requireNonNull(cluster, "cluster must be non-null");
        this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
        this.dnsZone = Objects.requireNonNull(dnsZone, "dnsZone must be non-null");
        this.rotations = ImmutableSortedSet.copyOf(Objects.requireNonNull(rotations, "rotations must be non-null"));
    }

    public String id() {
        return id;
    }

    public ApplicationId application() {
        return application;
    }

    public ClusterSpec.Id cluster() {
        return cluster;
    }

    public HostName hostname() {
        return hostname;
    }

    public Optional<String> dnsZone() {
        return dnsZone;
    }

    public Set<RotationName> rotations() {
        return rotations;
    }

}
