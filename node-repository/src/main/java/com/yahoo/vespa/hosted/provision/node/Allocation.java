// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.NetworkPorts;
import com.yahoo.config.provision.NodeResources;

import java.util.Optional;

/**
 * The allocation of a node
 *
 * @author bratseth
 */
public class Allocation {

    private final ApplicationId owner;
    private final ClusterMembership clusterMembership;

    private final NodeResources requestedResources;

    /**
     * Restart generation, see {@link com.yahoo.vespa.hosted.provision.node.Generation},
     * wanted is increased when a restart of the services on the node is needed, current is updated
     * when a restart has been done on the node.
     */
    private final Generation restartGeneration;

    /** This node can (and should) be removed from the cluster on the next deployment */
    private final boolean removable;

    private final Optional<NetworkPorts> networkPorts;


    public Allocation(ApplicationId owner, ClusterMembership clusterMembership, NodeResources requestedResources,
                      Generation restartGeneration, boolean removable) {
        this(owner, clusterMembership, requestedResources, restartGeneration, removable, Optional.empty());
    }

    public Allocation(ApplicationId owner, ClusterMembership clusterMembership, NodeResources requestedResources,
                      Generation restartGeneration, boolean removable, Optional<NetworkPorts> networkPorts) {
        this.owner = owner;
        this.clusterMembership = clusterMembership;
        this.requestedResources = requestedResources;
        this.restartGeneration = restartGeneration;
        this.removable = removable;
        this.networkPorts = networkPorts;
    }

    /** Returns the id of the application this is allocated to */
    public ApplicationId owner() { return owner; }

    /** Returns the role this node is allocated to */
    public ClusterMembership membership() { return clusterMembership; }

    /** Returns the node resources requested, leading to this allocation on this node */
    public NodeResources requestedResources() { return requestedResources; }

    /** Returns the restart generation (wanted and current) of this */
    public Generation restartGeneration() { return restartGeneration; }

    /** Returns network ports allocations (or empty if not recorded) */
    public Optional<NetworkPorts> networkPorts() { return networkPorts; }

    /** Returns a copy of this which is retired */
    public Allocation retire() {
        return new Allocation(owner, clusterMembership.retire(), requestedResources, restartGeneration, removable, networkPorts);
    }

    /** Returns a copy of this which is not retired */
    public Allocation unretire() {
        return new Allocation(owner, clusterMembership.unretire(), requestedResources, restartGeneration, removable, networkPorts);
    }

    /** Return whether this node is ready to be removed from the application */
    public boolean isRemovable() { return removable; }

    public Allocation withRequestedResources(NodeResources resources) {
        return new Allocation(owner, clusterMembership, resources, restartGeneration, removable, networkPorts);
    }

    /** Returns a copy of this with the current restart generation set to generation */
    public Allocation withRestart(Generation generation) {
        return new Allocation(owner, clusterMembership, requestedResources, generation, removable, networkPorts);
    }

    /** Returns a copy of this allocation where removable is set to true */
    public Allocation removable() {
        return new Allocation(owner, clusterMembership, requestedResources, restartGeneration, true, networkPorts);
    }

    public Allocation with(ClusterMembership newMembership) {
        return new Allocation(owner, newMembership, requestedResources, restartGeneration, removable, networkPorts);
    }

    public Allocation withNetworkPorts(NetworkPorts ports) {
        return new Allocation(owner, clusterMembership, requestedResources, restartGeneration, removable, Optional.of(ports));
    }

    @Override
    public String toString() { return "allocated to " + owner + " as '" + clusterMembership + "'"; }

}
