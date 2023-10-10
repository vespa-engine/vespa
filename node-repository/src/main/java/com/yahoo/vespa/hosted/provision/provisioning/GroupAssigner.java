// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
class GroupAssigner {

    private final NodeSpec requested;
    private final NodeList allNodes;
    private final Clock clock;

    GroupAssigner(NodeSpec requested, NodeList allNodes, Clock clock) {
        if (requested.groups() > 1 && requested.count().isEmpty())
            throw new IllegalArgumentException("Unlimited nodes cannot be grouped");
        this.requested = requested;
        this.allNodes = allNodes;
        this.clock = clock;
    }

    Collection<NodeCandidate> assignTo(Collection<NodeCandidate> candidates) {
        int[] countInGroup = countInEachGroup(candidates);
        candidates = byUnretiringPriority(candidates).stream().map(node -> unretireNodeInExpandedGroup(node, countInGroup)).toList();
        candidates = candidates.stream().map(node -> assignGroupToNewNode(node, countInGroup)).toList();
        candidates = byUnretiringPriority(candidates).stream().map(node -> moveNodeInSurplusGroup(node, countInGroup)).toList();
        candidates = byRetiringPriority(candidates).stream().map(node -> retireSurplusNodeInGroup(node, countInGroup)).toList();
        candidates = candidates.stream().filter(node -> ! shouldRemove(node)).toList();
        return candidates;
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

    private int[] countInEachGroup(Collection<NodeCandidate> candidates) {
        int[] countInGroup = new int[requested.groups()];
        for (var candidate : candidates) {
            if (candidate.allocation().get().membership().retired()) continue;
            var currentGroup = candidate.allocation().get().membership().cluster().group();
            if (currentGroup.isEmpty()) continue;
            if (currentGroup.get().index() >= requested.groups()) continue;
            countInGroup[currentGroup.get().index()]++;
        }
        return countInGroup;
    }

    /** Assign a group to new or to be reactivated nodes. */
    private NodeCandidate assignGroupToNewNode(NodeCandidate candidate, int[] countInGroup) {
        if (candidate.state() == Node.State.active && candidate.allocation().get().membership().retired()) return candidate;
        if (candidate.state() == Node.State.active && candidate.allocation().get().membership().cluster().group().isPresent()) return candidate;
        return inFirstGroupWithDeficiency(candidate, countInGroup);
    }

    private NodeCandidate moveNodeInSurplusGroup(NodeCandidate candidate, int[] countInGroup) {
        var currentGroup = candidate.allocation().get().membership().cluster().group();
        if (currentGroup.isEmpty()) return candidate;
        if (currentGroup.get().index() < requested.groups()) return candidate;
        return inFirstGroupWithDeficiency(candidate, countInGroup);
    }

    private NodeCandidate retireSurplusNodeInGroup(NodeCandidate candidate, int[] countInGroup) {
        if (candidate.allocation().get().membership().retired()) return candidate;
        var currentGroup = candidate.allocation().get().membership().cluster().group();
        if (currentGroup.isEmpty()) return candidate;
        if (currentGroup.get().index() >= requested.groups()) return candidate;
        if (requested.count().isEmpty()) return candidate; // Can't retire
        if (countInGroup[currentGroup.get().index()] <= requested.groupSize()) return candidate;
        countInGroup[currentGroup.get().index()]--;
        return candidate.withNode(candidate.toNode().retire(Agent.application, clock.instant()));
    }

    /** Unretire nodes that are already in the correct group when the group is deficient. */
    private NodeCandidate unretireNodeInExpandedGroup(NodeCandidate candidate, int[] countInGroup) {
        if ( ! candidate.allocation().get().membership().retired()) return candidate;
        var currentGroup = candidate.allocation().get().membership().cluster().group();
        if (currentGroup.isEmpty()) return candidate;
        if (currentGroup.get().index() >= requested.groups()) return candidate;
        if (candidate.preferToRetire() || candidate.wantToRetire()) return candidate;
        if (requested.count().isPresent() && countInGroup[currentGroup.get().index()] >= requested.groupSize()) return candidate;
        candidate = unretire(candidate);
        if (candidate.allocation().get().membership().retired()) return candidate;
        countInGroup[currentGroup.get().index()]++;
        return candidate;
    }

    private NodeCandidate inFirstGroupWithDeficiency(NodeCandidate candidate, int[] countInGroup) {
        for (int group = 0; group < requested.groups(); group++) {
            if (requested.count().isEmpty() || countInGroup[group] < requested.groupSize()) {
                return inGroup(group, candidate, countInGroup);
            }
        }
        return candidate;
    }

    private boolean shouldRemove(NodeCandidate candidate) {
        var currentGroup = candidate.allocation().get().membership().cluster().group();
        if (currentGroup.isEmpty()) return true; // new and not assigned an index: Not needed
        return currentGroup.get().index() >= requested.groups();
    }

    private NodeCandidate inGroup(int group, NodeCandidate candidate, int[] countInGroup) {
        candidate = unretire(candidate);
        if (candidate.allocation().get().membership().retired()) return candidate;
        var membership = candidate.allocation().get().membership();
        var currentGroup = membership.cluster().group();
        countInGroup[group]++;
        if ( ! currentGroup.isEmpty() && currentGroup.get().index() < requested.groups())
            countInGroup[membership.cluster().group().get().index()]--;
        return candidate.withNode(candidate.toNode().with(candidate.allocation().get().with(membership.with(membership.cluster().with(Optional.of(ClusterSpec.Group.from(group)))))));
    }

    /** Attempt to unretire the given node if it is retired. */
    private NodeCandidate unretire(NodeCandidate candidate) {
        if (candidate.retiredNow()) return candidate;
        if ( ! candidate.allocation().get().membership().retired()) return candidate;
        if ( ! hasCompatibleResources(candidate) ) return candidate;
        var parent = candidate.parentHostname().flatMap(hostname -> allNodes.node(hostname));
        if (parent.isPresent() && (parent.get().status().wantToRetire() || parent.get().status().preferToRetire())) return candidate;
        candidate = candidate.withNode();
        if ( ! requested.isCompatible(candidate.resources()))
            candidate = candidate.withNode(resize(candidate.toNode()));
        return candidate.withNode(candidate.toNode().unretire());
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
