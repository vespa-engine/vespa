// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationMutex;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.text.internal.SnippetGenerator;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.HostSharing;
import com.yahoo.yolean.Exceptions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

        try {
            loadBalancerProvisioner.ifPresent(provisioner -> provisioner.prepare(application, cluster, requested));
        } catch (RuntimeException e) {
            if (!requested.canFail())
                log.warning("Failed to prepare load balancers for " + application + " " + cluster + ": " + Exceptions.toMessageString(e) + " (Ignoring because bootstrap deployment)");
            throw e;
        }

        // Try preparing in memory without global unallocated lock. Most of the time there should be no changes,
        // and we can return nodes previously allocated.
        LockedNodeList allNodes = nodeRepository.nodes().list(PROBE_LOCK);
        NodeIndices indices = new NodeIndices(cluster.id(), allNodes);
        NodeAllocation probeAllocation = prepareAllocation(application, cluster, requested, indices::probeNext, allNodes);
        if (probeAllocation.fulfilledWithoutChanges()) {
            List<Node> acceptedNodes = probeAllocation.finalNodes();
            indices.commitProbe();
            return acceptedNodes;
        } else {
            // There were some changes, so re-do the allocation with locks
            indices.resetProbe();
            return prepareWithLocks(application, cluster, requested, indices);
        }
    }

    private ApplicationMutex parentLockOrNull(NodeType type) {
        return NodeCandidate.canMakeHostExclusive(type, nodeRepository.zone().cloud().allowHostSharing()) ?
               nodeRepository.applications().lock(InfrastructureApplication.withNodeType(type.parentNodeType()).id()) :
               null;
    }

    /// Note that this will write to the node repo.
    private List<Node> prepareWithLocks(ApplicationId application, ClusterSpec cluster, NodeSpec requested, NodeIndices indices) {
        Runnable waiter = null;
        List<Node> acceptedNodes;
        try (Mutex lock = nodeRepository.applications().lock(application);
             ApplicationMutex parentLockOrNull = parentLockOrNull(requested.type());
             Mutex allocationLock = nodeRepository.nodes().lockUnallocated()) {
            LockedNodeList allNodes = nodeRepository.nodes().list(allocationLock);
            NodeAllocation allocation = prepareAllocation(application, cluster, requested, indices::next, allNodes);
            NodeType hostType = allocation.nodeType().hostType();
            if (canProvisionDynamically(hostType) && allocation.hostDeficit().isPresent()) {
                HostSharing sharing = hostSharing(cluster, hostType);
                Version osVersion = nodeRepository.osVersions().targetFor(hostType).orElse(Version.emptyVersion);
                NodeAllocation.HostDeficit deficit = allocation.hostDeficit().get();
                Set<Node> hosts = new LinkedHashSet<>();
                Consumer<List<ProvisionedHost>> whenProvisioned = provisionedHosts -> {
                    List<Node> newHosts = provisionedHosts.stream()
                                                          .map(host -> host.generateHost(requested.hostTTL()))
                                                          .filter(hosts::add)
                                                          .toList();
                    nodeRepository.nodes().addNodes(newHosts, Agent.application);

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
                    Predicate<NodeResources> realHostResourcesWithinLimits =
                            resources -> nodeRepository.nodeResourceLimits().isWithinRealLimits(resources, cluster);
                    waiter = hostProvisioner.get().provisionHosts(request, realHostResourcesWithinLimits, whenProvisioned);
                } catch (NodeAllocationException e) {
                    // Mark the nodes that were written to ZK in the consumer for deprovisioning. While these hosts do
                    // not exist, we cannot remove them from ZK here because other nodes may already have been
                    // allocated on them, so let HostDeprovisioner deal with it
                    hosts.forEach(host -> nodeRepository.nodes().parkRecursively(host.hostname(), Agent.system, true,
                            "Failed to provision: " + Exceptions.toMessageString(e)));
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
            if (parentLockOrNull != null) {
                List<Node> exclusiveParents = allocation.parentsRequiredToBeExclusive();
                nodeRepository.nodes().setExclusiveToApplicationId(exclusiveParents, parentLockOrNull, application);
            }
            acceptedNodes = allocation.finalNodes();
            nodeRepository.nodes().reserve(allocation.reservableNodes());
            nodeRepository.nodes().addReservedNodes(new LockedNodeList(allocation.newNodes(), allocationLock));

            if (requested.rejectNonActiveParent()) { // TODO: Move into offer() - currently this must be done *after* reserving
                NodeList activeHosts = allNodes.state(Node.State.active).parents().nodeType(requested.type().hostType());
                acceptedNodes = acceptedNodes.stream()
                                             .filter(node -> node.parentHostname().isEmpty() || activeHosts.parentOf(node).isPresent())
                                             .toList();
            }
        }

        if (waiter != null) waiter.run();
        return acceptedNodes;
    }

    private NodeAllocation prepareAllocation(ApplicationId application, ClusterSpec cluster, NodeSpec requested,
                                             Supplier<Integer> nextIndex, LockedNodeList allNodes) {
        validateAccount(requested.cloudAccount(), application, allNodes);
        NodeAllocation allocation = new NodeAllocation(allNodes, application, cluster, requested, nextIndex, nodeRepository);
        IP.Allocation.Context allocationContext = IP.Allocation.Context.from(nodeRepository.zone().cloud(),
                                                                             requested.cloudAccount().isExclave(nodeRepository.zone()),
                                                                             nodeRepository.nameResolver());
        NodePrioritizer prioritizer = new NodePrioritizer(allNodes,
                                                          application,
                                                          cluster,
                                                          requested,
                                                          nodeRepository.zone().cloud().dynamicProvisioning(),
                                                          nodeRepository.zone().cloud().allowHostSharing(),
                                                          allocationContext,
                                                          nodeRepository.nodes(),
                                                          nodeRepository.resourcesCalculator(),
                                                          nodeRepository.spareCount(),
                                                          nodeRepository.exclusivity().allocation(cluster));
        allocation.offer(prioritizer.collect());
        if (requested.type() == NodeType.tenant && !requested.canFail() && allocation.changes()) {
            // This should not happen and indicates a bug in the allocation code because boostrap redeployment
            // resulted in allocation changes
            throw new IllegalArgumentException("Refusing change to allocated nodes for " + cluster + " in " +
                                               application + " during bootstrap deployment: " + requested);
        }
        return allocation;
    }

    private void validateAccount(CloudAccount requestedAccount, ApplicationId application, LockedNodeList allNodes) {
        CloudAccount effectiveAccount = requestedAccount.isUnspecified() ? nodeRepository.zone().cloud().account() : requestedAccount;
        List<Node> nodesInOtherAccount = allNodes.owner(application).nodeType(NodeType.tenant).stream()
                                                 .filter(node -> !node.cloudAccount().equals(effectiveAccount))
                                                 .toList();
        if (nodesInOtherAccount.isEmpty()) return;

        SnippetGenerator snippet = new SnippetGenerator();
        String hostnames = nodesInOtherAccount.stream()
                                              .map(Node::hostname)
                                              .collect(Collectors.joining(", "));
        String hostsSnippet = snippet.makeSnippet(hostnames, 100);
        throw new IllegalArgumentException("Cannot allocate nodes in " + requestedAccount + " because " +
                                           application + " has existing nodes in " + nodesInOtherAccount.get(0).cloudAccount() +
                                           ": " + hostsSnippet + ". Deployment must be removed in order to change account");
    }

    private boolean canProvisionDynamically(NodeType hostType) {
        return nodeRepository.zone().cloud().dynamicProvisioning() &&
               (hostType == NodeType.host || hostType.isConfigServerHostLike());
    }

    private HostSharing hostSharing(ClusterSpec cluster, NodeType hostType) {
        if ( hostType.isSharable())
            return nodeRepository.exclusivity().provisioning(cluster) ? HostSharing.provision :
                   nodeRepository.exclusivity().allocation(cluster) ? HostSharing.exclusive :
                   HostSharing.shared;
        else
            return HostSharing.shared;
    }

}
