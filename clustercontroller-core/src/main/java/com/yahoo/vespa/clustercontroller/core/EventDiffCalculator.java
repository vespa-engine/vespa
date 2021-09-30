// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
public class EventDiffCalculator {

    static class Params {
        ContentCluster cluster;
        ClusterStateBundle fromState;
        ClusterStateBundle toState;
        long currentTime;
        long maxMaintenanceGracePeriodTimeMs;

        public Params cluster(ContentCluster cluster) {
            this.cluster = cluster;
            return this;
        }
        public Params fromState(ClusterStateBundle bundle) {
            this.fromState = bundle;
            return this;
        }
        public Params toState(ClusterStateBundle bundle) {
            this.toState = bundle;
            return this;
        }
        public Params currentTimeMs(long time) {
            this.currentTime = time;
            return this;
        }
        public Params maxMaintenanceGracePeriodTimeMs(long timeMs) {
            this.maxMaintenanceGracePeriodTimeMs = timeMs;
            return this;
        }
    }

    public static Params params() { return new Params(); }

    private static class PerStateParams {
        final ContentCluster cluster;
        final Optional<String> bucketSpace;
        final AnnotatedClusterState fromState;
        final AnnotatedClusterState toState;
        final ClusterStateBundle.FeedBlock feedBlockFrom;
        final ClusterStateBundle.FeedBlock feedBlockTo;
        final long currentTime;
        final long maxMaintenanceGracePeriodTimeMs;

        PerStateParams(ContentCluster cluster,
                       Optional<String> bucketSpace,
                       AnnotatedClusterState fromState,
                       AnnotatedClusterState toState,
                       ClusterStateBundle.FeedBlock feedBlockFrom,
                       ClusterStateBundle.FeedBlock feedBlockTo,
                       long currentTime,
                       long maxMaintenanceGracePeriodTimeMs) {
            this.cluster = cluster;
            this.bucketSpace = bucketSpace;
            this.fromState = fromState;
            this.toState = toState;
            this.feedBlockFrom = feedBlockFrom;
            this.feedBlockTo = feedBlockTo;
            this.currentTime = currentTime;
            this.maxMaintenanceGracePeriodTimeMs = maxMaintenanceGracePeriodTimeMs;
        }
    }

    public static List<Event> computeEventDiff(final Params params) {
        final List<Event> events = new ArrayList<>();

        emitPerNodeDiffEvents(createBaselineParams(params), events);
        emitWholeClusterDiffEvent(createBaselineParams(params), events);
        emitDerivedBucketSpaceStatesDiffEvents(params, events);
        return events;
    }

    private static PerStateParams createBaselineParams(Params params) {
        return new PerStateParams(params.cluster,
                Optional.empty(),
                params.fromState.getBaselineAnnotatedState(),
                params.toState.getBaselineAnnotatedState(),
                params.fromState.getFeedBlockOrNull(),
                params.toState.getFeedBlockOrNull(),
                params.currentTime,
                params.maxMaintenanceGracePeriodTimeMs);
    }

    private static void emitWholeClusterDiffEvent(final PerStateParams params, final List<Event> events) {
        final ClusterState fromState = params.fromState.getClusterState();
        final ClusterState toState = params.toState.getClusterState();

        if (clusterHasTransitionedToUpState(fromState, toState)) {
            events.add(createClusterEvent("Enough nodes available for system to become up", params));
        } else if (clusterHasTransitionedToDownState(fromState, toState)) {
            if (clusterDownBecause(params, ClusterStateReason.TOO_FEW_STORAGE_NODES_AVAILABLE)) {
                events.add(createClusterEvent("Too few storage nodes available in cluster. Setting cluster state down", params));
            } else if (clusterDownBecause(params, ClusterStateReason.TOO_FEW_DISTRIBUTOR_NODES_AVAILABLE)) {
                events.add(createClusterEvent("Too few distributor nodes available in cluster. Setting cluster state down", params));
            } else if (clusterDownBecause(params, ClusterStateReason.TOO_LOW_AVAILABLE_STORAGE_NODE_RATIO)) {
                events.add(createClusterEvent("Too low ratio of available storage nodes. Setting cluster state down", params));
            } else if (clusterDownBecause(params, ClusterStateReason.TOO_LOW_AVAILABLE_DISTRIBUTOR_NODE_RATIO)) {
                events.add(createClusterEvent("Too low ratio of available distributor nodes. Setting cluster state down", params));
            } else {
                events.add(createClusterEvent("Cluster is down", params));
            }
        }
        // TODO should we emit any events when description changes?
        if (feedBlockStateHasChanged(params)) {
            if (params.feedBlockTo != null) {
                events.add(createClusterEvent(String.format("Cluster feed blocked due to resource exhaustion: %s",
                        params.feedBlockTo.getDescription()), params));
            } else {
                events.add(createClusterEvent("Cluster feed no longer blocked", params));
            }
        }
    }

    private static boolean feedBlockStateHasChanged(PerStateParams params) {
        return ((params.feedBlockFrom == null) != (params.feedBlockTo == null));
    }

    private static ClusterEvent createClusterEvent(String description, PerStateParams params) {
        return new ClusterEvent(ClusterEvent.Type.SYSTEMSTATE, description, params.currentTime);
    }

    private static boolean clusterDownBecause(final PerStateParams params, ClusterStateReason wantedReason) {
        final Optional<ClusterStateReason> actualReason = params.toState.getClusterStateReason();
        return actualReason.isPresent() && actualReason.get().equals(wantedReason);
    }

    private static void emitPerNodeDiffEvents(final PerStateParams params, final List<Event> events) {
        final ContentCluster cluster = params.cluster;
        final ClusterState fromState = params.fromState.getClusterState();
        final ClusterState toState = params.toState.getClusterState();

        for (ConfiguredNode node : cluster.getConfiguredNodes().values()) {
            for (NodeType nodeType : NodeType.getTypes()) {
                final Node n = new Node(nodeType, node.index());
                emitSingleNodeEvents(params, events, cluster, fromState, toState, n);
            }
        }
        emitNodeResourceExhaustionEvents(params, events, cluster);
    }

    // Returns a - b as a set operation
    private static <T> Set<T> setSubtraction(Set<T> a, Set<T> b) {
        var ret = new HashSet<>(a);
        ret.removeAll(b);
        return ret;
    }

    private static void emitNodeResourceExhaustionEvents(PerStateParams params, List<Event> events, ContentCluster cluster) {
        // Feed block events are not ordered by node
        Set<NodeResourceExhaustion> fromBlockSet = params.feedBlockFrom != null ? params.feedBlockFrom.getConcreteExhaustions() : Collections.emptySet();
        Set<NodeResourceExhaustion> toBlockSet   = params.feedBlockTo   != null ? params.feedBlockTo.getConcreteExhaustions()   : Collections.emptySet();

        for (var ex : setSubtraction(fromBlockSet, toBlockSet)) {
            var info = cluster.getNodeInfo(ex.node);
            events.add(createNodeEvent(info, String.format("Removed resource exhaustion: %s", ex.toExhaustionRemovedDescription()), params));
        }
        for (var ex : setSubtraction(toBlockSet, fromBlockSet)) {
            var info = cluster.getNodeInfo(ex.node);
            events.add(createNodeEvent(info, String.format("Added resource exhaustion: %s", ex.toExhaustionAddedDescription()), params));
        }
    }

    private static void emitSingleNodeEvents(PerStateParams params, List<Event> events, ContentCluster cluster, ClusterState fromState, ClusterState toState, Node n) {
        final NodeState nodeFrom = fromState.getNodeState(n);
        final NodeState nodeTo = toState.getNodeState(n);
        if (!nodeTo.equals(nodeFrom)) {
            final NodeInfo info = cluster.getNodeInfo(n);
            NodeStateReason prevReason = params.fromState.getNodeStateReasons().get(n);
            NodeStateReason currReason = params.toState.getNodeStateReasons().get(n);
            // Add specific reason events for node edge _before_ the actual transition event itself.
            // This makes the timeline of events more obvious.
            if (isGroupDownEdge(prevReason, currReason)) {
                events.add(createNodeEvent(info, "Group node availability is below configured threshold", params));
            } else if (isGroupUpEdge(prevReason, currReason)) {
                events.add(createNodeEvent(info, "Group node availability has been restored", params));
            } else if (isMayHaveMergesPendingUpEdge(prevReason, currReason)) {
                events.add(createNodeEvent(info, "Node may have merges pending", params));
            } else if (isMayHaveMergesPendingDownEdge(prevReason, currReason)) {
                events.add(createNodeEvent(info, "Node no longer has merges pending", params));
            } else if (isMaintenanceGracePeriodExceededDownEdge(prevReason, currReason, nodeFrom, nodeTo)) {
                events.add(createNodeEvent(info, String.format("Exceeded implicit maintenance mode grace period of " +
                        "%d milliseconds. Marking node down.", params.maxMaintenanceGracePeriodTimeMs), params));
            }
            events.add(createNodeEvent(info, String.format("Altered node state in cluster state from '%s' to '%s'",
                    nodeFrom.toString(true), nodeTo.toString(true)), params));
        }
    }

    private static NodeEvent createNodeEvent(NodeInfo nodeInfo, String description, PerStateParams params) {
        if (params.bucketSpace.isPresent()) {
            return NodeEvent.forBucketSpace(nodeInfo, params.bucketSpace.get(), description, NodeEvent.Type.CURRENT, params.currentTime);
        } else {
            return NodeEvent.forBaseline(nodeInfo, description, NodeEvent.Type.CURRENT, params.currentTime);
        }
    }

    private static boolean isGroupUpEdge(NodeStateReason prevReason, NodeStateReason currReason) {
        return prevReason == NodeStateReason.GROUP_IS_DOWN && currReason != NodeStateReason.GROUP_IS_DOWN;
    }

    private static boolean isGroupDownEdge(NodeStateReason prevReason, NodeStateReason currReason) {
        return prevReason != NodeStateReason.GROUP_IS_DOWN && currReason == NodeStateReason.GROUP_IS_DOWN;
    }

    private static boolean isMayHaveMergesPendingUpEdge(NodeStateReason prevReason, NodeStateReason currReason) {
        return prevReason != NodeStateReason.MAY_HAVE_MERGES_PENDING && currReason == NodeStateReason.MAY_HAVE_MERGES_PENDING;
    }

    private static boolean isMayHaveMergesPendingDownEdge(NodeStateReason prevReason, NodeStateReason currReason) {
        return prevReason == NodeStateReason.MAY_HAVE_MERGES_PENDING && currReason != NodeStateReason.MAY_HAVE_MERGES_PENDING;
    }

    private static boolean isMaintenanceGracePeriodExceededDownEdge(NodeStateReason prevReason, NodeStateReason currReason,
                                                                    NodeState prevState, NodeState currState) {
        return (prevReason != NodeStateReason.NODE_NOT_BACK_UP_WITHIN_GRACE_PERIOD &&
                currReason == NodeStateReason.NODE_NOT_BACK_UP_WITHIN_GRACE_PERIOD &&
                prevState.getState() == State.MAINTENANCE &&
                currState.getState() == State.DOWN);
    }

    private static boolean clusterHasTransitionedToUpState(ClusterState prevState, ClusterState currentState) {
        return prevState.getClusterState() != State.UP && currentState.getClusterState() == State.UP;
    }

    private static boolean clusterHasTransitionedToDownState(ClusterState prevState, ClusterState currentState) {
        return prevState.getClusterState() != State.DOWN && currentState.getClusterState() == State.DOWN;
    }

    private static void emitDerivedBucketSpaceStatesDiffEvents(Params params, List<Event> events) {
        params.toState.getDerivedBucketSpaceStates().entrySet().forEach(toEntry -> {
            String toBucketSpace = toEntry.getKey();
            AnnotatedClusterState toDerivedState = toEntry.getValue();
            AnnotatedClusterState fromDerivedState = params.fromState.getDerivedBucketSpaceStates().get(toBucketSpace);
            if (fromDerivedState != null && shouldConsiderDerivedStates(params, fromDerivedState, toDerivedState)) {
                emitPerNodeDiffEvents(createDerivedParams(params, toBucketSpace, fromDerivedState, toDerivedState), events);
            }
        });
    }

    private static boolean shouldConsiderDerivedStates(Params params, AnnotatedClusterState fromDerivedState, AnnotatedClusterState toDerivedState) {
        return (!fromDerivedState.getClusterState().equals(params.fromState.getBaselineClusterState())) ||
                (!toDerivedState.getClusterState().equals(params.toState.getBaselineClusterState()));
    }

    private static PerStateParams createDerivedParams(Params params, String bucketSpace, AnnotatedClusterState fromDerivedState, AnnotatedClusterState toDerivedState) {
        return new PerStateParams(params.cluster,
                Optional.of(bucketSpace),
                fromDerivedState,
                toDerivedState,
                null, // Not used in per-space event derivation
                null, // Ditto
                params.currentTime,
                params.maxMaintenanceGracePeriodTimeMs);
    }

}
