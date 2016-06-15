// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNode;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;

import java.util.List;

/**
 * Checks if a node can be upgraded.
 *
 * @author dybdahl
 */
public class NodeStateChangeChecker {

    private final int minStorageNodesUp;
    private double minRatioOfStorageNodesUp;
    private final int requiredRedundancy;
    private final ClusterInfo clusterInfo;

    public NodeStateChangeChecker(
            int minStorageNodesUp,
            double minRatioOfStorageNodesUp,
            int requiredRedundancy,
            ClusterInfo clusterInfo) {
        this.minStorageNodesUp = minStorageNodesUp;
        this.minRatioOfStorageNodesUp = minRatioOfStorageNodesUp;
        this.requiredRedundancy = requiredRedundancy;
        this.clusterInfo = clusterInfo;
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
            Node node, int clusterStateVersion, SetUnitStateRequest.Condition condition,
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
                return canSetStateUp(node, oldState.getState());
            case MAINTENANCE:
                return canSetStateMaintenance(node, clusterStateVersion);
            default:
                return Result.createDisallowed("Safe only supports state UP and MAINTENANCE, you tried: " + newState);
        }
    }

    private Result canSetStateUp(Node node, State oldState) {
        if (oldState != State.MAINTENANCE) {
            return Result.createDisallowed("Refusing to set wanted state to up when it is currently in " + oldState);
        }

        if (clusterInfo.getNodeInfo(node).getReportedState().getState() != State.UP) {
            return Result.createDisallowed("Refuse to set wanted state to UP, " +
                    "since the reported state is not UP (" +
                    clusterInfo.getNodeInfo(node).getReportedState().getState() + ")");
        }

        return Result.allowSettingOfWantedState();
    }

    private Result canSetStateMaintenance(Node node, int clusterStateVersion) {
        NodeInfo nodeInfo = clusterInfo.getNodeInfo(node);
        if (nodeInfo == null) {
            return Result.createDisallowed("Unknown node " + node);
        }
        NodeState reportedState = nodeInfo.getReportedState();
        if (reportedState.getState() == State.DOWN) {
            return Result.allowSettingOfWantedState();
        }

        Result checkDistributorsResult = checkDistributors(node, clusterStateVersion);
        if (!checkDistributorsResult.settingWantedStateIsAllowed()) {
            return checkDistributorsResult;
        }

        Result ongoingChanges = anyNodeSetToMaintenance();
        if (!ongoingChanges.settingWantedStateIsAllowed()) {
            return ongoingChanges;
        }

        if (clusterInfo.getStorageNodeInfo().size() < minStorageNodesUp) {
            return Result.createDisallowed("There are only " + clusterInfo.getStorageNodeInfo().size() +
                    " storage nodes up, while config requires at least " + minStorageNodesUp);
        }
        Result fractionCheck = isFractionHighEnough();
        if (!fractionCheck.settingWantedStateIsAllowed()) {
            return fractionCheck;
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

    private Result isFractionHighEnough() {
        int upNodesCount = 0;
        int nodesCount = 0;
        for (StorageNodeInfo storageNodeInfo : clusterInfo.getStorageNodeInfo()) {
            nodesCount++;
            State state = storageNodeInfo.getReportedState().getState();
            if (state == State.UP || state == State.RETIRED || state == State.INITIALIZING) {
                upNodesCount++;
            }
        }
        if (nodesCount == 0) {
            return Result.createDisallowed("No storage nodes in cluster state, not safe to restart.");
        }
        if (((double)upNodesCount) / nodesCount < minRatioOfStorageNodesUp) {
            return Result.createDisallowed("Not enough storage nodes running, running: " + upNodesCount
                    + " total storage nodes " +  nodesCount +
                    " required fraction " + minRatioOfStorageNodesUp);
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
