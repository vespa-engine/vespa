// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.HostSharing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs preparation of node activation changes for a cluster of an application.
 *
 * @author bratseth
 */
public class Preparer {

    private static final Mutex PROBE_LOCK = () -> {};
    private static final Logger log = Logger.getLogger(Preparer.class.getName());

    private final NodeRepository nodeRepository;
    private final Optional<HostProvisioner> hostProvisioner;
    private final Optional<LoadBalancerProvisioner> loadBalancerProvisioner;
    private final ProvisioningThrottler throttler;

    public Preparer(NodeRepository nodeRepository, Optional<HostProvisioner> hostProvisioner, Optional<LoadBalancerProvisioner> loadBalancerProvisioner, Metric metric) {
        this.nodeRepository = nodeRepository;
        this.hostProvisioner = hostProvisioner;
        this.loadBalancerProvisioner = loadBalancerProvisioner;
        this.throttler = new ProvisioningThrottler(nodeRepository, metric);
    }

    /**
     * Ensure sufficient nodes are reserved or active for the given application, group and cluster
     *
     * @param application        the application we are allocating to
     * @param cluster            the cluster and group we are allocating to
     * @param requested          a specification of the requested nodes
     * @return the list of nodes this cluster group will have allocated if activated
     */
    // Note: This operation may make persisted changes to the set of reserved and inactive nodes,
    // but it may not change the set of active nodes, as the active nodes must stay in sync with the
    // active config model which is changed on activate
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requested) {
        log.log(Level.FINE, () -> "Preparing " + cluster.type().name() + " " + cluster.id() + " with requested resources " +
                                  requested.resources().orElse(NodeResources.unspecified()));

        loadBalancerProvisioner.ifPresent(provisioner -> provisioner.prepare(application, cluster, requested));

        // Try preparing in memory without global unallocated lock. Most of the time there should be no changes,
        // and we can return nodes previously allocated.
        LockedNodeList allNodes = nodeRepository.nodes().list(PROBE_LOCK);
        NodeIndices indices = new NodeIndices(cluster.id(), allNodes);
        NodeAllocation probeAllocation = prepareAllocation(application, cluster, requested, indices::probeNext, allNodes);
        if (probeAllocation.fulfilledAndNoChanges()) {
            List<Node> acceptedNodes = probeAllocation.finalNodes();
            indices.commitProbe();
            return acceptedNodes;
        } else {
            // There were some changes, so re-do the allocation with locks
            indices.resetProbe();
            return prepareWithLocks(application, cluster, requested, indices);
        }
    }

    /// Note that this will write to the node repo.
    private List<Node> prepareWithLocks(ApplicationId application, ClusterSpec cluster, NodeSpec requested, NodeIndices indices) {
        try (Mutex lock = nodeRepository.applications().lock(application);
             Mutex allocationLock = nodeRepository.nodes().lockUnallocated()) {
            LockedNodeList allNodes = nodeRepository.nodes().list(allocationLock);
            NodeAllocation allocation = prepareAllocation(application, cluster, requested, indices::next, allNodes);
            NodeType hostType = allocation.nodeType().hostType();
            if (canProvisionDynamically(hostType) && allocation.hostDeficit().isPresent()) {
                HostSharing sharing = hostSharing(cluster, hostType);
                Version osVersion = nodeRepository.osVersions().targetFor(hostType).orElse(Version.emptyVersion);
                NodeAllocation.HostDeficit deficit = allocation.hostDeficit().get();
                List<Node> hosts = new ArrayList<>();
                Consumer<List<ProvisionedHost>> whenProvisioned = provisionedHosts -> {
                    hosts.addAll(provisionedHosts.stream().map(host -> host.generateHost(requested.hostTTL())).toList());
                    nodeRepository.nodes().addNodes(hosts, Agent.application);

                    // Offer the nodes on the newly provisioned hosts, this should be enough to cover the deficit
                    List<NodeCandidate> candidates = provisionedHosts.stream()
                            .map(host -> NodeCandidate.createNewExclusiveChild(host.generateNode(),
                                                                               host.generateHost(requested.hostTTL())))
                            .toList();
                    allocation.offer(candidates);
                };
                try {
                    if (throttler.throttle(allNodes, Agent.system)) {
                        throw new NodeAllocationException("Host provisioning is being throttled", true);
                    }

                    HostProvisionRequest request = new HostProvisionRequest(allocation.provisionIndices(deficit.count()),
                                                                            hostType,
                                                                            deficit.resources(),
                                                                            application,
                                                                            osVersion,
                                                                            sharing,
                                                                            Optional.of(cluster.type()),
                                                                            Optional.of(cluster.id()),
                                                                            requested.cloudAccount(),
                                                                            deficit.dueToFlavorUpgrade());
                    Predicate<NodeResources> realHostResourcesWithinLimits = resources -> nodeRepository.nodeResourceLimits().isWithinRealLimits(resources, application, cluster);
                    hostProvisioner.get().provisionHosts(request, realHostResourcesWithinLimits, whenProvisioned);
                } catch (NodeAllocationException e) {
                    // Mark the nodes that were written to ZK in the consumer for deprovisioning. While these hosts do
                    // not exist, we cannot remove them from ZK here because other nodes may already have been
                    // allocated on them, so let HostDeprovisioner deal with it
                    hosts.forEach(host -> nodeRepository.nodes().deprovision(host.hostname(), Agent.system, nodeRepository.clock().instant()));
                    throw e;
                }
            } else if (allocation.hostDeficit().isPresent() && requested.canFail() &&
                       allocation.hasRetiredJustNow() && requested instanceof NodeSpec.CountNodeSpec cns) {
                // Non-dynamically provisioned zone with a deficit because we just now retired some nodes.
                // Try again, but without retiring
                indices.resetProbe();
                List<Node> accepted = prepareWithLocks(application, cluster, cns.withoutRetiring(), indices);
                log.warning("Prepared " + application + " " + cluster.id() + " without retirement due to lack of capacity");
                return accepted;
            }

            if (! allocation.fulfilled() && requested.canFail())
                throw new NodeAllocationException("Could not satisfy " + requested + " in " + application + " " + cluster +
                                                  allocation.allocationFailureDetails(), true);

            // Carry out and return allocation
            List<Node> acceptedNodes = allocation.finalNodes();
            nodeRepository.nodes().reserve(allocation.reservableNodes());
            nodeRepository.nodes().addReservedNodes(new LockedNodeList(allocation.newNodes(), allocationLock));

            if (requested.rejectNonActiveParent()) { // TODO: Move into offer() - currently this must be done *after* reserving
                NodeList activeHosts = allNodes.state(Node.State.active).parents().nodeType(requested.type().hostType());
                acceptedNodes = acceptedNodes.stream()
                                             .filter(node -> node.parentHostname().isEmpty() || activeHosts.parentOf(node).isPresent())
                                             .toList();
            }
            return acceptedNodes;
        }
    }

    private NodeAllocation prepareAllocation(ApplicationId application, ClusterSpec cluster, NodeSpec requested,
                                             Supplier<Integer> nextIndex, LockedNodeList allNodes) {

        NodeAllocation allocation = new NodeAllocation(allNodes, application, cluster, requested, nextIndex, nodeRepository);
        NodePrioritizer prioritizer = new NodePrioritizer(allNodes,
                                                          application,
                                                          cluster,
                                                          requested,
                                                          nodeRepository.zone().cloud().dynamicProvisioning(),
                                                          nodeRepository.nameResolver(),
                                                          nodeRepository.nodes(),
                                                          nodeRepository.resourcesCalculator(),
                                                          nodeRepository.spareCount(),
                                                          requested.cloudAccount().isExclave(nodeRepository.zone()));
        allocation.offer(prioritizer.collect());
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
