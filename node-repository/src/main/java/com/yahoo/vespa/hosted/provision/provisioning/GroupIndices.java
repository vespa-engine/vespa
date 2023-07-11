// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Knows how to assign a group index to a number of nodes (some of which have an index already),
 * such that the nodes are placed in the desired groups with minimal group movement.
 *
 * @author bratseth
 */
class GroupIndices {

    private final NodeSpec requested;
    private final NodeList allNodes;
    private final Clock clock;

    GroupIndices(NodeSpec requested, NodeList allNodes, Clock clock) {
        if (requested.groups() > 1 && requested.count().isEmpty())
            throw new IllegalArgumentException("Unlimited nodes cannot be grouped");
        this.requested = requested;
        this.allNodes = allNodes;
        this.clock = clock;
    }

    Collection<NodeCandidate> assignTo(Collection<NodeCandidate> nodes) {
        int[] countInGroup = countInEachGroup(nodes);
        nodes = byUnretiringPriority(nodes).stream().map(node -> unretireNodeInExpandedGroup(node, countInGroup)).toList();
        nodes = nodes.stream().map(node -> assignGroupToNewNode(node, countInGroup)).toList();
        nodes = byUnretiringPriority(nodes).stream().map(node -> moveNodeInSurplusGroup(node, countInGroup)).toList();
        nodes = byRetiringPriority(nodes).stream().map(node -> retireSurplusNodeInGroup(node, countInGroup)).toList();
        nodes = nodes.stream().filter(node -> ! shouldRemove(node)).toList();
        return nodes;
    }

    /** Prefer to retire nodes we want the least */
    private List<NodeCandidate> byRetiringPriority(Collection<NodeCandidate> candidates) {
        return candidates.stream().sorted(Comparator.reverseOrder()).toList();
    }

    /** Prefer to unretire nodes we don't want to retire, and otherwise those with lower index */
    private List<NodeCandidate> byUnretiringPriority(Collection<NodeCandidate> candidates) {
        return candidates.stream()
                         .sorted(Comparator.comparing(NodeCandidate::wantToRetire)
                                           .thenComparing(n -> n.allocation().get().membership().index()))
                         .toList();
    }

    private int[] countInEachGroup(Collection<NodeCandidate> nodes) {
        int[] countInGroup = new int[requested.groups()];
        for (var node : nodes) {
            if (node.allocation().get().membership().retired()) continue;
            var currentGroup = node.allocation().get().membership().cluster().group();
            if (currentGroup.isEmpty()) continue;
            if (currentGroup.get().index() >= requested.groups()) continue;
            countInGroup[currentGroup.get().index()]++;
        }
        return countInGroup;
    }

    /** Assign a group to new or to be reactivated nodes. */
    private NodeCandidate assignGroupToNewNode(NodeCandidate node, int[] countInGroup) {
        if (node.state() == Node.State.active && node.allocation().get().membership().retired()) return node;
        if (node.state() == Node.State.active && node.allocation().get().membership().cluster().group().isPresent()) return node;
        return inFirstGroupWithDeficiency(node, countInGroup);
    }

    private NodeCandidate moveNodeInSurplusGroup(NodeCandidate node, int[] countInGroup) {
        var currentGroup = node.allocation().get().membership().cluster().group();
        if (currentGroup.isEmpty()) return node;
        if (currentGroup.get().index() < requested.groups()) return node;
        return inFirstGroupWithDeficiency(node, countInGroup);
    }

    private NodeCandidate retireSurplusNodeInGroup(NodeCandidate node, int[] countInGroup) {
        if (node.allocation().get().membership().retired()) return node;
        var currentGroup = node.allocation().get().membership().cluster().group();
        if (currentGroup.isEmpty()) return node;
        if (currentGroup.get().index() >= requested.groups()) return node;
        if (requested.count().isEmpty()) return node; // Can't retire
        if (countInGroup[currentGroup.get().index()] <= requested.groupSize()) return node;
        countInGroup[currentGroup.get().index()]--;
        return node.withNode(node.toNode().retire(Agent.application, clock.instant()));
    }

    /** Unretire nodes that are already in the correct group when the group is deficient. */
    private NodeCandidate unretireNodeInExpandedGroup(NodeCandidate node, int[] countInGroup) {
        if ( ! node.allocation().get().membership().retired()) return node;
        var currentGroup = node.allocation().get().membership().cluster().group();
        if (currentGroup.isEmpty()) return node;
        if (currentGroup.get().index() >= requested.groups()) return node;
        if (node.preferToRetire() || node.wantToRetire()) return node;
        if (requested.count().isPresent() && countInGroup[currentGroup.get().index()] >= requested.groupSize()) return node;
        node = unretire(node);
        if (node.allocation().get().membership().retired()) return node;
        countInGroup[currentGroup.get().index()]++;
        return node;
    }

    private NodeCandidate inFirstGroupWithDeficiency(NodeCandidate node, int[] countInGroup) {
        for (int group = 0; group < requested.groups(); group++) {
            if (requested.count().isEmpty() || countInGroup[group] < requested.groupSize()) {
                return inGroup(group, node, countInGroup);
            }
        }
        return node;
    }

    private boolean shouldRemove(NodeCandidate node) {
        var currentGroup = node.allocation().get().membership().cluster().group();
        if (currentGroup.isEmpty()) return true; // new and not assigned an index: Not needed
        return currentGroup.get().index() >= requested.groups();
    }

    private NodeCandidate inGroup(int group, NodeCandidate node, int[] countInGroup) {
        node = unretire(node);
        if (node.allocation().get().membership().retired()) return node;
        var membership = node.allocation().get().membership();
        var currentGroup = membership.cluster().group();
        countInGroup[group]++;
        if ( ! currentGroup.isEmpty() && currentGroup.get().index() < requested.groups())
            countInGroup[membership.cluster().group().get().index()]--;
        return node.withNode(node.toNode().with(node.allocation().get().with(membership.with(membership.cluster().with(Optional.of(ClusterSpec.Group.from(group)))))));
    }

    /** Attempt to unretire the given node if it is retired. */
    private NodeCandidate unretire(NodeCandidate node) {
        if (node.retiredNow()) return node;
        if ( ! node.allocation().get().membership().retired()) return node;
        if ( ! hasCompatibleResources(node) ) return node;
        var parent = node.parentHostname().flatMap(hostname -> allNodes.node(hostname));
        if (parent.isPresent() && (parent.get().status().wantToRetire() || parent.get().status().preferToRetire())) return node;
        node = node.withNode();
        if ( ! requested.isCompatible(node.resources()))
            node = node.withNode(resize(node.toNode()));
        return node.withNode(node.toNode().unretire());
    }

    private Node resize(Node node) {
        NodeResources hostResources = allNodes.parentOf(node).get().flavor().resources();
        return node.with(new Flavor(requested.resources().get()
                                             .with(hostResources.diskSpeed())
                                             .with(hostResources.storageType())
                                             .with(hostResources.architecture())),
                         Agent.application, clock.instant());
    }

    private boolean hasCompatibleResources(NodeCandidate candidate) {
        return requested.isCompatible(candidate.resources()) || candidate.isResizable;
    }

}
