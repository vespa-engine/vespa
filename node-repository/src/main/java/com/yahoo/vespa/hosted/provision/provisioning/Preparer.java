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
import java.util.stream.Collectors;

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

    /** Prepare all required resources for the given application and cluster */
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes) {
        try {
            var nodes = prepareNodes(application, cluster, requestedNodes);
            prepareLoadBalancer(application, cluster, requestedNodes);
            return nodes;
        }
        catch (NodeAllocationException e) {
            throw new NodeAllocationException("Could not satisfy " + requestedNodes +
                                              " in " + application + " " + cluster, e,
                                              e.retryable());
        }
    }

    /**
     * Ensure sufficient nodes are reserved or active for the given application and cluster
     *
     * @return the list of nodes this cluster will have allocated if activated
     */
     // Note: This operation may make persisted changes to the set of reserved and inactive nodes,
     // but it may not change the set of active nodes, as the active nodes must stay in sync with the
     // active config model which is changed on activate
    private List<Node> prepareNodes(ApplicationId application, ClusterSpec cluster, NodeSpec requested) {
        LockedNodeList allNodes = groupPreparer.createUnlockedNodeList();
        List<Node> surplusNodes = findNodesInRemovableGroups(application, requested.groups(), allNodes);
        List<Node> accepted = groupPreparer.prepare(application, cluster, requested, surplusNodes, allNodes);
        if (requested.rejectNonActiveParent()) { // TODO: Move to NodeAllocation
            NodeList activeHosts = allNodes.state(Node.State.active).parents().nodeType(requested.type().hostType());
            accepted = accepted.stream()
                               .filter(node -> node.parentHostname().isEmpty() || activeHosts.parentOf(node).isPresent())
                               .toList();
        }
        return accepted.stream().filter(node -> ! surplusNodes.contains(node)).collect(Collectors.toList());
    }

    /** Prepare a load balancer for given application and cluster */
    public void prepareLoadBalancer(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes) {
        loadBalancerProvisioner.ifPresent(provisioner -> provisioner.prepare(application, cluster, requestedNodes));
    }

    /**
     * Returns a list of the nodes which are
     * in groups with index number above or equal the group count
     */
    private List<Node> findNodesInRemovableGroups(ApplicationId application, int wantedGroups, NodeList allNodes) {
        List<Node> surplusNodes = new ArrayList<>();
        for (Node node : allNodes.owner(application).state(Node.State.active)) {
            ClusterSpec nodeCluster = node.allocation().get().membership().cluster();
            if (nodeCluster.group().get().index() >= wantedGroups)
                surplusNodes.add(node);
        }
        return surplusNodes;
    }

}
