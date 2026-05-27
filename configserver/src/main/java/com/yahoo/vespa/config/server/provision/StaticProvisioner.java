// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.provision;

import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.SystemName;
import java.util.List;

/**
 * Host provisioning from an existing {@link AllocatedHosts} instance.
 *
 * @author bratseth
 */
public class StaticProvisioner implements HostProvisioner {

    private final AllocatedHosts allocatedHosts;
    
    /** The fallback provisioner to use for unknown clusters, or null to not fall back */
    private final HostProvisioner fallback;
    private final SystemName systemName;

    /**
     * Creates a static host provisioner which will fall back to using the given provisioner
     * if a request is made for nodes in a cluster which is not present in this allocation.
     */
    public StaticProvisioner(AllocatedHosts allocatedHosts, HostProvisioner fallback, SystemName systemName) {
        this.allocatedHosts = allocatedHosts;
        this.fallback = fallback;
        this.systemName = systemName;
    }

    @Override
    public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
        List<HostSpec> hostsAlreadyAllocatedToCluster = 
                allocatedHosts.getHosts().stream()
                                         .filter(host -> host.membership().isPresent() && matches(host.membership().get().cluster(), cluster))
                                         .toList();
        if (hostsAlreadyAllocatedToCluster.isEmpty()) {
            return fallback.prepare(cluster, capacity, logger);
        }

        if (systemName.isKubernetesLike()) {
            return hostsAlreadyAllocatedToCluster.stream()
                    .map(host -> host.membership()
                            .map(membership -> host.withMembership(membership.with(cluster)))
                            .orElse(host))
                    .toList();
        }

        return hostsAlreadyAllocatedToCluster;
    }

    private boolean matches(ClusterSpec nodeCluster, ClusterSpec requestedCluster) {
        if (requestedCluster.group().isPresent()) // we are requesting a specific group
            return nodeCluster.equals(requestedCluster);
        else // we are requesting nodes of all groups in this cluster
            return nodeCluster.satisfies(requestedCluster);
    }

}
