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

    public Preparer(NodeRepository nodeRepository, Optional<HostProvisioner> hostProvisioner,
                    Optional<LoadBalancerProvisioner> loadBalancerProvisioner) {
        this.groupPreparer = new GroupPreparer(nodeRepository, hostProvisioner, loadBalancerProvisioner);
    }

    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requested) {
        return groupPreparer.prepare(application, cluster, requested);
    }

}
