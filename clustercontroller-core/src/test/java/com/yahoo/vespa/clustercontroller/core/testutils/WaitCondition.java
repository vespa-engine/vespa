// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.testutils;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.AnnotatedClusterState;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.DummyVdsNode;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.listeners.SystemStateListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* @author Haakon Humberset
*/
public interface WaitCondition {

    /** Return null if met, why not if it is not met. */
    String isConditionMet();

    abstract class StateWait implements WaitCondition {
        private final Object monitor;
        protected ClusterState currentState;
        ClusterState convergedState;
        private final SystemStateListener listener = new SystemStateListener() {
            @Override
            public void handleNewPublishedState(ClusterStateBundle state) {
                synchronized (monitor) {
                    currentState = state.getBaselineClusterState();
                    monitor.notifyAll();
                }
            }

            @Override
            public void handleStateConvergedInCluster(ClusterStateBundle states) {
                synchronized (monitor) {
                    currentState = convergedState = states.getBaselineClusterState();
                    monitor.notifyAll();
                }
            }
        };

        protected StateWait(FleetController fc, Object monitor) {
            this.monitor = monitor;
            synchronized (this.monitor) {
                fc.addSystemStateListener(listener);
            }
        }

        ClusterState getCurrentState() {
            synchronized (monitor) {
                return currentState;
            }
        }
    }

    class RegexStateMatcher extends StateWait {

        private final Pattern pattern;
        private Collection<DummyVdsNode> nodesToCheck = Set.of();
        private ClusterState lastCheckedState;
        private boolean checkAllSpaces = false;
        private Set<String> checkSpaceSubset = Set.of();

        RegexStateMatcher(String regex, FleetController fc, Object monitor) {
            super(fc, monitor);
            pattern = Pattern.compile(regex);
        }

        RegexStateMatcher includeNotifyingNodes(Collection<DummyVdsNode> nodes) {
            Objects.requireNonNull(nodes, "nodes must be non-null");
            nodesToCheck = nodes;
            return this;
        }

        RegexStateMatcher checkAllSpaces(boolean checkAllSpaces) {
            this.checkAllSpaces = checkAllSpaces;
            return this;
        }

        RegexStateMatcher checkSpaceSubset(Set<String> spaces) {
            Objects.requireNonNull(spaces, "spaces must be non-null");
            this.checkSpaceSubset = spaces;
            return this;
        }

        private static List<ClusterState> statesInBundle(ClusterStateBundle bundle) {
            List<ClusterState> states = new ArrayList<>(3);
            states.add(bundle.getBaselineClusterState());
            bundle.getDerivedBucketSpaceStates().forEach((space, state) -> states.add(state.getClusterState()));
            return states;
        }

        @Override
        public String isConditionMet() {
            if (convergedState == null) return "No cluster state defined yet";

            lastCheckedState = convergedState;
            Matcher m = pattern.matcher(lastCheckedState.toString());
            if (!m.matches() && checkSpaceSubset.isEmpty()) return "Cluster state mismatch";

            for (DummyVdsNode node : nodesToCheck) {
                if (node.getClusterState() == null) return "Node " + node + " has not received a cluster state yet";

                boolean match;
                if (checkAllSpaces) {
                    match = statesInBundle(node.getClusterStateBundle()).stream()
                                                                        .allMatch(state -> pattern
                                                                                .matcher(withoutTimestamps(state.toString()))
                                                                                .matches());
                } else if (!checkSpaceSubset.isEmpty()) {
                    match = checkSpaceSubset.stream().allMatch(space -> {
                        String state = node.getClusterStateBundle().getDerivedBucketSpaceStates()
                                           .getOrDefault(space, AnnotatedClusterState.emptyState()).getClusterState().toString();
                        return pattern.matcher(withoutTimestamps(state)).matches();
                    });
                } else {
                    match = pattern.matcher(withoutTimestamps(node.getClusterState().toString())).matches();
                }

                if (!match) {
                    return "Node " + node + " state mismatch.\n  wanted: " + pattern + "\n  is:     " + node.getClusterStateBundle().toString();
                }
                if (!node.hasPendingGetNodeStateRequest()) {
                    return "Node " + node + " has not received another get node state request yet";
                }
            }
            return null;
        }

        /** Returns the given state string with timestamps removed */
        private String withoutTimestamps(String state) {
            String[] parts = state.split(" ");
            StringBuilder b = new StringBuilder();
            for (String part : parts) {
                if ( ! part.contains(".t"))
                    b.append(part).append(" ");
            }
            if (b.length() > 0)
                b.setLength(b.length() - 1);
            return b.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("RegexStateMatcher:")
              .append("\n  wanted:       '").append(pattern.pattern()).append("'")
              .append("\n  last checked: '").append(lastCheckedState).append("'")
              .append("\n  current:      '").append(currentState).append("'");
            return sb.toString();
        }
    }

    class InitProgressPassedMatcher extends StateWait {
        private final Node node;
        private final double minProgress;

        InitProgressPassedMatcher(Node n, double minProgress, FleetController fc, Object monitor) {
            super(fc, monitor);
            this.node = n;
            this.minProgress = minProgress;
        }

        @Override
        public String isConditionMet() {
            if (currentState == null) {
                return "No cluster state defined yet";
            }
            double currentProgress = currentState.getNodeState(node).getInitProgress();
            if (currentProgress < minProgress) {
                return "Current progress of node " + node + " at " + currentProgress + " is less than wanted progress of " + minProgress;
            }
            return null;
        }

        @Override
        public String toString() {
            return "InitProgressPassedMatcher(" + node + ", " + minProgress + ")";
        }
    }

    class MinUsedBitsMatcher extends StateWait {
        private final int bitCount;
        private final int nodeCount;

        MinUsedBitsMatcher(int bitCount, int nodeCount, FleetController fc, Object monitor) {
            super(fc, monitor);
            this.bitCount = bitCount;
            this.nodeCount = nodeCount;
        }

        @Override
        public String isConditionMet() {
            if (currentState == null) {
                return "No cluster state defined yet";
            }
            int nodebitcount = 0;
            for (NodeType type : NodeType.getTypes()) {
                int nodeCount = currentState.getNodeCount(type);
                for (int i=0; i<nodeCount; ++i) {
                    if (currentState.getNodeState(new Node(type, i)).getMinUsedBits() == bitCount) {
                        ++nodebitcount;
                    }
                }
            }
            if (nodebitcount == nodeCount) return null;
            return "Currently, " + nodebitcount + " and not " + nodeCount + " nodes have " + bitCount + " min bits used set";
        }

        @Override
        public String toString() { return "MinUsedBitsMatcher(" + bitCount + ", " + nodeCount + ")"; }
    }

}
