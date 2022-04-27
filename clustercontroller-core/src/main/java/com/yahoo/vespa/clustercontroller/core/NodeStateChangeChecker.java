// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.lang.MutableBoolean;
import com.yahoo.lang.SettableOptional;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Group;
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
import java.util.Objects;
import java.util.Optional;

/**
 * Checks if a node can be upgraded.
 *
 * @author Haakon Dybdahl
 */
public class NodeStateChangeChecker {

    public static final String BUCKETS_METRIC_NAME = "vds.datastored.bucket_space.buckets_total";
    public static final Map<String, String> BUCKETS_METRIC_DIMENSIONS = Map.of("bucketSpace", "default");

    private final int requiredRedundancy;
    private final HierarchicalGroupVisiting groupVisiting;
    private final ClusterInfo clusterInfo;
    private final boolean inMoratorium;

    public NodeStateChangeChecker(
            int requiredRedundancy,
            HierarchicalGroupVisiting groupVisiting,
            ClusterInfo clusterInfo,
            boolean inMoratorium) {
        this.requiredRedundancy = requiredRedundancy;
        this.groupVisiting = groupVisiting;
        this.clusterInfo = clusterInfo;
        this.inMoratorium = inMoratorium;
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
            NodeState oldWantedState, NodeState newWantedState) {
        if (condition == SetUnitStateRequest.Condition.FORCE) {
            return Result.allowSettingOfWantedState();
        }

        if (inMoratorium) {
            return Result.createDisallowed("Master cluster controller is bootstrapping and in moratorium");
        }

        if (condition != SetUnitStateRequest.Condition.SAFE) {
            return Result.createDisallowed("Condition not implemented: " + condition.name());
        }

        if (node.getType() != NodeType.STORAGE) {
            return Result.createDisallowed("Safe-set of node state is only supported for storage nodes! " +
                    "Requested node type: " + node.getType().toString());
        }

        StorageNodeInfo nodeInfo = clusterInfo.getStorageNodeInfo(node.getIndex());
        if (nodeInfo == null) {
            return Result.createDisallowed("Unknown node " + node);
        }

        // If the new state and description equals the existing, we're done. This is done for 2 cases:
        // - We can short-circuit setting of a new wanted state, which e.g. hits ZooKeeper.
        // - We ensure that clients that have previously set the wanted state, continue
        //   to see the same conclusion, even though they possibly would have been denied
        //   MUST_SET_WANTED_STATE if re-evaluated. This is important for implementing idempotent clients.
        if (newWantedState.getState().equals(oldWantedState.getState()) &&
            Objects.equals(newWantedState.getDescription(), oldWantedState.getDescription())) {
            return Result.createAlreadySet();
        }

        switch (newWantedState.getState()) {
            case UP:
                return canSetStateUp(nodeInfo, oldWantedState);
            case MAINTENANCE:
                return canSetStateMaintenanceTemporarily(nodeInfo, clusterState, newWantedState.getDescription());
            case DOWN:
                return canSetStateDownPermanently(nodeInfo, clusterState, newWantedState.getDescription());
            default:
                return Result.createDisallowed("Destination node state unsupported in safe mode: " + newWantedState);
        }
    }

    private Result canSetStateDownPermanently(NodeInfo nodeInfo, ClusterState clusterState, String newDescription) {
        NodeState oldWantedState = nodeInfo.getUserWantedState();
        if (oldWantedState.getState() != State.UP && !oldWantedState.getDescription().equals(newDescription)) {
            // Refuse to override whatever an operator or unknown entity is doing.
            //
            // Note:  The new state&description is NOT equal to the old state&description:
            // that would have been short-circuited prior to this.
            return Result.createDisallowed("A conflicting wanted state is already set: " +
                    oldWantedState.getState() + ": " + oldWantedState.getDescription());
        }

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

        HostInfo hostInfo = nodeInfo.getHostInfo();
        Integer hostInfoNodeVersion = hostInfo.getClusterStateVersionOrNull();
        int clusterControllerVersion = clusterState.getVersion();
        if (hostInfoNodeVersion == null || hostInfoNodeVersion != clusterControllerVersion) {
            return Result.createDisallowed("Cluster controller at version " + clusterControllerVersion
                    + " got info for storage node " + nodeInfo.getNodeIndex() + " at a different version "
                    + hostInfoNodeVersion);
        }

        Optional<Metrics.Value> bucketsMetric;
        bucketsMetric = hostInfo.getMetrics().getValueAt(BUCKETS_METRIC_NAME, BUCKETS_METRIC_DIMENSIONS);
        if (bucketsMetric.isEmpty() || bucketsMetric.get().getLast() == null) {
            return Result.createDisallowed("Missing last value of the " + BUCKETS_METRIC_NAME +
                    " metric for storage node " + nodeInfo.getNodeIndex());
        }

        long lastBuckets = bucketsMetric.get().getLast();
        if (lastBuckets > 0) {
            return Result.createDisallowed("The storage node manages " + lastBuckets + " buckets");
        }

        return Result.allowSettingOfWantedState();
    }

    private Result canSetStateUp(NodeInfo nodeInfo, NodeState oldWantedState) {
        if (oldWantedState.getState() == State.UP) {
            // The description is not significant when setting wanting to set the state to UP
            return Result.createAlreadySet();
        }

        if (nodeInfo.getReportedState().getState() != State.UP) {
            return Result.createDisallowed("Refuse to set wanted state to UP, " +
                    "since the reported state is not UP (" +
                    nodeInfo.getReportedState().getState() + ")");
        }

        return Result.allowSettingOfWantedState();
    }

    private Result canSetStateMaintenanceTemporarily(StorageNodeInfo nodeInfo, ClusterState clusterState,
                                                     String newDescription) {
        NodeState oldWantedState = nodeInfo.getUserWantedState();
        if (oldWantedState.getState() != State.UP && !oldWantedState.getDescription().equals(newDescription)) {
            // Refuse to override whatever an operator or unknown entity is doing.  If the description is
            // identical, we assume it is the same operator.
            //
            // Note:  The new state&description is NOT equal to the old state&description:
            // that would have been short-circuited prior to this.
            return Result.createDisallowed("A conflicting wanted state is already set: " +
                    oldWantedState.getState() + ": " + oldWantedState.getDescription());
        }

        Result otherGroupCheck = anotherNodeInAnotherGroupHasWantedState(nodeInfo);
        if (!otherGroupCheck.settingWantedStateIsAllowed()) {
            return otherGroupCheck;
        }

        if (clusterState.getNodeState(nodeInfo.getNode()).getState() == State.DOWN) {
            return Result.allowSettingOfWantedState();
        }

        if (anotherNodeInGroupAlreadyAllowed(nodeInfo, newDescription)) {
            return Result.allowSettingOfWantedState();
        }

        Result allNodesAreUpCheck = checkAllNodesAreUp(clusterState);
        if (!allNodesAreUpCheck.settingWantedStateIsAllowed()) {
            return allNodesAreUpCheck;
        }

        Result checkDistributorsResult = checkDistributors(nodeInfo.getNode(), clusterState.getVersion());
        if (!checkDistributorsResult.settingWantedStateIsAllowed()) {
            return checkDistributorsResult;
        }

        return Result.allowSettingOfWantedState();
    }

    /**
     * Returns a disallow-result if there is another node (in another group, if hierarchical)
     * that has a wanted state != UP.  We disallow more than 1 suspended node/group at a time.
     */
    private Result anotherNodeInAnotherGroupHasWantedState(StorageNodeInfo nodeInfo) {
        if (groupVisiting.isHierarchical()) {
            SettableOptional<Result> anotherNodeHasWantedState = new SettableOptional<>();

            groupVisiting.visit(group -> {
                if (!groupContainsNode(group, nodeInfo.getNode())) {
                    Result result = otherNodeInGroupHasWantedState(group);
                    if (!result.settingWantedStateIsAllowed()) {
                        anotherNodeHasWantedState.set(result);
                        // Have found a node that is suspended, halt the visiting
                        return false;
                    }
                }

                return true;
            });

            return anotherNodeHasWantedState.asOptional().orElseGet(Result::allowSettingOfWantedState);
        } else {
            // Return a disallow-result if there is another node with a wanted state
            return otherNodeHasWantedState(nodeInfo);
        }
    }

    /** Returns a disallow-result, if there is a node in the group with wanted state != UP. */
    private Result otherNodeInGroupHasWantedState(Group group) {
        for (var configuredNode : group.getNodes()) {
            StorageNodeInfo storageNodeInfo = clusterInfo.getStorageNodeInfo(configuredNode.index());
            if (storageNodeInfo == null) continue;  // needed for tests only
            State storageNodeWantedState = storageNodeInfo
                    .getUserWantedState().getState();
            if (storageNodeWantedState != State.UP) {
                return Result.createDisallowed(
                        "At most one group can have wanted state: Other storage node " + configuredNode.index() +
                        " in group " + group.getIndex() + " has wanted state " + storageNodeWantedState);
            }

            State distributorWantedState = clusterInfo.getDistributorNodeInfo(configuredNode.index())
                    .getUserWantedState().getState();
            if (distributorWantedState != State.UP) {
                return Result.createDisallowed(
                        "At most one group can have wanted state: Other distributor " + configuredNode.index() +
                        " in group " + group.getIndex() + " has wanted state " + distributorWantedState);
            }
        }

        return Result.allowSettingOfWantedState();
    }

    private Result otherNodeHasWantedState(StorageNodeInfo nodeInfo) {
        for (var configuredNode : clusterInfo.getConfiguredNodes().values()) {
            if (configuredNode.index() == nodeInfo.getNodeIndex()) {
                continue;
            }

            State storageNodeWantedState = clusterInfo.getStorageNodeInfo(configuredNode.index())
                    .getUserWantedState().getState();
            if (storageNodeWantedState != State.UP) {
                return Result.createDisallowed(
                        "At most one node can have a wanted state when #groups = 1: Other storage node " +
                                configuredNode.index() + " has wanted state " + storageNodeWantedState);
            }

            State distributorWantedState = clusterInfo.getDistributorNodeInfo(configuredNode.index())
                    .getUserWantedState().getState();
            if (distributorWantedState != State.UP) {
                return Result.createDisallowed(
                        "At most one node can have a wanted state when #groups = 1: Other distributor " +
                                configuredNode.index() + " has wanted state " + distributorWantedState);
            }
        }

        return Result.allowSettingOfWantedState();
    }

    private boolean anotherNodeInGroupAlreadyAllowed(StorageNodeInfo nodeInfo, String newDescription) {
        MutableBoolean alreadyAllowed = new MutableBoolean(false);

        groupVisiting.visit(group -> {
            if (!groupContainsNode(group, nodeInfo.getNode())) {
                return true;
            }

            alreadyAllowed.set(anotherNodeInGroupAlreadyAllowed(group, nodeInfo.getNode(), newDescription));

            // Have found the leaf group we were looking for, halt the visiting.
            return false;
        });

        return alreadyAllowed.get();
    }

    private boolean anotherNodeInGroupAlreadyAllowed(Group group, Node node, String newDescription) {
        return group.getNodes().stream()
                .filter(configuredNode -> configuredNode.index() != node.getIndex())
                .map(configuredNode -> clusterInfo.getStorageNodeInfo(configuredNode.index()))
                .filter(Objects::nonNull)  // needed for tests only
                .map(NodeInfo::getUserWantedState)
                .anyMatch(userWantedState -> userWantedState.getState() == State.MAINTENANCE &&
                          Objects.equals(userWantedState.getDescription(), newDescription));
    }

    private static boolean groupContainsNode(Group group, Node node) {
        for (ConfiguredNode configuredNode : group.getNodes()) {
            if (configuredNode.index() == node.getIndex()) {
                return true;
            }
        }

        return false;
    }

    private Result checkAllNodesAreUp(ClusterState clusterState) {
        // This method verifies both storage nodes and distributors are up (or retired).
        // The complicated part is making a summary error message.

        for (NodeInfo storageNodeInfo : clusterInfo.getStorageNodeInfos()) {
            State wantedState = storageNodeInfo.getUserWantedState().getState();
            if (wantedState != State.UP && wantedState != State.RETIRED) {
                return Result.createDisallowed("Another storage node wants state " +
                        wantedState.toString().toUpperCase() + ": " + storageNodeInfo.getNodeIndex());
            }

            State state = clusterState.getNodeState(storageNodeInfo.getNode()).getState();
            if (state != State.UP && state != State.RETIRED) {
                return Result.createDisallowed("Another storage node has state " + state.toString().toUpperCase() +
                        ": " + storageNodeInfo.getNodeIndex());
            }
        }

        for (NodeInfo distributorNodeInfo : clusterInfo.getDistributorNodeInfos()) {
            State wantedState = distributorNodeInfo.getUserWantedState().getState();
            if (wantedState != State.UP && wantedState != State.RETIRED) {
                return Result.createDisallowed("Another distributor wants state " + wantedState.toString().toUpperCase() +
                        ": " + distributorNodeInfo.getNodeIndex());
            }

            State state = clusterState.getNodeState(distributorNodeInfo.getNode()).getState();
            if (state != State.UP && state != State.RETIRED) {
                return Result.createDisallowed("Another distributor has state " + state.toString().toUpperCase() +
                        ": " + distributorNodeInfo.getNodeIndex());
            }
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
        if (clusterInfo.getDistributorNodeInfos().isEmpty()) {
            return Result.createDisallowed("Not aware of any distributors, probably not safe to upgrade?");
        }
        for (DistributorNodeInfo distributorNodeInfo : clusterInfo.getDistributorNodeInfos()) {
            Integer distributorClusterStateVersion = distributorNodeInfo.getHostInfo().getClusterStateVersionOrNull();
            if (distributorClusterStateVersion == null) {
                return Result.createDisallowed("Distributor node " + distributorNodeInfo.getNodeIndex()
                                               + " has not reported any cluster state version yet.");
            } else if (distributorClusterStateVersion != clusterStateVersion) {
                return Result.createDisallowed("Distributor node " + distributorNodeInfo.getNodeIndex()
                                               + " does not report same version ("
                                               + distributorNodeInfo.getHostInfo().getClusterStateVersionOrNull()
                                               + ") as fleetcontroller (" + clusterStateVersion + ")");
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
