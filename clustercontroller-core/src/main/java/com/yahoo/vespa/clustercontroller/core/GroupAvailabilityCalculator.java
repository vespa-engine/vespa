package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.distribution.Group;
import com.yahoo.vdslib.distribution.GroupVisitor;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

class GroupAvailabilityCalculator {
    private final Distribution distribution;
    private final double minNodeRatioPerGroup;

    private GroupAvailabilityCalculator(Distribution distribution,
                                        double minNodeRatioPerGroup)
    {
        this.distribution = distribution;
        this.minNodeRatioPerGroup = minNodeRatioPerGroup;
    }

    public static class Builder {
        private Distribution distribution;
        private double minNodeRatioPerGroup = 1.0;

        Builder withDistribution(Distribution distribution) {
            this.distribution = distribution;
            return this;
        }
        Builder withMinNodeRatioPerGroup(double minRatio) {
            this.minNodeRatioPerGroup = minRatio;
            return this;
        }
        GroupAvailabilityCalculator build() {
            return new GroupAvailabilityCalculator(distribution, minNodeRatioPerGroup);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private class InsufficientAvailabilityGroupVisitor implements GroupVisitor {
        private final Set<Integer> implicitlyDown = new HashSet<>();
        private final ClusterState clusterState;

        public InsufficientAvailabilityGroupVisitor(ClusterState clusterState) {
            this.clusterState = clusterState;
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

        private double computeGroupAvailability(Group g) {
            // TODO also look at distributors
            final long availableNodes = availableNodesIn(g).count();
            // Model should make it impossible to deploy with zero nodes in a group,
            // so no div by zero risk.
            return availableNodes / (double)g.getNodes().size();
        }

        private void markAllAvailableGroupNodeIndicesAsDown(Group group) {
            candidateNodesForSettingDown(group).forEach(n -> implicitlyDown.add(n.index()));
        }

        @Override
        public boolean visitGroup(Group group) {
            if (group.isLeafGroup()) {
                if (computeGroupAvailability(group) < minNodeRatioPerGroup) {
                    markAllAvailableGroupNodeIndicesAsDown(group);
                }
            }
            return true;
        }

        Set<Integer> implicitlyDownNodeIndices() {
            return implicitlyDown;
        }
    }

    private static boolean isFlatCluster(Group root) {
        return root.isLeafGroup();
    }

    public Set<Integer> nodesThatShouldBeDown(ClusterState state) {
        if (distribution == null) { // FIXME: for tests that don't set distribution properly!
            return Collections.emptySet();
        }
        if (isFlatCluster(distribution.getRootGroup())) {
            // Implicit group takedown only applies to hierarchic cluster setups.
            return new HashSet<>();
        }
        InsufficientAvailabilityGroupVisitor visitor = new InsufficientAvailabilityGroupVisitor(state);
        distribution.visitGroups(visitor);
        return visitor.implicitlyDownNodeIndices();
    }

}
