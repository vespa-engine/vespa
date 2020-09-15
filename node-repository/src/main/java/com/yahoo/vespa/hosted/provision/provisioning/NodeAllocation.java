// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.lang.MutableInteger;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Used to manage a list of nodes during the node reservation process
 * in order to fulfill the nodespec.
 * 
 * @author bratseth
 */
class NodeAllocation {

    /** List of all nodes in node-repository */
    private final NodeList allNodes;

    /** The application this list is for */
    private final ApplicationId application;

    /** The cluster this list is for */
    private final ClusterSpec cluster;

    /** The requested nodes of this list */
    private final NodeSpec requestedNodes;

    /** The node candidates this has accepted so far, keyed on hostname */
    private final Map<String, NodeCandidate> nodes = new LinkedHashMap<>();

    /** The number of already allocated nodes accepted and not retired */
    private int accepted = 0;

    /** The number of already allocated nodes accepted and not retired and not needing resize */
    private int acceptedWithoutResizingRetired = 0;

    /** The number of nodes rejected because of clashing parentHostname */
    private int rejectedDueToClashingParentHost = 0;

    /** The number of nodes rejected due to exclusivity constraints */
    private int rejectedDueToExclusivity = 0;

    private int rejectedDueToInsufficientRealResources = 0;

    /** The number of nodes that just now was changed to retired */
    private int wasRetiredJustNow = 0;

    /** The node indexes to verify uniqueness of each members index */
    private final Set<Integer> indexes = new HashSet<>();

    /** The next membership index to assign to a new node */
    private final MutableInteger highestIndex;

    private final NodeRepository nodeRepository;
    private final NodeResourceLimits nodeResourceLimits;

    NodeAllocation(NodeList allNodes, ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                   MutableInteger highestIndex, NodeRepository nodeRepository) {
        this.allNodes = allNodes;
        this.application = application;
        this.cluster = cluster;
        this.requestedNodes = requestedNodes;
        this.highestIndex = highestIndex;
        this.nodeRepository = nodeRepository;
        nodeResourceLimits = new NodeResourceLimits(nodeRepository);
    }

    /**
     * Offer some nodes to this. The nodes may have an allocation to a different application or cluster,
     * an allocation to this cluster, or no current allocation (in which case one is assigned).
     * 
     * Note that if unallocated nodes are offered before allocated nodes, this will unnecessarily
     * reject allocated nodes due to index duplicates.
     *
     * @param nodesPrioritized the nodes which are potentially on offer. These may belong to a different application etc.
     * @return the subset of offeredNodes which was accepted, with the correct allocation assigned
     */
    List<Node> offer(List<NodeCandidate> nodesPrioritized) {
        List<Node> accepted = new ArrayList<>();
        for (NodeCandidate candidate : nodesPrioritized) {
            Node offered = candidate.node;

            if (offered.allocation().isPresent()) {
                Allocation allocation = offered.allocation().get();
                ClusterMembership membership = allocation.membership();
                if ( ! allocation.owner().equals(application)) continue; // wrong application
                if ( ! membership.cluster().satisfies(cluster)) continue; // wrong cluster id/type
                if ((! candidate.isSurplusNode || saturated()) && ! membership.cluster().group().equals(cluster.group())) continue; // wrong group and we can't or have no reason to change it
                if ( offered.state() == Node.State.active && allocation.isRemovable()) continue; // don't accept; causes removal
                if ( indexes.contains(membership.index())) continue; // duplicate index (just to be sure)

                boolean resizeable = false;
                boolean acceptToRetire = false;
                if (requestedNodes.considerRetiring()) {
                    resizeable = candidate.isResizable;
                    acceptToRetire = acceptToRetire(candidate);
                }

                if ((! saturated() && hasCompatibleFlavor(candidate) && requestedNodes.acceptable(offered)) || acceptToRetire)
                    accepted.add(acceptNode(candidate, shouldRetire(candidate), resizeable));
            }
            else if (! saturated() && hasCompatibleFlavor(candidate)) {
                if ( ! nodeResourceLimits.isWithinRealLimits(offered, cluster)) {
                    ++rejectedDueToInsufficientRealResources;
                    continue;
                }
                if ( violatesParentHostPolicy(offered)) {
                    ++rejectedDueToClashingParentHost;
                    continue;
                }
                if ( ! exclusiveTo(application.tenant(), application.application(), offered.parentHostname())) {
                    ++rejectedDueToExclusivity;
                    continue;
                }
                if ( requestedNodes.isExclusive() && ! hostsOnly(application, offered.parentHostname())) {
                    ++rejectedDueToExclusivity;
                    continue;
                }
                if (offered.status().wantToRetire()) {
                    continue;
                }
                candidate = candidate.withNode(offered.allocate(application,
                                                      ClusterMembership.from(cluster, highestIndex.add(1)),
                                                      requestedNodes.resources().orElse(candidate.node.resources()),
                                                      nodeRepository.clock().instant()));
                accepted.add(acceptNode(candidate, false, false));
            }
        }

        return accepted;
    }

    private boolean shouldRetire(NodeCandidate candidate) {
        if ( ! requestedNodes.considerRetiring()) return false;
        if ( ! nodeResourceLimits.isWithinRealLimits(candidate.node, cluster)) return true;
        if (violatesParentHostPolicy(candidate.node)) return true;
        if ( ! hasCompatibleFlavor(candidate)) return true;
        if (candidate.node.status().wantToRetire()) return true;
        if (requestedNodes.isExclusive() && ! hostsOnly(application, candidate.node.parentHostname())) return true;
        return false;
    }

    private boolean violatesParentHostPolicy(Node offered) {
        return checkForClashingParentHost() && offeredNodeHasParentHostnameAlreadyAccepted(offered);
    }

    private boolean checkForClashingParentHost() {
        return nodeRepository.zone().system() == SystemName.main &&
               nodeRepository.zone().environment().isProduction() &&
               ! application.instance().isTester();
    }

    private boolean offeredNodeHasParentHostnameAlreadyAccepted(Node offered) {
        for (NodeCandidate acceptedNode : nodes.values()) {
            if (acceptedNode.node.parentHostname().isPresent() && offered.parentHostname().isPresent() &&
                    acceptedNode.node.parentHostname().get().equals(offered.parentHostname().get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * If a parent host is given, and it hosts another application which requires exclusive access
     * to the physical host, then we cannot host this application on it.
     */
    private boolean exclusiveTo(TenantName tenant, ApplicationName application, Optional<String> parentHostname) {
        if (parentHostname.isEmpty()) return true;
        for (Node nodeOnHost : allNodes.childrenOf(parentHostname.get())) {
            if (nodeOnHost.allocation().isEmpty()) continue;
            if ( nodeOnHost.allocation().get().membership().cluster().isExclusive() &&
                 ! allocatedTo(tenant, application, nodeOnHost))
                return false;
        }
        return true;
    }

    /** Returns true if this host only hosts the given application (in any instance) */
    private boolean hostsOnly(ApplicationId application, Optional<String> parentHostname) {
        if (parentHostname.isEmpty()) return true; // yes, as host is exclusive

        for (Node nodeOnHost : allNodes.childrenOf(parentHostname.get())) {
            if (nodeOnHost.allocation().isEmpty()) continue;
            if ( ! allocatedTo(application.tenant(), application.application(), nodeOnHost)) return false;
        }
        return true;
    }

    private boolean allocatedTo(TenantName tenant, ApplicationName application, Node node) {
        if (node.allocation().isEmpty()) return false;
        ApplicationId owner = node.allocation().get().owner();
        return owner.tenant().equals(tenant) && owner.application().equals(application);
    }

    /**
     * Returns whether this node should be accepted into the cluster even if it is not currently desired
     * (already enough nodes, or wrong flavor).
     * Such nodes will be marked retired during finalization of the list of accepted nodes.
     * The conditions for this are:
     *
     * This is a content or combined node. These must always be retired before being removed to allow the cluster to
     * migrate away data.
     *
     * This is a container node and it is not desired due to having the wrong flavor. In this case this
     * will (normally) obtain for all the current nodes in the cluster and so retiring before removing must
     * be used to avoid removing all the current nodes at once, before the newly allocated replacements are
     * initialized. (In the other case, where a container node is not desired because we have enough nodes we
     * do want to remove it immediately to get immediate feedback on how the size reduction works out.)
     */
    private boolean acceptToRetire(NodeCandidate candidate) {
        if (candidate.node.state() != Node.State.active) return false;
        if (! candidate.node.allocation().get().membership().cluster().group().equals(cluster.group())) return false;
        if (candidate.node.allocation().get().membership().retired()) return true; // don't second-guess if already retired

        return cluster.type().isContent() ||
               (cluster.type() == ClusterSpec.Type.container && !hasCompatibleFlavor(candidate));
    }

    private boolean hasCompatibleFlavor(NodeCandidate candidate) {
        return requestedNodes.isCompatible(candidate.node.flavor(), nodeRepository.flavors()) || candidate.isResizable;
    }

    private Node acceptNode(NodeCandidate candidate, boolean wantToRetire, boolean resizeable) {
        Node node = candidate.node;

        if (node.allocation().isPresent()) // Record the currently requested resources
            node = node.with(node.allocation().get().withRequestedResources(requestedNodes.resources().orElse(node.resources())));

        if (! wantToRetire) {
            accepted++;

            // We want to allocate new nodes rather than unretiring with resize, so count without those
            // for the purpose of deciding when to stop accepting nodes (saturation)
            if (node.allocation().isEmpty()
                || ! ( requestedNodes.needsResize(node) && node.allocation().get().membership().retired()))
                acceptedWithoutResizingRetired++;

            if (resizeable && ! ( node.allocation().isPresent() && node.allocation().get().membership().retired()))
                node = resize(node);

            if (node.state() != Node.State.active) // reactivated node - wipe state that deactivated it
                node = node.unretire().removable(false);
        } else {
            ++wasRetiredJustNow;
            node = node.retire(nodeRepository.clock().instant());
        }
        if ( ! node.allocation().get().membership().cluster().equals(cluster)) {
            // group may be different
            node = setCluster(cluster, node);
        }
        candidate = candidate.withNode(node);
        indexes.add(node.allocation().get().membership().index());
        highestIndex.set(Math.max(highestIndex.get(), node.allocation().get().membership().index()));
        put(candidate);
        return node;
    }

    private void put(NodeCandidate candidate) {
        nodes.put(candidate.node.hostname(), candidate);
    }

    private Node resize(Node node) {
        NodeResources hostResources = allNodes.parentOf(node).get().flavor().resources();
        return node.with(new Flavor(requestedNodes.resources().get()
                                                  .with(hostResources.diskSpeed())
                                                  .with(hostResources.storageType())));
    }

    private Node setCluster(ClusterSpec cluster, Node node) {
        ClusterMembership membership = node.allocation().get().membership().with(cluster);
        return node.with(node.allocation().get().with(membership));
    }

    /** Returns true if no more nodes are needed in this list */
    private boolean saturated() {
        return requestedNodes.saturatedBy(acceptedWithoutResizingRetired);
    }

    /** Returns true if the content of this list is sufficient to meet the request */
    boolean fulfilled() {
        return requestedNodes.fulfilledBy(accepted);
    }

    /**
     * Returns {@link FlavorCount} describing the docker node deficit for the given {@link NodeSpec}.
     *
     * @return empty if the requested spec is not count based or the requested flavor type is not docker or
     *         the request is already fulfilled. Otherwise returns {@link FlavorCount} containing the required flavor
     *         and node count to cover the deficit.
     */
    Optional<FlavorCount> getFulfilledDockerDeficit() {
        return Optional.of(requestedNodes)
                .filter(NodeSpec.CountNodeSpec.class::isInstance)
                .map(spec -> new FlavorCount(spec.resources().get(), spec.fulfilledDeficitCount(accepted)))
                .filter(flavorCount -> flavorCount.getCount() > 0);
    }

    /**
     * Make the number of <i>non-retired</i> nodes in the list equal to the requested number
     * of nodes, and retire the rest of the list. Only retire currently active nodes.
     * Prefer to retire nodes of the wrong flavor.
     * Make as few changes to the retired set as possible.
     *
     * @return the final list of nodes
     */
    List<Node> finalNodes() {
        int currentRetiredCount = (int) nodes.values().stream().filter(node -> node.node.allocation().get().membership().retired()).count();
        int deltaRetiredCount = requestedNodes.idealRetiredCount(nodes.size(), currentRetiredCount) - currentRetiredCount;

        if (deltaRetiredCount > 0) { // retire until deltaRetiredCount is 0
            for (NodeCandidate candidate : byRetiringPriority(nodes.values())) {
                if ( ! candidate.node.allocation().get().membership().retired() && candidate.node.state() == Node.State.active) {
                    candidate = candidate.withNode(candidate.node.retire(Agent.application, nodeRepository.clock().instant()));
                    put(candidate);
                    if (--deltaRetiredCount == 0) break;
                }
            }
        }
        else if (deltaRetiredCount < 0) { // unretire until deltaRetiredCount is 0
            for (NodeCandidate candidate : byUnretiringPriority(nodes.values())) {
                if ( candidate.node.allocation().get().membership().retired() && hasCompatibleFlavor(candidate) ) {
                    if (candidate.isResizable)
                        candidate = candidate.withNode(resize(candidate.node));
                    candidate = candidate.withNode(candidate.node.unretire());
                    put(candidate);
                    if (++deltaRetiredCount == 0) break;
                }
            }
        }
        
        for (NodeCandidate candidate : nodes.values()) {
            // Set whether the node is exclusive
            Allocation allocation = candidate.node.allocation().get();
            candidate = candidate.withNode(candidate.node.with(allocation.with(allocation.membership()
                                .with(allocation.membership().cluster().exclusive(requestedNodes.isExclusive())))));
            put(candidate);
        }

        return nodes.values().stream().map(n -> n.node).collect(Collectors.toList());
    }

    List<Node> reservableNodes() {
        // Include already reserved nodes to extend reservation period and to potentially update their cluster spec.
        EnumSet<Node.State> reservableStates = EnumSet.of(Node.State.inactive, Node.State.ready, Node.State.reserved);
        return nodesFilter(n -> !n.isNewNode && reservableStates.contains(n.node.state()));
    }

    List<Node> newNodes() {
        return nodesFilter(n -> n.isNewNode);
    }

    private List<Node> nodesFilter(Predicate<NodeCandidate> predicate) {
        return nodes.values().stream()
                .filter(predicate)
                .map(n -> n.node)
                .collect(Collectors.toList());
    }

    /** Prefer to retire nodes we want the least */
    private List<NodeCandidate> byRetiringPriority(Collection<NodeCandidate> candidates) {
        return candidates.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    /** Prefer to unretire nodes we don't want to retire, and otherwise those with lower index */
    private List<NodeCandidate> byUnretiringPriority(Collection<NodeCandidate> candidates) {
        return candidates.stream()
                         .sorted(Comparator.comparing((NodeCandidate n) -> n.node.status().wantToRetire())
                                           .thenComparing(n -> n.node.allocation().get().membership().index()))
                         .collect(Collectors.toList());
    }

    public String outOfCapacityDetails() {
        List<String> reasons = new ArrayList<>();
        if (rejectedDueToExclusivity > 0)
            reasons.add("host exclusivity constraints");
        if (rejectedDueToClashingParentHost > 0)
            reasons.add("insufficient nodes available on separate physical hosts");
        if (wasRetiredJustNow > 0)
            reasons.add("retirement of allocated nodes");
        if (rejectedDueToInsufficientRealResources > 0)
            reasons.add("insufficient real resources on hosts");

        if (reasons.isEmpty()) return "";
        return ": Not enough nodes available due to " + reasons.stream().collect(Collectors.joining(", "));
    }

    static class FlavorCount {

        private final NodeResources flavor;
        private final int count;

        private FlavorCount(NodeResources flavor, int count) {
            this.flavor = flavor;
            this.count = count;
        }

        NodeResources getFlavor() {
            return flavor;
        }

        int getCount() {
            return count;
        }
    }

}
