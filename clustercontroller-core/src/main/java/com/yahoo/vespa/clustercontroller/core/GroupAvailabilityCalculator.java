// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.distribution.Group;
import com.yahoo.vdslib.distribution.GroupVisitor;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class GroupAvailabilityCalculator {

    private final Distribution distribution;
    private final double minNodeRatioPerGroup;
    private final int safeMaintenanceGroupThreshold;
    private final List<Integer> nodesSafelySetToMaintenance;

    private GroupAvailabilityCalculator(Distribution distribution,
                                        double minNodeRatioPerGroup,
                                        int safeMaintenanceGroupThreshold,
                                        List<Integer> nodesSafelySetToMaintenance) {
        this.distribution = Objects.requireNonNull(distribution, "distribution must be non-null");
        this.minNodeRatioPerGroup = minNodeRatioPerGroup;
        this.safeMaintenanceGroupThreshold = safeMaintenanceGroupThreshold;
        this.nodesSafelySetToMaintenance = nodesSafelySetToMaintenance;
    }

    public static class Builder {
        private Distribution distribution;
        private double minNodeRatioPerGroup = 1.0;
        private int safeMaintenanceGroupThreshold = 2;
        private final List<Integer> nodesSafelySetToMaintenance = new ArrayList<>();

        Builder withDistribution(Distribution distribution) {
            this.distribution = distribution;
            return this;
        }
        Builder withMinNodeRatioPerGroup(double minRatio) {
            this.minNodeRatioPerGroup = minRatio;
            return this;
        }
        /**
         * If the number of nodes safely set to maintenance is at least this number, the remaining
         * nodes in the group will be set to maintenance (storage nodes) or down (distributors).
         *
         * <p>This feature is disabled if safeMaintenanceGroupThreshold is 0 (not default).</p>
         */
        Builder withSafeMaintenanceGroupThreshold(int safeMaintenanceGroupThreshold) {
            this.safeMaintenanceGroupThreshold = safeMaintenanceGroupThreshold;
            return this;
        }
        Builder withNodesSafelySetToMaintenance(List<Integer> nodesSafelySetToMaintenance) {
            this.nodesSafelySetToMaintenance.addAll(nodesSafelySetToMaintenance);
            return this;
        }
        GroupAvailabilityCalculator build() {
            return new GroupAvailabilityCalculator(distribution, minNodeRatioPerGroup,
                    safeMaintenanceGroupThreshold, nodesSafelySetToMaintenance);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private class InsufficientAvailabilityGroupVisitor implements GroupVisitor {
        private final Set<Integer> implicitlyMaintained = new HashSet<>();
        private final Set<Integer> implicitlyDown = new HashSet<>();
        private final ClusterState clusterState;
        private final Set<Integer> nodesSafelySetToMaintenance;
        private final int safeMaintenanceGroupThreshold;

        public InsufficientAvailabilityGroupVisitor(ClusterState clusterState,
                                                    List<Integer> nodesSafelySetToMaintenance,
                                                    int safeMaintenanceGroupThreshold) {
            this.clusterState = clusterState;
            this.nodesSafelySetToMaintenance = Set.copyOf(nodesSafelySetToMaintenance);
            this.safeMaintenanceGroupThreshold = safeMaintenanceGroupThreshold;
        }

        private boolean nodeIsAvailableInState(final int index, final String states) {
            return clusterState.getNodeState(new Node(NodeType.STORAGE, index)).getState().oneOf(states);
        }

        private Stream<ConfiguredNode> availableNodesIn(Group g) {
            // We consider nodes in states (u)p, (i)nitializing, (m)aintenance as being
            // available from the perspective of taking entire groups down (even though
            // maintenance mode is a half-truth in this regard).
            return g.getNodes().stream().filter(n -> nodeIsAvailableInState(n.index(), "uim"));
        }

        private Stream<ConfiguredNode> candidateNodesForSettingDown(Group g) {
            // We don't implicitly set (m)aintenance nodes down, as these are usually set
            // in maintenance for a good reason (e.g. orchestration or manual reboot).
            // Similarly, we don't take down (r)etired nodes as these may contain data
            // that the rest of the cluster needs.
            return g.getNodes().stream().filter(n -> nodeIsAvailableInState(n.index(), "ui"));
        }

        private Stream<ConfiguredNode> candidateNodesForSettingMaintenance(Group g) {
            // Most states should be set in maintenance, e.g. retirement may take a long time,
            // so force maintenance to allow upgrades.
            return g.getNodes().stream()
                    // "m" is NOT included since that would be a no-op.
                    .filter(n -> nodeIsAvailableInState(n.index(), "uird"));
        }

        private double computeGroupAvailability(Group g) {
            // TODO also look at distributors
            final long availableNodes = availableNodesIn(g).count();
            // Model should make it impossible to deploy with zero nodes in a group,
            // so no div by zero risk.
            return availableNodes / (double)g.getNodes().size();
        }

        private int computeNodesSafelySetToMaintenance(Group group) {
            Set<ConfiguredNode> nodesInGroupSafelySetToMaintenance = group.getNodes().stream()
                    .filter(configuredNode -> nodesSafelySetToMaintenance.contains(configuredNode.index()))
                    .collect(Collectors.toSet());

            return nodesInGroupSafelySetToMaintenance.size();
        }

        private void markAllAvailableGroupNodeIndicesAsDown(Group group) {
            candidateNodesForSettingDown(group).forEach(n -> implicitlyDown.add(n.index()));
        }

        private void markAllAvailableGroupNodeIndicesAsMaintained(Group group) {
            candidateNodesForSettingMaintenance(group).forEach(n -> implicitlyMaintained.add(n.index()));
        }

        @Override
        public boolean visitGroup(Group group) {
            if (group.isLeafGroup()) {
                if (safeMaintenanceGroupThreshold > 0 &&
                        computeNodesSafelySetToMaintenance(group) >= safeMaintenanceGroupThreshold) {
                    markAllAvailableGroupNodeIndicesAsMaintained(group);
                } else if (computeGroupAvailability(group) < minNodeRatioPerGroup) {
                    markAllAvailableGroupNodeIndicesAsDown(group);
                }
            }
            return true;
        }

        Result result() {
            var intersection = new HashSet<>(implicitlyMaintained);
            intersection.retainAll(implicitlyDown);
            if (intersection.size() > 0) {
                throw new IllegalStateException("Nodes implicitly both maintenance and down: " + intersection);
            }

            return new Result(implicitlyMaintained, implicitlyDown);
        }
    }

    private static boolean isFlatCluster(Group root) {
        return root.isLeafGroup();
    }

    public static class Result {
        private final Set<Integer> shouldBeMaintained;
        private final Set<Integer> shouldBeDown;

        public Result() { this(Set.of(), Set.of()); }

        public Result(Set<Integer> shouldBeMaintained, Set<Integer> shouldBeDown) {
            this.shouldBeMaintained = Set.copyOf(shouldBeMaintained);
            this.shouldBeDown = Set.copyOf(shouldBeDown);
        }

        public Set<Integer> nodesThatShouldBeMaintained() { return shouldBeMaintained; }
        public Set<Integer> nodesThatShouldBeDown() { return shouldBeDown; }
    }

    public Result calculate(ClusterState state) {
        if (isFlatCluster(distribution.getRootGroup())) {
            // Implicit group takedown only applies to hierarchic cluster setups.
            return new Result();
        }
        InsufficientAvailabilityGroupVisitor visitor = new InsufficientAvailabilityGroupVisitor(
                state, nodesSafelySetToMaintenance, safeMaintenanceGroupThreshold);
        distribution.visitGroups(visitor);
        return visitor.result();
    }

    public Set<Integer> nodesThatShouldBeDown(ClusterState state) {
        return calculate(state).nodesThatShouldBeDown();
    }

}
