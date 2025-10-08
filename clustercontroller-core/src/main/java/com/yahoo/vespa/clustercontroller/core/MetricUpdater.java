// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.utils.util.ComponentMetricReporter;
import com.yahoo.vespa.clustercontroller.utils.util.MetricReporter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.CLUSTER;
import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.CLUSTER_ID;
import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.CONTROLLER_INDEX;
import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.DID_WORK;
import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.NODE_TYPE;
import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.WORK_ID;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.AGREED_MASTER_VOTES;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.AVAILABLE_NODES_RATIO;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.BUSY_TICK_TIME_MS;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.CLUSTER_BUCKETS_OUT_OF_SYNC_RATIO;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.CLUSTER_CONTROLLER;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.CLUSTER_STATE_CHANGE;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.IDLE_TICK_TIME_MS;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.IS_MASTER;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.NODES_NOT_CONVERGED;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.NODE_EVENT;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.REMOTE_TASK_QUEUE_SIZE;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.DISK_LIMIT;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.MAX_DISK_UTILIZATION;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.MAX_MEMORY_UTILIZATION;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.MEMORY_LIMIT;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.NODES_ABOVE_LIMIT;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.STORED_DOCUMENT_BYTES;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.STORED_DOCUMENT_COUNT;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.WORK_MS;

public class MetricUpdater {

    private final ComponentMetricReporter metricReporter;
    private final Timer timer;
    // Publishing and converging on a cluster state version is never instant nor atomic, but
    // it usually completes within a few seconds. If convergence does not happen for more than
    // 30 seconds, it's a sign something has stalled.
    private Duration stateVersionConvergenceGracePeriod = Duration.ofSeconds(30);

    public MetricUpdater(MetricReporter metricReporter, Timer timer, int controllerIndex, String clusterName) {
        this.metricReporter = new ComponentMetricReporter(metricReporter, "%s.".formatted(CLUSTER_CONTROLLER));
        this.metricReporter.addDimension(CONTROLLER_INDEX, String.valueOf(controllerIndex));
        this.metricReporter.addDimension(CLUSTER, clusterName);
        this.metricReporter.addDimension(CLUSTER_ID, clusterName);
        this.timer = timer;
    }

    public MetricReporter.Context createContext(Map<String, String> dimensions) {
        return metricReporter.createContext(dimensions);
    }

    public void setStateVersionConvergenceGracePeriod(Duration gracePeriod) {
        stateVersionConvergenceGracePeriod = gracePeriod;
    }

    private static int nodesInAvailableState(Map<State, Integer> nodeCounts) {
        return nodeCounts.getOrDefault(State.INITIALIZING, 0)
                + nodeCounts.getOrDefault(State.RETIRED, 0)
                + nodeCounts.getOrDefault(State.UP, 0)
                // Even though technically not true, we here treat Maintenance as an available state to
                // avoid triggering false alerts when a node is taken down transiently in an orchestrated manner.
                + nodeCounts.getOrDefault(State.MAINTENANCE, 0);
    }

    public void updateClusterStateMetrics(ContentCluster cluster, ClusterState state,
                                          ResourceUsageStats resourceUsage, Instant lastStateBroadcastTimePoint) {
        Map<String, String> dimensions = new HashMap<>();
        Instant now = timer.getCurrentWallClockTime();
        // NodeInfo::getClusterStateVersionBundleAcknowledged() returns -1 if the node has not yet ACKed a
        // cluster state version. Check for this version explicitly if we've yet to publish a state. This
        // will prevent the node from being erroneously counted as divergent (can't reasonably diverge from
        // something that doesn't exist...!).
        int effectiveStateVersion = (state.getVersion() > 0) ? state.getVersion() : -1;
        boolean convergenceDeadlinePassed = lastStateBroadcastTimePoint.plus(stateVersionConvergenceGracePeriod).isBefore(now);
        for (NodeType type : NodeType.getTypes()) {
            dimensions.put(NODE_TYPE, type.toString().toLowerCase());
            MetricReporter.Context context = createContext(dimensions);
            Map<State, Integer> nodeCounts = new HashMap<>();
            for (State s : State.values()) {
                nodeCounts.put(s, 0);
            }
            int nodesNotConverged = 0;
            for (Integer i : cluster.getConfiguredNodes().keySet()) {
                var node = new Node(type, i);
                NodeState s = state.getNodeState(node);
                Integer count = nodeCounts.get(s.getState());
                nodeCounts.put(s.getState(), count + 1);
                var info = cluster.getNodeInfo(node);
                if (info != null && convergenceDeadlinePassed && s.getState().oneOf("uir")) {
                    if (info.getClusterStateVersionBundleAcknowledged() != effectiveStateVersion) {
                        nodesNotConverged++;
                    }
                }
            }
            for (State s : State.values()) {
                String name = s.toString().toLowerCase() + ".count";
                metricReporter.set(name, nodeCounts.get(s), context);
            }

            final int availableNodes = nodesInAvailableState(nodeCounts);
            final int totalNodes = Math.max(cluster.getConfiguredNodes().size(), 1); // Assumes 1-1 between distributor and storage
            metricReporter.set(AVAILABLE_NODES_RATIO, (double)availableNodes / totalNodes, context);
            metricReporter.set(NODES_NOT_CONVERGED, nodesNotConverged, context);
        }
        dimensions.remove(NODE_TYPE);
        MetricReporter.Context context = createContext(dimensions);
        metricReporter.add(CLUSTER_STATE_CHANGE, 1, context);

        metricReporter.set(MAX_DISK_UTILIZATION, resourceUsage.getMaxDiskUtilization(), context);
        metricReporter.set(MAX_MEMORY_UTILIZATION, resourceUsage.getMaxMemoryUtilization(), context);
        metricReporter.set(NODES_ABOVE_LIMIT, resourceUsage.getNodesAboveLimit(), context);
        metricReporter.set(DISK_LIMIT, resourceUsage.getDiskLimit(), context);
        metricReporter.set(MEMORY_LIMIT, resourceUsage.getMemoryLimit(), context);
    }

    public void updateMasterElectionMetrics(Map<Integer, Integer> data) {
        Map<Integer, Integer> voteCounts = new HashMap<>();
        for(Integer i : data.values()) {
            int count = (voteCounts.get(i) == null ? 0 : voteCounts.get(i));
            voteCounts.put(i, count + 1);
        }
        SortedSet<Integer> counts = new TreeSet<>(voteCounts.values());
        if (counts.size() > 1 && counts.first() > counts.last()) {
            throw new IllegalStateException("Assumed smallest count is sorted first");
        }
        int maxCount = counts.isEmpty() ? 0 : counts.last();
        metricReporter.set(AGREED_MASTER_VOTES, maxCount);
    }

    public void updateMasterState(boolean isMaster) {
        metricReporter.set(IS_MASTER, isMaster ? 1 : 0);
        if (!isMaster) {
            // Metric gauge values are "sticky" once set, which potentially causes
            // max-aggregation of metrics across cluster controllers to return stale
            // and unexpected values unless we explicitly zero out metrics when
            // leadership is lost.
            resetNodeStateAndResourceUsageMetricsToZero();
        }
    }

    private void resetNodeStateAndResourceUsageMetricsToZero() {
        for (NodeType type : NodeType.getTypes()) {
            Map<String, String> dimensions = Map.of(NODE_TYPE, type.toString().toLowerCase());
            MetricReporter.Context context = createContext(dimensions);
            for (State s : State.values()) {
                String name = s.toString().toLowerCase() + ".count";
                metricReporter.set(name, 0, context);
            }
            metricReporter.set(NODES_NOT_CONVERGED, 0, context);
        }
    }

    public void updateClusterBucketsOutOfSyncRatio(double ratio) {
        metricReporter.set(CLUSTER_BUCKETS_OUT_OF_SYNC_RATIO, ratio);
    }

    public void updateClusterDocumentMetrics(long docsTotal, long bytesTotal) {
        metricReporter.set(STORED_DOCUMENT_COUNT, docsTotal);
        metricReporter.set(STORED_DOCUMENT_BYTES, bytesTotal);
    }

    public void addTickTime(long millis, boolean didWork) {
        if (didWork) {
            metricReporter.set(BUSY_TICK_TIME_MS, millis);
        } else {
            metricReporter.set(IDLE_TICK_TIME_MS, millis);
        }
    }

    public void recordNewNodeEvent() {
        // TODO(hakonhall): Replace add() with a persistent aggregate metric.
        metricReporter.add(NODE_EVENT, 1);
    }

    public void updateRemoteTaskQueueSize(int size) {
        metricReporter.set(REMOTE_TASK_QUEUE_SIZE, size);
    }

    public boolean forWork(String workId, BooleanSupplier work) {
        long startNanos = System.nanoTime();
        boolean didWork = work.getAsBoolean();
        double seconds = Duration.ofNanos(System.nanoTime() - startNanos).toMillis() / 1000.;

        MetricReporter.Context context = createContext(Map.of(DID_WORK, Boolean.toString(didWork),
                                                              WORK_ID, workId));
        metricReporter.set(WORK_MS, seconds, context);

        return didWork;
    }
}
