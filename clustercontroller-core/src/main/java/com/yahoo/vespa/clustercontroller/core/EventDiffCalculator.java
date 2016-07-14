// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for inferring the difference between two cluster states and their
 * state annotations and producing a set of events that describe the changes between
 * the two. Diffing the states directly provides a clear picture of _what_ has changed,
 * while the annotations are generally required to explain _why_ the changes happened
 * in the first place.
 *
 * Events are primarily used for administrative/user visibility into what's happening
 * in the cluster and are output to the Vespa log as well as kept in a circular history
 * buffer per node and for the cluster as a whole.
 */
// ... class name is a work in progress
public class EventDiffCalculator {

    static class Params {
        ContentCluster cluster;
        AnnotatedClusterState previousClusterState;
        AnnotatedClusterState currentClusterState;
        long currentTime;

        public Params cluster(ContentCluster cluster) {
            this.cluster = cluster;
            return this;
        }
        public Params previousClusterState(AnnotatedClusterState clusterState) {
            this.previousClusterState = clusterState;
            return this;
        }
        public Params currentClusterState(AnnotatedClusterState clusterState) {
            this.currentClusterState = clusterState;
            return this;
        }
        public Params currentTime(long time) {
            this.currentTime = time;
            return this;
        }
    }

    public static List<Event> computeEventDiff(final Params params) {
        final ClusterState prevState = params.previousClusterState.getClusterState();
        final ClusterState currentState = params.currentClusterState.getClusterState();
        final List<Event> events = new ArrayList<>();

        emitPerNodeDiffEvents(params, events);
        emitWholeClusterDiffEvent(prevState, currentState, events);

        return events;
    }

    private static void emitWholeClusterDiffEvent(ClusterState prevState,
                                                  ClusterState currentState,
                                                  List<Event> events)
    {
        if (clusterHasTransitionedToUpState(prevState, currentState)) {
            events.add(new ClusterEvent(ClusterEvent.Type.SYSTEMSTATE/*TODO TEST*/,
                    "Enough nodes available for system to become up", 0/*FIXME*/));
        }
    }

    private static void emitPerNodeDiffEvents(final Params params, List<Event> events) {
        final ContentCluster cluster = params.cluster;
        final ClusterState prevState = params.previousClusterState.getClusterState();
        final ClusterState currentState = params.currentClusterState.getClusterState();

        // TODO refactor!
        for (ConfiguredNode node : cluster.getConfiguredNodes().values()) {
            for (NodeType nodeType : NodeType.getTypes()) {
                final Node n = new Node(nodeType, node.index());
                final NodeState nodePrev = prevState.getNodeState(n);
                final NodeState nodeCurr = currentState.getNodeState(n);
                if (!nodeCurr.equals(nodePrev)) {
                    final NodeInfo info = cluster.getNodeInfo(n);
                    events.add(new NodeEvent(info,
                            String.format("Altered node state in cluster from '%s' to '%s'",
                                    nodePrev.toString(true), nodeCurr.toString(true)),
                            NodeEvent.Type.CURRENT, 0/*FIXME*/));

                    // TODO refactor!
                    NodeStateReason prevReason = params.previousClusterState.getNodeStateReasons().get(n);
                    NodeStateReason currReason = params.currentClusterState.getNodeStateReasons().get(n);
                    if (prevReason != NodeStateReason.GROUP_IS_DOWN && currReason == NodeStateReason.GROUP_IS_DOWN) {
                        events.add(new NodeEvent(info, "Setting node down as the total availability of its " +
                                "group is below the configured threshold",
                                NodeEvent.Type.CURRENT, 0/*FIXME*/));
                    } else if (prevReason == NodeStateReason.GROUP_IS_DOWN && currReason != NodeStateReason.GROUP_IS_DOWN) {
                        events.add(new NodeEvent(info, "Group node availability restored; taking node back up",
                                NodeEvent.Type.CURRENT, 0/*FIXME*/));
                    }
                }
            }
        }
    }

    private static boolean clusterHasTransitionedToUpState(ClusterState prevState, ClusterState currentState) {
        return prevState.getClusterState() != State.UP && currentState.getClusterState() == State.UP;
    }

    public static Params params() { return new Params(); }

}
