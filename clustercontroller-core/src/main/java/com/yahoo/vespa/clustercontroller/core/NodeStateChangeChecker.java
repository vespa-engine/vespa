// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.hostinfo.Metrics;
import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNode;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checks if a node can be upgraded.
 *
 * @author Haakon Dybdahl
 */
public class NodeStateChangeChecker {
    public static final String LEGACY_BUCKETS_METRIC_NAME = "vds.datastored.alldisks.buckets";
    public static final String BUCKETS_METRIC_NAME = "vds.datastored.bucket_space.buckets_total";
    public static final Map<String, String> BUCKETS_METRIC_DIMENSIONS = Map.of("bucketSpace", "default");

    private final int minStorageNodesUp;
    private double minRatioOfStorageNodesUp;
    private final int requiredRedundancy;
    private final ClusterInfo clusterInfo;
    private final boolean determineBucketsFromBucketSpaceMetric;

    public NodeStateChangeChecker(
            int minStorageNodesUp,
            double minRatioOfStorageNodesUp,
            int requiredRedundancy,
            ClusterInfo clusterInfo,
            boolean determineBucketsFromBucketSpaceMetric) {
        this.minStorageNodesUp = minStorageNodesUp;
        this.minRatioOfStorageNodesUp = minRatioOfStorageNodesUp;
        this.requiredRedundancy = requiredRedundancy;
        this.clusterInfo = clusterInfo;
        this.determineBucketsFromBucketSpaceMetric = determineBucketsFromBucketSpaceMetric;
    }

    public static class Result {

        public enum Action {
            MUST_SET_WANTED_STATE,
            ALREADY_SET,
            DISALLOWED
        }

        private final Action action;
        private final String reason;

        private Result(Action action, String reason) {
            this.action = action;
            this.reason = reason;
        }

        public static Result createDisallowed(String reason) {
            return new Result(Action.DISALLOWED, reason);
        }

        public static Result allowSettingOfWantedState() {
            return new Result(Action.MUST_SET_WANTED_STATE, "Preconditions fulfilled and new state different");
        }

        public static Result createAlreadySet() {
            return new Result(Action.ALREADY_SET, "Basic preconditions fulfilled and new state is already effective");
        }

        public boolean settingWantedStateIsAllowed() {
            return action == Action.MUST_SET_WANTED_STATE;
        }

        public boolean wantedStateAlreadySet() {
            return action == Action.ALREADY_SET;
        }

        public String getReason() {
            return reason;
        }

        public String toString() {
            return "action " + action + ": " + reason;
        }
    }

    public Result evaluateTransition(
            Node node, ClusterState clusterState, SetUnitStateRequest.Condition condition,
            NodeState oldState, NodeState newState) {
        if (condition == SetUnitStateRequest.Condition.FORCE) {
            return Result.allowSettingOfWantedState();
        }

        if (condition != SetUnitStateRequest.Condition.SAFE) {
            return Result.createDisallowed("Condition not implemented: " + condition.name());
        }

        if (node.getType() != NodeType.STORAGE) {
            return Result.createDisallowed("Safe-set of node state is only supported for storage nodes! " +
                    "Requested node type: " + node.getType().toString());
        }

        NodeInfo nodeInfo = clusterInfo.getStorageNodeInfo(node.getIndex());
        if (nodeInfo == null) {
            return Result.createDisallowed("Unknown node " + node);
        }

        // If the new state and description equals the existing, we're done. This is done for 2 cases:
        // - We can short-circuit setting of a new wanted state, which e.g. hits ZooKeeper.
        // - We ensure that clients that have previously set the wanted state, continue
        //   to see the same conclusion, even though they possibly would have been denied
        //   MUST_SET_WANTED_STATE if re-evaluated. This is important for implementing idempotent clients.
        if (newState.getState().equals(oldState.getState())) {
            return Result.createAlreadySet();
        }

        switch (newState.getState()) {
            case UP:
                return canSetStateUp(nodeInfo);
            case MAINTENANCE:
                return canSetStateMaintenanceTemporarily(node, clusterState);
            case DOWN:
                return canSetStateDownPermanently(nodeInfo, clusterState);
            default:
                return Result.createDisallowed("Destination node state unsupported in safe mode: " + newState);
        }
    }

    private Result canSetStateDownPermanently(NodeInfo nodeInfo, ClusterState clusterState) {
        State reportedState = nodeInfo.getReportedState().getState();
        if (reportedState != State.UP) {
            return Result.createDisallowed("Reported state (" + reportedState
                    + ") is not UP, so no bucket data is available");
        }

        State currentState = clusterState.getNodeState(nodeInfo.getNode()).getState();
        if (currentState != State.RETIRED) {
            return Result.createDisallowed("Only retired nodes are allowed to be set to DOWN in safe mode - is "
                    + currentState);
        }

        Result thresholdCheckResult = checkUpThresholds(clusterState);
        if (!thresholdCheckResult.settingWantedStateIsAllowed()) {
            return thresholdCheckResult;
        }

        HostInfo hostInfo = nodeInfo.getHostInfo();
        Integer hostInfoNodeVersion = hostInfo.getClusterStateVersionOrNull();
        int clusterControllerVersion = clusterState.getVersion();
        if (hostInfoNodeVersion == null || hostInfoNodeVersion != clusterControllerVersion) {
            return Result.createDisallowed("Cluster controller at version " + clusterControllerVersion
                    + " got info for storage node " + nodeInfo.getNodeIndex() + " at a different version "
                    + hostInfoNodeVersion);
        }

        Optional<Metrics.Value> bucketsMetric;
        if (determineBucketsFromBucketSpaceMetric) {
            bucketsMetric = hostInfo.getMetrics().getValueAt(BUCKETS_METRIC_NAME, BUCKETS_METRIC_DIMENSIONS);
            if (!bucketsMetric.isPresent() || bucketsMetric.get().getLast() == null) {
                return Result.createDisallowed("Missing last value of the " + BUCKETS_METRIC_NAME +
                        " metric for storage node " + nodeInfo.getNodeIndex());
            }
        } else {
            bucketsMetric = hostInfo.getMetrics().getValue(LEGACY_BUCKETS_METRIC_NAME);
            if (!bucketsMetric.isPresent() || bucketsMetric.get().getLast() == null) {
                return Result.createDisallowed("Missing last value of the " + LEGACY_BUCKETS_METRIC_NAME +
                        " metric for storage node " + nodeInfo.getNodeIndex());
            }
        }

        long lastBuckets = bucketsMetric.get().getLast();
        if (lastBuckets > 0) {
            return Result.createDisallowed("The storage node manages " + lastBuckets + " buckets");
        }

        return Result.allowSettingOfWantedState();
    }

    private Result canSetStateUp(NodeInfo nodeInfo) {
        if (nodeInfo.getReportedState().getState() != State.UP) {
            return Result.createDisallowed("Refuse to set wanted state to UP, " +
                    "since the reported state is not UP (" +
                    nodeInfo.getReportedState().getState() + ")");
        }

        return Result.allowSettingOfWantedState();
    }

    private Result canSetStateMaintenanceTemporarily(Node node, ClusterState clusterState) {
        if (clusterState.getNodeState(node).getState() == State.DOWN) {
            return Result.allowSettingOfWantedState();
        }

        Result checkDistributorsResult = checkDistributors(node, clusterState.getVersion());
        if (!checkDistributorsResult.settingWantedStateIsAllowed()) {
            return checkDistributorsResult;
        }

        Result ongoingChanges = anyNodeSetToMaintenance();
        if (!ongoingChanges.settingWantedStateIsAllowed()) {
            return ongoingChanges;
        }

        Result thresholdCheckResult = checkUpThresholds(clusterState);
        if (!thresholdCheckResult.settingWantedStateIsAllowed()) {
            return thresholdCheckResult;
        }

        return Result.allowSettingOfWantedState();
    }

    private Result anyNodeSetToMaintenance() {
        for (NodeInfo nodeInfo : clusterInfo.getAllNodeInfo()) {
            if (nodeInfo.getWantedState().getState() == State.MAINTENANCE) {
                return Result.createDisallowed("There is a node already in maintenance:" + nodeInfo.getNodeIndex());
            }
        }
        return Result.allowSettingOfWantedState();
    }

    private int contentNodesWithAvailableNodeState(ClusterState clusterState) {
        final int nodeCount = clusterState.getNodeCount(NodeType.STORAGE);
        int upNodesCount = 0;
        for (int i = 0; i < nodeCount; ++i) {
            final Node node = new Node(NodeType.STORAGE, i);
            final State state = clusterState.getNodeState(node).getState();
            if (state == State.UP || state == State.RETIRED || state == State.INITIALIZING) {
                upNodesCount++;
            }
        }
        return upNodesCount;
    }

    private Result checkUpThresholds(ClusterState clusterState) {
        if (clusterInfo.getStorageNodeInfo().size() < minStorageNodesUp) {
            return Result.createDisallowed("There are only " + clusterInfo.getStorageNodeInfo().size() +
                    " storage nodes up, while config requires at least " + minStorageNodesUp);
        }

        final int nodesCount = clusterInfo.getStorageNodeInfo().size();
        final int upNodesCount = contentNodesWithAvailableNodeState(clusterState);

        if (nodesCount == 0) {
            return Result.createDisallowed("No storage nodes in cluster state");
        }
        if (((double)upNodesCount) / nodesCount < minRatioOfStorageNodesUp) {
            return Result.createDisallowed("Not enough storage nodes running: " + upNodesCount
                    + " of " +  nodesCount + " storage nodes are up which is less that the required fraction of "
                    + minRatioOfStorageNodesUp);
        }
        return Result.allowSettingOfWantedState();
    }

    private Result checkStorageNodesForDistributor(
            DistributorNodeInfo distributorNodeInfo, List<StorageNode> storageNodes, Node node) {
        for (StorageNode storageNode : storageNodes) {
            if (storageNode.getIndex() == node.getIndex()) {
                Integer minReplication = storageNode.getMinCurrentReplicationFactorOrNull();
                // Why test on != null? Missing min-replication is OK (indicate empty/few buckets on system).
                if (minReplication != null && minReplication < requiredRedundancy) {
                    return Result.createDisallowed("Distributor "
                            + distributorNodeInfo.getNodeIndex()
                            + " says storage node " + node.getIndex()
                            + " has buckets with redundancy as low as "
                            + storageNode.getMinCurrentReplicationFactorOrNull()
                            + ", but we require at least " + requiredRedundancy);
                } else {
                    return Result.allowSettingOfWantedState();
                }
            }
        }

        return Result.allowSettingOfWantedState();
    }

    /**
     * We want to check with the distributors to verify that it is safe to take down the storage node.
     * @param node the node to be checked
     * @param clusterStateVersion the cluster state we expect distributors to have
     */
    private Result checkDistributors(Node node, int clusterStateVersion) {
        if (clusterInfo.getDistributorNodeInfo().isEmpty()) {
            return Result.createDisallowed("Not aware of any distributors, probably not safe to upgrade?");
        }
        for (DistributorNodeInfo distributorNodeInfo : clusterInfo.getDistributorNodeInfo()) {
            Integer distributorClusterStateVersion = distributorNodeInfo.getHostInfo().getClusterStateVersionOrNull();
            if (distributorClusterStateVersion == null) {
                return Result.createDisallowed("Distributor node (" + distributorNodeInfo.getNodeIndex()
                        + ") has not reported any cluster state version yet.");
            } else if (distributorClusterStateVersion != clusterStateVersion) {
                return Result.createDisallowed("Distributor node (" + distributorNodeInfo.getNodeIndex()
                        + ") does not report same version ("
                        + distributorNodeInfo.getHostInfo().getClusterStateVersionOrNull()
                        + ") as fleetcontroller has (" + clusterStateVersion + ")");
            }

            List<StorageNode> storageNodes = distributorNodeInfo.getHostInfo().getDistributor().getStorageNodes();
            Result storageNodesResult = checkStorageNodesForDistributor(distributorNodeInfo, storageNodes, node);
            if (!storageNodesResult.settingWantedStateIsAllowed()) {
                return storageNodesResult;
            }
        }

        return Result.allowSettingOfWantedState();
    }
}
