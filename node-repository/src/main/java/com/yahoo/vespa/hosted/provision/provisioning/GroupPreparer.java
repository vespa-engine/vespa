// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.HostSharing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Performs preparation of node activation changes for a single host group in an application.
 *
 * @author bratseth
 */
public class GroupPreparer {

    private static final Mutex PROBE_LOCK = () -> {};
    private static final Logger log = Logger.getLogger(GroupPreparer.class.getName());

    private final NodeRepository nodeRepository;
    private final Optional<HostProvisioner> hostProvisioner;

    /** Contains list of prepared nodes and the {@link LockedNodeList} object to use for next prepare call */
    record PrepareResult(List<Node> prepared, LockedNodeList allNodes) {}

    public GroupPreparer(NodeRepository nodeRepository, Optional<HostProvisioner> hostProvisioner) {
        this.nodeRepository = nodeRepository;
        this.hostProvisioner = hostProvisioner;
    }

    /**
     * Ensure sufficient nodes are reserved or active for the given application, group and cluster
     *
     * @param application        the application we are allocating to
     * @param cluster            the cluster and group we are allocating to
     * @param requestedNodes     a specification of the requested nodes
     * @param surplusActiveNodes currently active nodes which are available to be assigned to this group.
     *                           This method will remove from this list if it finds it needs additional nodes
     * @param indices            the next available node indices for this cluster.
     *                           This method will consume these when it allocates new nodes to the cluster.
     * @param allNodes           list of all nodes and hosts. Use {@link #createUnlockedNodeList()} to create param for
     *                           first invocation. Then use previous {@link PrepareResult#allNodes()} for the following.
     * @return the list of nodes this cluster group will have allocated if activated, and
     */
    // Note: This operation may make persisted changes to the set of reserved and inactive nodes,
    // but it may not change the set of active nodes, as the active nodes must stay in sync with the
    // active config model which is changed on activate
    public PrepareResult prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                                 List<Node> surplusActiveNodes, NodeIndices indices, int wantedGroups,
                                 LockedNodeList allNodes) {
        log.log(Level.FINE, () -> "Preparing " + cluster.type().name() + " " + cluster.id() + " with requested resources " +
                                  requestedNodes.resources().orElse(NodeResources.unspecified()));
        // Try preparing in memory without global unallocated lock. Most of the time there should be no changes,
        // and we can return nodes previously allocated.
        NodeAllocation probeAllocation = prepareAllocation(application, cluster, requestedNodes, surplusActiveNodes,
                                                           indices::probeNext, wantedGroups, allNodes);
        if (probeAllocation.fulfilledAndNoChanges()) {
            List<Node> acceptedNodes = probeAllocation.finalNodes();
            surplusActiveNodes.removeAll(acceptedNodes);
            indices.commitProbe();
            return new PrepareResult(acceptedNodes, allNodes);
        } else {
            // There were some changes, so re-do the allocation with locks
            indices.resetProbe();
            List<Node> prepared = prepareWithLocks(application, cluster, requestedNodes, surplusActiveNodes, indices, wantedGroups);
            return new PrepareResult(prepared, createUnlockedNodeList());
        }
    }

    // Use this to create allNodes param to prepare method for first invocation of prepare
    LockedNodeList createUnlockedNodeList() { return nodeRepository.nodes().list(PROBE_LOCK); }

    /// Note that this will write to the node repo.
    private List<Node> prepareWithLocks(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                                        List<Node> surplusActiveNodes, NodeIndices indices, int wantedGroups) {
        try (Mutex lock = nodeRepository.applications().lock(application);
             Mutex allocationLock = nodeRepository.nodes().lockUnallocated()) {
            LockedNodeList allNodes = nodeRepository.nodes().list(allocationLock);
            NodeAllocation allocation = prepareAllocation(application, cluster, requestedNodes, surplusActiveNodes,
                                                          indices::next, wantedGroups, allNodes);
            NodeType hostType = allocation.nodeType().hostType();
            if (canProvisionDynamically(hostType) && allocation.hostDeficit().isPresent()) {
                HostSharing sharing = hostSharing(cluster, hostType);
                Version osVersion = nodeRepository.osVersions().targetFor(hostType).orElse(Version.emptyVersion);
                NodeAllocation.HostDeficit deficit = allocation.hostDeficit().get();
                List<Node> hosts = new ArrayList<>();
                Consumer<List<ProvisionedHost>> provisionedHostsConsumer = provisionedHosts -> {
                    hosts.addAll(provisionedHosts.stream().map(ProvisionedHost::generateHost).toList());
                    nodeRepository.nodes().addNodes(hosts, Agent.application);

                    // Offer the nodes on the newly provisioned hosts, this should be enough to cover the deficit
                    List<NodeCandidate> candidates = provisionedHosts.stream()
                            .map(host -> NodeCandidate.createNewExclusiveChild(host.generateNode(),
                                    host.generateHost()))
                            .toList();
                    allocation.offer(candidates);
                };

                try {
                    hostProvisioner.get().provisionHosts(
                            allocation.provisionIndices(deficit.count()), hostType, deficit.resources(), application,
                            osVersion, sharing, Optional.of(cluster.type()), requestedNodes.cloudAccount(),
                            provisionedHostsConsumer);
                } catch (NodeAllocationException e) {
                    // Mark the nodes that were written to ZK in the consumer for deprovisioning. While these hosts do
                    // not exist, we cannot remove them from ZK here because other nodes may already have been
                    // allocated on them, so let HostDeprovisioner deal with it
                    hosts.forEach(host -> nodeRepository.nodes().deprovision(host.hostname(), Agent.system, nodeRepository.clock().instant()));
                    throw e;
                }
            }

            if (! allocation.fulfilled() && requestedNodes.canFail())
                throw new NodeAllocationException((cluster.group().isPresent() ? "Node allocation failure on " + cluster.group().get()
                                                                               : "") + allocation.allocationFailureDetails(),
                                                  true);

            // Carry out and return allocation
            nodeRepository.nodes().reserve(allocation.reservableNodes());
            nodeRepository.nodes().addReservedNodes(new LockedNodeList(allocation.newNodes(), allocationLock));
            List<Node> acceptedNodes = allocation.finalNodes();
            surplusActiveNodes.removeAll(acceptedNodes);
            return acceptedNodes;
        }
    }

    private NodeAllocation prepareAllocation(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                                             List<Node> surplusActiveNodes, Supplier<Integer> nextIndex, int wantedGroups,
                                             LockedNodeList allNodes) {

        NodeAllocation allocation = new NodeAllocation(allNodes, application, cluster, requestedNodes, nextIndex, nodeRepository);
        NodePrioritizer prioritizer = new NodePrioritizer(allNodes,
                                                          application,
                                                          cluster,
                                                          requestedNodes,
                                                          wantedGroups,
                                                          nodeRepository.zone().cloud().dynamicProvisioning(),
                                                          nodeRepository.nameResolver(),
                                                          nodeRepository.nodes(),
                                                          nodeRepository.resourcesCalculator(),
                                                          nodeRepository.spareCount(),
                                                          requestedNodes.cloudAccount().isEnclave(nodeRepository.zone()));
        allocation.offer(prioritizer.collect(surplusActiveNodes));
        return allocation;
    }

    private boolean canProvisionDynamically(NodeType hostType) {
        return nodeRepository.zone().cloud().dynamicProvisioning() &&
               (hostType == NodeType.host || hostType.isConfigServerHostLike());
    }

    private HostSharing hostSharing(ClusterSpec cluster, NodeType hostType) {
        if ( hostType.isSharable())
            return nodeRepository.exclusiveAllocation(cluster) ? HostSharing.exclusive : HostSharing.any;
        else
            return HostSharing.any;
    }

}
