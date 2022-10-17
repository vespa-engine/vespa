// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodesAndHosts;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
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
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes, int wantedGroups) {
        try {
            var nodes = prepareNodes(application, cluster, requestedNodes, wantedGroups);
            prepareLoadBalancer(application, cluster, requestedNodes);
            return nodes;
        }
        catch (NodeAllocationException e) {
            throw new NodeAllocationException("Could not satisfy " + requestedNodes +
                                              ( wantedGroups > 1 ? " (in " + wantedGroups + " groups)" : "") +
                                              " in " + application + " " + cluster + ": " + e.getMessage(),
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
    private List<Node> prepareNodes(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                                    int wantedGroups) {
        NodesAndHosts<LockedNodeList> allNodesAndHosts = groupPreparer.createNodesAndHostUnlocked();
        NodeList appNodes = allNodesAndHosts.nodes().owner(application);
        List<Node> surplusNodes = findNodesInRemovableGroups(appNodes, cluster, wantedGroups);

        List<Integer> usedIndices = appNodes.cluster(cluster.id()).mapToList(node -> node.allocation().get().membership().index());
        NodeIndices indices = new NodeIndices(usedIndices);
        List<Node> acceptedNodes = new ArrayList<>();

        for (int groupIndex = 0; groupIndex < wantedGroups; groupIndex++) {
            ClusterSpec clusterGroup = cluster.with(Optional.of(ClusterSpec.Group.from(groupIndex)));
            GroupPreparer.PrepareResult result = groupPreparer.prepare(application, clusterGroup,
                                                                       requestedNodes.fraction(wantedGroups),
                                                                       surplusNodes, indices, wantedGroups,
                                                                       allNodesAndHosts);
            allNodesAndHosts = result.allNodesAndHosts; // Might have changed
            List<Node> accepted = result.prepared;
            if (requestedNodes.rejectNonActiveParent()) {
                NodeList activeHosts = allNodesAndHosts.nodes().state(Node.State.active).parents().nodeType(requestedNodes.type().hostType());
                accepted = accepted.stream()
                                   .filter(node -> node.parentHostname().isEmpty() || activeHosts.parentOf(node).isPresent())
                                   .collect(Collectors.toList());
            }

            replace(acceptedNodes, accepted);
        }
        moveToActiveGroup(surplusNodes, wantedGroups, cluster.group());
        acceptedNodes.removeAll(surplusNodes);
        return acceptedNodes;
    }

    /** Prepare a load balancer for given application and cluster */
    public void prepareLoadBalancer(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes) {
        loadBalancerProvisioner.ifPresent(provisioner -> provisioner.prepare(application, cluster, requestedNodes));
    }

    /**
     * Returns a list of the nodes which are
     * in groups with index number above or equal the group count
     */
    private List<Node> findNodesInRemovableGroups(NodeList appNodes, ClusterSpec requestedCluster, int wantedGroups) {
        List<Node> surplusNodes = new ArrayList<>();
        for (Node node : appNodes.state(Node.State.active)) {
            ClusterSpec nodeCluster = node.allocation().get().membership().cluster();
            if ( ! nodeCluster.id().equals(requestedCluster.id())) continue;
            if ( ! nodeCluster.type().equals(requestedCluster.type())) continue;
            if (nodeCluster.group().get().index() >= wantedGroups)
                surplusNodes.add(node);
        }
        return surplusNodes;
    }
    
    /** Move nodes from unwanted groups to wanted groups to avoid lingering groups consisting of retired nodes */
    private void moveToActiveGroup(List<Node> surplusNodes, int wantedGroups, Optional<ClusterSpec.Group> targetGroup) {
        for (ListIterator<Node> i = surplusNodes.listIterator(); i.hasNext(); ) {
            Node node = i.next();
            ClusterMembership membership = node.allocation().get().membership();
            ClusterSpec cluster = membership.cluster();
            if (cluster.group().get().index() >= wantedGroups) {
                ClusterSpec.Group newGroup = targetGroup.orElse(ClusterSpec.Group.from(0));
                ClusterMembership newGroupMembership = membership.with(cluster.with(Optional.of(newGroup)));
                i.set(node.with(node.allocation().get().with(newGroupMembership)));
            }
        }
    }

    /**
     * Nodes are immutable so when changing attributes to the node we create a new instance.
     *
     * This method is used to both add new nodes and replaces old node references with the new references.
     */
    private List<Node> replace(List<Node> list, List<Node> changed) {
        list.removeAll(changed);
        list.addAll(changed);
        return list;
    }

}
