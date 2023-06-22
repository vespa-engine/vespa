// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import ai.vespa.metrics.StorageMetrics;
import com.yahoo.lang.MutableBoolean;
import com.yahoo.lang.SettableOptional;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Group;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.hostinfo.Metrics;
import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNode;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vdslib.state.NodeType.STORAGE;
import static com.yahoo.vdslib.state.State.DOWN;
import static com.yahoo.vdslib.state.State.MAINTENANCE;
import static com.yahoo.vdslib.state.State.RETIRED;
import static com.yahoo.vdslib.state.State.UP;
import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result.allowSettingOfWantedState;
import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result.createAlreadySet;
import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result.createDisallowed;
import static com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest.Condition.FORCE;
import static com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest.Condition.SAFE;
import static java.util.logging.Level.FINE;

/**
 * Checks if a node can be upgraded.
 *
 * @author Haakon Dybdahl
 */
public class NodeStateChangeChecker {

    private static final Logger log = Logger.getLogger(NodeStateChangeChecker.class.getName());
    private static final String BUCKETS_METRIC_NAME = StorageMetrics.VDS_DATASTORED_BUCKET_SPACE_BUCKETS_TOTAL.baseName();
    private static final Map<String, String> BUCKETS_METRIC_DIMENSIONS = Map.of("bucketSpace", "default");

    private final int requiredRedundancy;
    private final HierarchicalGroupVisiting groupVisiting;
    private final ClusterInfo clusterInfo;
    private final boolean inMoratorium;
    private final int maxNumberOfGroupsAllowedToBeDown;

    public NodeStateChangeChecker(ContentCluster cluster, boolean inMoratorium) {
        this.requiredRedundancy = cluster.getDistribution().getRedundancy();
        this.groupVisiting = new HierarchicalGroupVisiting(cluster.getDistribution());
        this.clusterInfo = cluster.clusterInfo();
        this.inMoratorium = inMoratorium;
        this.maxNumberOfGroupsAllowedToBeDown = cluster.maxNumberOfGroupsAllowedToBeDown();
        if ( ! groupVisiting.isHierarchical() && maxNumberOfGroupsAllowedToBeDown > 1)
            throw new IllegalArgumentException("Cannot have both 1 group and maxNumberOfGroupsAllowedToBeDown > 1");
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

    public Result evaluateTransition(Node node, ClusterState clusterState, SetUnitStateRequest.Condition condition,
                                     NodeState oldWantedState, NodeState newWantedState) {
        if (condition == FORCE) {
            return allowSettingOfWantedState();
        }

        if (inMoratorium) {
            return createDisallowed("Master cluster controller is bootstrapping and in moratorium");
        }

        if (condition != SAFE) {
            return createDisallowed("Condition not implemented: " + condition.name());
        }

        if (node.getType() != STORAGE) {
            return createDisallowed("Safe-set of node state is only supported for storage nodes! " +
                    "Requested node type: " + node.getType().toString());
        }

        StorageNodeInfo nodeInfo = clusterInfo.getStorageNodeInfo(node.getIndex());
        if (nodeInfo == null) {
            return createDisallowed("Unknown node " + node);
        }

        // If the new state and description equals the existing, we're done. This is done for 2 cases:
        // - We can short-circuit setting of a new wanted state, which e.g. hits ZooKeeper.
        // - We ensure that clients that have previously set the wanted state, continue
        //   to see the same conclusion, even though they possibly would have been denied
        //   MUST_SET_WANTED_STATE if re-evaluated. This is important for implementing idempotent clients.
        if (newWantedState.getState().equals(oldWantedState.getState()) &&
            Objects.equals(newWantedState.getDescription(), oldWantedState.getDescription())) {
            return createAlreadySet();
        }

        return switch (newWantedState.getState()) {
            case UP -> canSetStateUp(nodeInfo, oldWantedState);
            case MAINTENANCE -> canSetStateMaintenanceTemporarily(nodeInfo, clusterState, newWantedState.getDescription());
            case DOWN -> canSetStateDownPermanently(nodeInfo, clusterState, newWantedState.getDescription());
            default -> createDisallowed("Destination node state unsupported in safe mode: " + newWantedState);
        };
    }

    private Result canSetStateDownPermanently(NodeInfo nodeInfo, ClusterState clusterState, String newDescription) {
        NodeState oldWantedState = nodeInfo.getUserWantedState();
        if (oldWantedState.getState() != UP && !oldWantedState.getDescription().equals(newDescription)) {
            // Refuse to override whatever an operator or unknown entity is doing.
            //
            // Note:  The new state&description is NOT equal to the old state&description:
            // that would have been short-circuited prior to this.
            return createDisallowed("A conflicting wanted state is already set: " +
                    oldWantedState.getState() + ": " + oldWantedState.getDescription());
        }

        State reportedState = nodeInfo.getReportedState().getState();
        if (reportedState != UP) {
            return createDisallowed("Reported state (" + reportedState
                    + ") is not UP, so no bucket data is available");
        }

        State currentState = clusterState.getNodeState(nodeInfo.getNode()).getState();
        if (currentState != RETIRED) {
            return createDisallowed("Only retired nodes are allowed to be set to DOWN in safe mode - is "
                    + currentState);
        }

        HostInfo hostInfo = nodeInfo.getHostInfo();
        Integer hostInfoNodeVersion = hostInfo.getClusterStateVersionOrNull();
        int clusterControllerVersion = clusterState.getVersion();
        if (hostInfoNodeVersion == null || hostInfoNodeVersion != clusterControllerVersion) {
            return createDisallowed("Cluster controller at version " + clusterControllerVersion
                    + " got info for storage node " + nodeInfo.getNodeIndex() + " at a different version "
                    + hostInfoNodeVersion);
        }

        Optional<Metrics.Value> bucketsMetric;
        bucketsMetric = hostInfo.getMetrics().getValueAt(BUCKETS_METRIC_NAME, BUCKETS_METRIC_DIMENSIONS);
        if (bucketsMetric.isEmpty() || bucketsMetric.get().getLast() == null) {
            return createDisallowed("Missing last value of the " + BUCKETS_METRIC_NAME +
                    " metric for storage node " + nodeInfo.getNodeIndex());
        }

        long lastBuckets = bucketsMetric.get().getLast();
        if (lastBuckets > 0) {
            return createDisallowed("The storage node manages " + lastBuckets + " buckets");
        }

        return allowSettingOfWantedState();
    }

    private Result canSetStateUp(NodeInfo nodeInfo, NodeState oldWantedState) {
        if (oldWantedState.getState() == UP) {
            // The description is not significant when setting wanting to set the state to UP
            return createAlreadySet();
        }

        if (nodeInfo.getReportedState().getState() != UP) {
            return createDisallowed("Refuse to set wanted state to UP, " +
                    "since the reported state is not UP (" +
                    nodeInfo.getReportedState().getState() + ")");
        }

        return allowSettingOfWantedState();
    }

    private Result canSetStateMaintenanceTemporarily(StorageNodeInfo nodeInfo, ClusterState clusterState,
                                                     String newDescription) {
        NodeState oldWantedState = nodeInfo.getUserWantedState();
        if (oldWantedState.getState() != UP && !oldWantedState.getDescription().equals(newDescription)) {
            // Refuse to override whatever an operator or unknown entity is doing.  If the description is
            // identical, we assume it is the same operator.
            //
            // Note:  The new state&description is NOT equal to the old state&description:
            // that would have been short-circuited prior to this.
            return createDisallowed("A conflicting wanted state is already set: " +
                    oldWantedState.getState() + ": " + oldWantedState.getDescription());
        }

        if (maxNumberOfGroupsAllowedToBeDown == -1) {
            var otherGroupCheck = anotherNodeInAnotherGroupHasWantedState(nodeInfo);
            if (!otherGroupCheck.settingWantedStateIsAllowed()) {
                return otherGroupCheck;
            }
            if (anotherNodeInGroupAlreadyAllowed(nodeInfo, newDescription)) {
                return allowSettingOfWantedState();
            }
        } else {
            var result = otherNodesHaveWantedState(nodeInfo, newDescription, clusterState);
            if (result.isPresent())
                return result.get();
        }

        if (clusterState.getNodeState(nodeInfo.getNode()).getState() == DOWN) {
            log.log(FINE, "node is DOWN, allow");
            return allowSettingOfWantedState();
        }

        Result allNodesAreUpCheck = checkAllNodesAreUp(clusterState);
        if (!allNodesAreUpCheck.settingWantedStateIsAllowed()) {
            log.log(FINE, "allNodesAreUpCheck: " + allNodesAreUpCheck);
            return allNodesAreUpCheck;
        }

        Result checkDistributorsResult = checkDistributors(nodeInfo.getNode(), clusterState.getVersion());
        if (!checkDistributorsResult.settingWantedStateIsAllowed()) {
            log.log(FINE, "checkDistributors: "+ checkDistributorsResult);
            return checkDistributorsResult;
        }

        return allowSettingOfWantedState();
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

    /**
     * Returns an optional Result, where return value is:
     * For flat setup: Return Optional.of(disallowed) if wanted state is set on some node, else Optional.empty
     * For hierarchical setup: No wanted state for other nodes, return Optional.empty
     *                         Wanted state for nodes/groups are not UP:
     *                           if less than maxNumberOfGroupsAllowedToBeDown: return Optional.of(allowed)
     *                           else: if node is in group with nodes already down: return Optional.of(allowed), else Optional.of(disallowed)
     */
    private Optional<Result> otherNodesHaveWantedState(StorageNodeInfo nodeInfo, String newDescription, ClusterState clusterState) {
        Node node = nodeInfo.getNode();

        if (groupVisiting.isHierarchical()) {
            Set<Integer> groupsWithNodesWantedStateNotUp = groupsWithUserWantedStateNotUp();
            if (groupsWithNodesWantedStateNotUp.size() == 0) {
                log.log(FINE, "groupsWithNodesWantedStateNotUp=0");
                return Optional.empty();
            }

            Set<Integer> groupsWithSameStateAndDescription = groupsWithSameStateAndDescription(MAINTENANCE, newDescription);
            if (aGroupContainsNode(groupsWithSameStateAndDescription, node)) {
                log.log(FINE, "Node is in group with same state and description, allow");
                return Optional.of(allowSettingOfWantedState());
            }
            // There are groups with nodes not up, but with another description, probably operator set
            if (groupsWithSameStateAndDescription.size() == 0) {
                return Optional.of(createDisallowed("Wanted state already set for another node in groups: " +
                                                            sortSetIntoList(groupsWithNodesWantedStateNotUp)));
            }

            Set<Integer> retiredAndNotUpGroups = groupsWithNotRetiredAndNotUp(clusterState);
            int numberOfGroupsToConsider = retiredAndNotUpGroups.size();
            // Subtract one group if node is in a group with nodes already retired or not up, since number of such groups will
            // not increase if we allow node to go down
            if (aGroupContainsNode(retiredAndNotUpGroups, node)) {
                numberOfGroupsToConsider = retiredAndNotUpGroups.size() - 1;
            }
            if (numberOfGroupsToConsider < maxNumberOfGroupsAllowedToBeDown) {
                log.log(FINE, "Allow, retiredAndNotUpGroups=" + retiredAndNotUpGroups);
                return Optional.of(allowSettingOfWantedState());
            }

            return Optional.of(createDisallowed(String.format("At most %d groups can have wanted state: %s",
                                                              maxNumberOfGroupsAllowedToBeDown,
                                                              sortSetIntoList(retiredAndNotUpGroups))));
        } else {
            // Return a disallow-result if there is another node with a wanted state
            var otherNodeHasWantedState = otherNodeHasWantedState(nodeInfo);
            if ( ! otherNodeHasWantedState.settingWantedStateIsAllowed())
                return Optional.of(otherNodeHasWantedState);
        }
        return Optional.empty();
    }

    private ArrayList<Integer> sortSetIntoList(Set<Integer> set) {
        var sortedList = new ArrayList<>(set);
        Collections.sort(sortedList);
        return sortedList;
    }

    /** Returns a disallow-result, if there is a node in the group with wanted state != UP. */
    private Result otherNodeInGroupHasWantedState(Group group) {
        for (var configuredNode : group.getNodes()) {
            int index = configuredNode.index();
            StorageNodeInfo storageNodeInfo = clusterInfo.getStorageNodeInfo(index);
            if (storageNodeInfo == null) continue;  // needed for tests only
            State storageNodeWantedState = storageNodeInfo.getUserWantedState().getState();
            if (storageNodeWantedState != UP) {
                return createDisallowed(
                        "At most one group can have wanted state: Other storage node " + index +
                        " in group " + group.getIndex() + " has wanted state " + storageNodeWantedState);
            }

            State distributorWantedState = clusterInfo.getDistributorNodeInfo(index).getUserWantedState().getState();
            if (distributorWantedState != UP) {
                return createDisallowed(
                        "At most one group can have wanted state: Other distributor " + index +
                        " in group " + group.getIndex() + " has wanted state " + distributorWantedState);
            }
        }

        return allowSettingOfWantedState();
    }

    private Result otherNodeHasWantedState(StorageNodeInfo nodeInfo) {
        for (var configuredNode : clusterInfo.getConfiguredNodes().values()) {
            int index = configuredNode.index();
            if (index == nodeInfo.getNodeIndex()) {
                continue;
            }

            State storageNodeWantedState = clusterInfo.getStorageNodeInfo(index).getUserWantedState().getState();
            if (storageNodeWantedState != UP) {
                return createDisallowed(
                        "At most one node can have a wanted state when #groups = 1: Other storage node " +
                                index + " has wanted state " + storageNodeWantedState);
            }

            State distributorWantedState = clusterInfo.getDistributorNodeInfo(index).getUserWantedState().getState();
            if (distributorWantedState != UP) {
                return createDisallowed(
                        "At most one node can have a wanted state when #groups = 1: Other distributor " +
                                index + " has wanted state " + distributorWantedState);
            }
        }

        return allowSettingOfWantedState();
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

    private boolean aGroupContainsNode(Collection<Integer> groupIndexes, Node node) {
        for (Group group : getGroupsWithIndexes(groupIndexes)) {
            if (groupContainsNode(group, node))
                return true;
        }

        return false;
    }

    private List<Group> getGroupsWithIndexes(Collection<Integer> groupIndexes) {
        return clusterInfo.getStorageNodeInfos().stream()
                          .map(NodeInfo::getGroup)
                          .filter(group -> groupIndexes.contains(group.getIndex()))
                          .collect(Collectors.toList());
    }

    private Result checkAllNodesAreUp(ClusterState clusterState) {
        // This method verifies both storage nodes and distributors are up (or retired).
        // The complicated part is making a summary error message.

        for (NodeInfo storageNodeInfo : clusterInfo.getStorageNodeInfos()) {
            State wantedState = storageNodeInfo.getUserWantedState().getState();
            if (wantedState != UP && wantedState != RETIRED) {
                return createDisallowed("Another storage node wants state " +
                        wantedState.toString().toUpperCase() + ": " + storageNodeInfo.getNodeIndex());
            }

            State state = clusterState.getNodeState(storageNodeInfo.getNode()).getState();
            if (state != UP && state != RETIRED) {
                return createDisallowed("Another storage node has state " + state.toString().toUpperCase() +
                        ": " + storageNodeInfo.getNodeIndex());
            }
        }

        for (NodeInfo distributorNodeInfo : clusterInfo.getDistributorNodeInfos()) {
            State wantedState = distributorNodeInfo.getUserWantedState().getState();
            if (wantedState != UP && wantedState != RETIRED) {
                return createDisallowed("Another distributor wants state " + wantedState.toString().toUpperCase() +
                        ": " + distributorNodeInfo.getNodeIndex());
            }

            State state = clusterState.getNodeState(distributorNodeInfo.getNode()).getState();
            if (state != UP && state != RETIRED) {
                return createDisallowed("Another distributor has state " + state.toString().toUpperCase() +
                        ": " + distributorNodeInfo.getNodeIndex());
            }
        }

        return allowSettingOfWantedState();
    }

    private Result checkStorageNodesForDistributor(DistributorNodeInfo distributorNodeInfo, List<StorageNode> storageNodes, Node node) {
        for (StorageNode storageNode : storageNodes) {
            if (storageNode.getIndex() == node.getIndex()) {
                Integer minReplication = storageNode.getMinCurrentReplicationFactorOrNull();
                // Why test on != null? Missing min-replication is OK (indicate empty/few buckets on system).
                if (minReplication != null && minReplication < requiredRedundancy) {
                    return createDisallowed("Distributor "
                            + distributorNodeInfo.getNodeIndex()
                            + " says storage node " + node.getIndex()
                            + " has buckets with redundancy as low as "
                            + storageNode.getMinCurrentReplicationFactorOrNull()
                            + ", but we require at least " + requiredRedundancy);
                } else {
                    return allowSettingOfWantedState();
                }
            }
        }

        return allowSettingOfWantedState();
    }

    /**
     * We want to check with the distributors to verify that it is safe to take down the storage node.
     * @param node the node to be checked
     * @param clusterStateVersion the cluster state we expect distributors to have
     */
    private Result checkDistributors(Node node, int clusterStateVersion) {
        if (clusterInfo.getDistributorNodeInfos().isEmpty()) {
            return createDisallowed("Not aware of any distributors, probably not safe to upgrade?");
        }
        for (DistributorNodeInfo distributorNodeInfo : clusterInfo.getDistributorNodeInfos()) {
            Integer distributorClusterStateVersion = distributorNodeInfo.getHostInfo().getClusterStateVersionOrNull();
            if (distributorClusterStateVersion == null) {
                return createDisallowed("Distributor node " + distributorNodeInfo.getNodeIndex()
                                               + " has not reported any cluster state version yet.");
            } else if (distributorClusterStateVersion != clusterStateVersion) {
                return createDisallowed("Distributor node " + distributorNodeInfo.getNodeIndex()
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

        return allowSettingOfWantedState();
    }

    private Set<Integer> groupsWithUserWantedStateNotUp() {
        return clusterInfo.getAllNodeInfos().stream()
                          .filter(sni -> !UP.equals(sni.getUserWantedState().getState()))
                          .map(NodeInfo::getGroup)
                          .filter(Objects::nonNull)
                          .filter(Group::isLeafGroup)
                          .map(Group::getIndex)
                          .collect(Collectors.toSet());
    }

    // groups with at least one node with the same state & description
    private Set<Integer> groupsWithSameStateAndDescription(State state, String newDescription) {
        return clusterInfo.getAllNodeInfos().stream()
                          .filter(nodeInfo -> {
                              var userWantedState = nodeInfo.getUserWantedState();
                              return userWantedState.getState() == state &&
                                      Objects.equals(userWantedState.getDescription(), newDescription);
                          })
                          .map(NodeInfo::getGroup)
                          .filter(Objects::nonNull)
                          .filter(Group::isLeafGroup)
                          .map(Group::getIndex)
                          .collect(Collectors.toSet());
    }

    // groups with at least one node in state (not retired AND not up)
    private Set<Integer> groupsWithNotRetiredAndNotUp(ClusterState clusterState) {
        return clusterInfo.getAllNodeInfos().stream()
                          .filter(nodeInfo -> (nodeInfo.getUserWantedState().getState() != RETIRED
                                               && nodeInfo.getUserWantedState().getState() != UP)
                                  || (clusterState.getNodeState(nodeInfo.getNode()).getState() != RETIRED
                                      && clusterState.getNodeState(nodeInfo.getNode()).getState() != UP))
                          .map(NodeInfo::getGroup)
                          .filter(Objects::nonNull)
                          .filter(Group::isLeafGroup)
                          .map(Group::getIndex)
                          .collect(Collectors.toSet());
    }

}
