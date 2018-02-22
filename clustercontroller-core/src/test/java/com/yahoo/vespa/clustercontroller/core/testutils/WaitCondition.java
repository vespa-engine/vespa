// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.testutils;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.DummyVdsNode;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.listeners.SystemStateListener;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* @author <a href="mailto:humbe@yahoo-inc.com">Haakon Humberset</a>
*/
public interface WaitCondition {

    /** Return null if met, why not if it is not met. */
    public String isConditionMet();

    public abstract class StateWait implements WaitCondition {
        private final Object monitor;
        protected ClusterState currentState;
        private final SystemStateListener listener = new SystemStateListener() {
            @Override
            public void handleNewSystemState(ClusterStateBundle state) {
                synchronized (monitor) {
                    currentState = state.getBaselineClusterState();
                    monitor.notifyAll();
                }
            }
        };

        public StateWait(FleetController fc, Object monitor) {
            this.monitor = monitor;
            fc.addSystemStateListener(listener);
        }

        public ClusterState getCurrentState() {
            synchronized (monitor) {
                return currentState;
            }
        }
    }

    public class RegexStateMatcher extends StateWait {

        private final Pattern pattern;
        private Collection<DummyVdsNode> nodesToCheck;
        private ClusterState lastCheckedState;

        public RegexStateMatcher(String regex, FleetController fc, Object monitor) {
            super(fc, monitor);
            pattern = Pattern.compile(regex);
        }

        public RegexStateMatcher includeNotifyingNodes(Collection<DummyVdsNode> nodes) {
            nodesToCheck = nodes;
            return this;
        }

        @Override
        public String isConditionMet() {
            if (currentState != null) {
                lastCheckedState = currentState;
                Matcher m = pattern.matcher(lastCheckedState.toString());
                if (m.matches()) {
                    if (nodesToCheck != null) {
                        for (DummyVdsNode node : nodesToCheck) {
                            if (node.getClusterState() == null) {
                                return "Node " + node + " has not received a cluster state yet";
                            }
                            if (! pattern.matcher(withoutTimestamps(node.getClusterState().toString())).matches()) {
                                return "Node " + node + " state mismatch.\n  wanted: " + pattern + "\n  is:     " + node.getClusterState().toString();
                            }
                            if (node.getStateCommunicationVersion() > 0) {
                                if (!node.hasPendingGetNodeStateRequest()) {
                                    return "Node " + node + " has not received another get node state request yet";
                                }
                            }
                        }
                    }
                    return null;
                }
                return "Cluster state mismatch";
            }
            return "No cluster state defined yet";
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
            sb.append("RegexStateMatcher(\n  wanted:  '").append(pattern.pattern())
              .append("'\n  last checked: '").append(lastCheckedState).append("'")
              .append("'\n  current: '").append(currentState).append(")");
            return sb.toString();
        }
    }

    public class InitProgressPassedMatcher extends StateWait {
        private final Node node;
        private final double minProgress;

        public InitProgressPassedMatcher(Node n, double minProgress, FleetController fc, Object monitor) {
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

    public static class MinUsedBitsMatcher extends StateWait {
        private final int bitCount;
        private final int nodeCount;

        public MinUsedBitsMatcher(int bitCount, int nodeCount, FleetController fc, Object monitor) {
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
