// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Performs preparation of node activation changes for an application.
 *
 * @author bratseth
 */
class Preparer {

    private final GroupPreparer groupPreparer;
    private final Optional<LoadBalancerProvisioner> loadBalancerProvisioner;

    public Preparer(NodeRepository nodeRepository, Optional<HostProvisioner> hostProvisioner,
                    Optional<LoadBalancerProvisioner> loadBalancerProvisioner) {
        this.loadBalancerProvisioner = loadBalancerProvisioner;
        this.groupPreparer = new GroupPreparer(nodeRepository, hostProvisioner);
    }

    /**
     * Prepare all required resources for the given application and cluster.
     *
     * @return the list of nodes this cluster will have allocated if activated
     */
    // Note: This may make persisted changes to the set of reserved and inactive nodes,
    // but it may not change the set of active nodes, as the active nodes must stay in sync with the
    // active config model which is changed on activate
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requested) {
        try {
            loadBalancerProvisioner.ifPresent(provisioner -> provisioner.prepare(application, cluster, requested));
            return groupPreparer.prepare(application, cluster, requested, groupPreparer.createUnlockedNodeList());
        }
        catch (NodeAllocationException e) {
            throw new NodeAllocationException("Could not satisfy " + requested + " in " + application + " " + cluster, e,
                                              e.retryable());
        }
    }

}
