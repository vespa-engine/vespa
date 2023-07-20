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
import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNode;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result.allow;
import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result.alreadySet;
import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result.disallow;
import static com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest.Condition.FORCE;
import static com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest.Condition.SAFE;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

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
        if ( ! isGroupedSetup() && maxNumberOfGroupsAllowedToBeDown > 1)
            throw new IllegalArgumentException("Cannot have both 1 group and maxNumberOfGroupsAllowedToBeDown > 1");
    }

    public Result evaluateTransition(Node node, ClusterState clusterState, SetUnitStateRequest.Condition condition,
                                     NodeState oldWantedState, NodeState newWantedState) {
        if (condition == FORCE)
            return allow();

        if (inMoratorium)
            return disallow("Master cluster controller is bootstrapping and in moratorium");

        if (condition != SAFE)
            return disallow("Condition not implemented: " + condition.name());

        if (node.getType() != STORAGE)
            return disallow("Safe-set of node state is only supported for storage nodes! " +
                            "Requested node type: " + node.getType().toString());

        StorageNodeInfo nodeInfo = clusterInfo.getStorageNodeInfo(node.getIndex());
        if (nodeInfo == null)
            return disallow("Unknown node " + node);

        if (noChanges(oldWantedState, newWantedState))
            return alreadySet();

        return switch (newWantedState.getState()) {
            case UP -> canSetStateUp(nodeInfo, oldWantedState);
            case MAINTENANCE -> canSetStateMaintenanceTemporarily(nodeInfo, clusterState, newWantedState.getDescription());
            case DOWN -> canSetStateDownPermanently(nodeInfo, clusterState, newWantedState.getDescription());
            default -> disallow("Destination node state unsupported in safe mode: " + newWantedState);
        };
    }

    private static boolean noChanges(NodeState oldWantedState, NodeState newWantedState) {
        // If the new state and description equals the existing, we're done. This is done for 2 cases:
        // - We can short-circuit setting of a new wanted state, which e.g. hits ZooKeeper.
        // - We ensure that clients that have previously set the wanted state, continue
        //   to see the same conclusion, even though they possibly would have been
        //   DISALLOWED if re-evaluated. This is important for implementing idempotent clients.
        return newWantedState.getState().equals(oldWantedState.getState())
               && Objects.equals(newWantedState.getDescription(), oldWantedState.getDescription());
    }

    private Result canSetStateDownPermanently(NodeInfo nodeInfo, ClusterState clusterState, String newDescription) {
        var result = checkIfStateSetWithDifferentDescription(nodeInfo, newDescription);
        if (result.notAllowed())
            return result;

        State reportedState = nodeInfo.getReportedState().getState();
        if (reportedState != UP)
            return disallow("Reported state (" + reportedState + ") is not UP, so no bucket data is available");

        State currentState = clusterState.getNodeState(nodeInfo.getNode()).getState();
        if (currentState != RETIRED)
            return disallow("Only retired nodes are allowed to be set to DOWN in safe mode - is " + currentState);

        HostInfo hostInfo = nodeInfo.getHostInfo();
        Integer hostInfoNodeVersion = hostInfo.getClusterStateVersionOrNull();
        int clusterControllerVersion = clusterState.getVersion();
        int nodeIndex = nodeInfo.getNodeIndex();
        if (hostInfoNodeVersion == null || hostInfoNodeVersion != clusterControllerVersion)
            return disallow("Cluster controller at version " + clusterControllerVersion +
                            " got info for storage node " + nodeIndex + " at a different version " +
                            hostInfoNodeVersion);

        var bucketsMetric = hostInfo.getMetrics().getValueAt(BUCKETS_METRIC_NAME, BUCKETS_METRIC_DIMENSIONS);
        if (bucketsMetric.isEmpty() || bucketsMetric.get().getLast() == null)
            return disallow("Missing last value of the " + BUCKETS_METRIC_NAME + " metric for storage node " + nodeIndex);

        long lastBuckets = bucketsMetric.get().getLast();
        if (lastBuckets > 0)
            return disallow("The storage node manages " + lastBuckets + " buckets");

        return allow();
    }

    private Result canSetStateUp(NodeInfo nodeInfo, NodeState oldWantedState) {
        if (oldWantedState.getState() == UP)
            return alreadySet(); // The description is not significant when wanting to set the state to UP

        State reportedState = nodeInfo.getReportedState().getState();
        if (reportedState != UP)
            return disallow("Refuse to set wanted state to UP, since the reported state is not UP (" + reportedState + ")");

        return allow();
    }

    private Result canSetStateMaintenanceTemporarily(StorageNodeInfo nodeInfo, ClusterState clusterState,
                                                     String newDescription) {
        var result = checkIfStateSetWithDifferentDescription(nodeInfo, newDescription);
        if (result.notAllowed())
            return result;

        if (isGroupedSetup()) {
            if (maxNumberOfGroupsAllowedToBeDown == -1) {
                result = checkIfAnotherNodeInAnotherGroupHasWantedState(nodeInfo);
                if (result.notAllowed())
                    return result;
                if (anotherNodeInGroupAlreadyAllowed(nodeInfo, newDescription))
                    return allow();
            } else {
                log.log(INFO, "Checking if we can set " + nodeInfo.getNode() + " to maintenance temporarily");
                var optionalResult = checkIfOtherNodesHaveWantedState(nodeInfo, newDescription, clusterState);
                if (optionalResult.isPresent())
                    return optionalResult.get();
            }
        } else {
            result = otherNodeHasWantedState(nodeInfo);
            if (result.notAllowed())
                return result;
        }

        if (nodeIsDown(clusterState, nodeInfo)) {
            log.log(FINE, "node is DOWN, allow");
            return allow();
        }

        result = checkIfNodesAreUpOrRetired(clusterState);
        if (result.notAllowed()) {
            log.log(FINE, "nodesAreUpOrRetired: " + result);
            return result;
        }

        result = checkClusterStateAndRedundancy(nodeInfo.getNode(), clusterState.getVersion());
        if (result.notAllowed()) {
            log.log(FINE, "checkDistributors: "+ result);
            return result;
        }

        return allow();
    }

    private boolean isGroupedSetup() {
        return groupVisiting.isHierarchical();
    }

    /** Refuse to override whatever an operator or unknown entity is doing. */
    private static Result checkIfStateSetWithDifferentDescription(NodeInfo nodeInfo, String newDescription) {
        State oldWantedState = nodeInfo.getUserWantedState().getState();
        String oldDescription = nodeInfo.getUserWantedState().getDescription();
        if (oldWantedState != UP && ! oldDescription.equals(newDescription))
            return disallow("A conflicting wanted state is already set: " + oldWantedState + ": " + oldDescription);

        return allow();
    }

    /**
     * Returns a disallow-result if there is another node in another group
     * that has a wanted state != UP.  We disallow more than 1 suspended group at a time.
     */
    private Result checkIfAnotherNodeInAnotherGroupHasWantedState(StorageNodeInfo nodeInfo) {
        SettableOptional<Result> anotherNodeHasWantedState = new SettableOptional<>();
        groupVisiting.visit(group -> {
            if (! groupContainsNode(group, nodeInfo.getNode())) {
                Result result = otherNodeInGroupHasWantedState(group);
                if (result.notAllowed()) {
                    anotherNodeHasWantedState.set(result);
                    // Have found a node that is suspended, halt the visiting
                    return false;
                }
            }

            return true;
        });

        return anotherNodeHasWantedState.asOptional().orElseGet(Result::allow);
    }

    /**
     * Returns an optional Result, where return value is:
     * - No wanted state for other nodes, return Optional.empty
     * - Wanted state for nodes/groups are not UP:
     * - if less than maxNumberOfGroupsAllowedToBeDown: return Optional.of(allowed)
     *      else: if node is in group with nodes already down: return Optional.of(allowed), else Optional.of(disallowed)
     */
    private Optional<Result> checkIfOtherNodesHaveWantedState(StorageNodeInfo nodeInfo, String newDescription, ClusterState clusterState) {
        Node node = nodeInfo.getNode();

        Set<Integer> groupsWithNodesWantedStateNotUp = groupsWithUserWantedStateNotUp();
        if (groupsWithNodesWantedStateNotUp.size() == 0) {
            log.log(INFO, "groupsWithNodesWantedStateNotUp=0");
            return Optional.empty();
        }

        Set<Integer> groupsWithSameStateAndDescription = groupsWithSameStateAndDescription(MAINTENANCE, newDescription);
        if (aGroupContainsNode(groupsWithSameStateAndDescription, node)) {
            log.log(INFO, "Node is in group with same state and description, allow");
            return Optional.of(allow());
        }
        // There are groups with nodes not up, but with another description, probably operator set
        if (groupsWithSameStateAndDescription.size() == 0) {
            return Optional.of(disallow("Wanted state already set for another node in groups: " +
                                        sortSetIntoList(groupsWithNodesWantedStateNotUp)));
        }

        Set<Integer> retiredAndNotUpGroups = groupsWithNotRetiredAndNotUp(clusterState);
        int numberOfGroupsToConsider = retiredAndNotUpGroups.size();
        // Subtract one group if node is in a group with nodes already retired or not up, since number of such groups will
        // not increase if we allow node to go down
        if (aGroupContainsNode(retiredAndNotUpGroups, node)) {
            numberOfGroupsToConsider = retiredAndNotUpGroups.size() - 1;
        }

        var result = checkRedundancy(retiredAndNotUpGroups, clusterState);
        if (result.isPresent() && result.get().notAllowed())
            return result;

        if (numberOfGroupsToConsider < maxNumberOfGroupsAllowedToBeDown) {
            log.log(INFO, "Allow, retiredAndNotUpGroups=" + retiredAndNotUpGroups);
            return Optional.of(allow());
        }

        return Optional.of(disallow(String.format("At most %d groups can have wanted state: %s",
                                                  maxNumberOfGroupsAllowedToBeDown,
                                                  sortSetIntoList(retiredAndNotUpGroups))));
    }

    // Check redundancy for nodes seen from all distributors that are UP in cluster state for
    // storage nodes that are in groups that should be UP
    private Optional<Result> checkRedundancy(Set<Integer> retiredAndNotUpGroups, ClusterState clusterState) {
        Set<Integer> indexesToCheck = new HashSet<>();
        retiredAndNotUpGroups.forEach(index -> getNodesInGroup(index).forEach(node -> indexesToCheck.add(node.index())));

        for (var distributorNodeInfo : clusterInfo.getDistributorNodeInfos()) {
            if (clusterState.getNodeState(distributorNodeInfo.getNode()).getState() != UP) continue;

            var r = checkRedundancySeenFromDistributor(distributorNodeInfo, indexesToCheck);
            if (r.notAllowed())
                return Optional.of(r);
        }
        return Optional.empty();
    }

    private static boolean nodeIsDown(ClusterState clusterState, NodeInfo nodeInfo) {
        return clusterState.getNodeState(nodeInfo.getNode()).getState() == DOWN;
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
            if (storageNodeWantedState != UP)
                return disallow("At most one group can have wanted state: Other storage node " + index +
                                " in group " + group.getIndex() + " has wanted state " + storageNodeWantedState);

            State distributorWantedState = clusterInfo.getDistributorNodeInfo(index).getUserWantedState().getState();
            if (distributorWantedState != UP)
                return disallow("At most one group can have wanted state: Other distributor " + index +
                                " in group " + group.getIndex() + " has wanted state " + distributorWantedState);
        }

        return allow();
    }

    private Result otherNodeHasWantedState(StorageNodeInfo nodeInfo) {
        for (var configuredNode : clusterInfo.getConfiguredNodes().values()) {
            int index = configuredNode.index();
            if (index == nodeInfo.getNodeIndex()) continue;

            State storageNodeWantedState = clusterInfo.getStorageNodeInfo(index).getUserWantedState().getState();
            if (storageNodeWantedState != UP) {
                return disallow("At most one node can have a wanted state when #groups = 1: Other storage node " +
                                index + " has wanted state " + storageNodeWantedState);
            }

            State distributorWantedState = clusterInfo.getDistributorNodeInfo(index).getUserWantedState().getState();
            if (distributorWantedState != UP) {
                return disallow("At most one node can have a wanted state when #groups = 1: Other distributor " +
                                index + " has wanted state " + distributorWantedState);
            }
        }

        return allow();
    }

    private boolean anotherNodeInGroupAlreadyAllowed(StorageNodeInfo nodeInfo, String newDescription) {
        MutableBoolean alreadyAllowed = new MutableBoolean(false);

        groupVisiting.visit(group -> {
            if (!groupContainsNode(group, nodeInfo.getNode()))
                return true;

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
            if (configuredNode.index() == node.getIndex())
                return true;
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

    /** Verifies that storage nodes and distributors are up (or retired). */
    private Result checkIfNodesAreUpOrRetired(ClusterState clusterState) {
        for (NodeInfo nodeInfo : clusterInfo.getAllNodeInfos()) {
            State wantedState = nodeInfo.getUserWantedState().getState();
            if (wantedState != UP && wantedState != RETIRED)
                return disallow("Another " + nodeInfo.type() + " wants state " +
                                wantedState.toString().toUpperCase() + ": " + nodeInfo.getNodeIndex());

            State state = clusterState.getNodeState(nodeInfo.getNode()).getState();
            if (state != UP && state != RETIRED)
                return disallow("Another " + nodeInfo.type() + " has state " +
                                state.toString().toUpperCase() + ": " + nodeInfo.getNodeIndex());
        }

        return allow();
    }

    private Result checkRedundancy(DistributorNodeInfo distributorNodeInfo, Node node) {
        Integer minReplication = minReplication(distributorNodeInfo).get(node.getIndex());
        return verifyRedundancy(distributorNodeInfo, minReplication, node.getIndex());
    }

    private Result checkRedundancySeenFromDistributor(DistributorNodeInfo distributorNodeInfo, Set<Integer> indexesToCheck) {
        Map<Integer, Integer> replication = new LinkedHashMap<>(minReplication(distributorNodeInfo));

        Integer minReplication = null;
        Integer minReplicationIndex = null;
        for (var entry : replication.entrySet()) {
            Integer value = entry.getValue();
            Integer nodeIndex = entry.getKey();
            if ( ! indexesToCheck.contains(nodeIndex)) continue;
            if (minReplication == null || (value != null && value < minReplication)) {
                minReplication = value;
                if (minReplication == null) continue;

                minReplicationIndex = nodeIndex;
                if (minReplication < requiredRedundancy) break;
            }
        }

        return verifyRedundancy(distributorNodeInfo, minReplication, minReplicationIndex);
    }

    private Result verifyRedundancy(DistributorNodeInfo distributorNodeInfo, Integer minReplication, Integer minReplicationIndex) {
        // Why test on != null? Missing min-replication is OK (indicate empty/few buckets on system).
        if (minReplication != null && minReplication < requiredRedundancy) {
            return disallow("Distributor " + distributorNodeInfo.getNodeIndex()
                                    + " says storage node " + minReplicationIndex
                                    + " has buckets with redundancy as low as "
                                    + minReplication + ", but we require at least " + requiredRedundancy);
        }

        return allow();
    }

    // Replication per storage node index
    private Map<Integer, Integer> minReplication(DistributorNodeInfo distributorNodeInfo) {
        Map<Integer, Integer> replicationPerNodeIndex = new HashMap<>();
        for (StorageNode storageNode : distributorNodeInfo.getHostInfo().getDistributor().getStorageNodes()) {
            var currentValue = replicationPerNodeIndex.get(storageNode.getIndex());
            Integer minReplicationFactor = storageNode.getMinCurrentReplicationFactorOrNull();
            if (currentValue == null || (minReplicationFactor != null && minReplicationFactor < currentValue))
                replicationPerNodeIndex.put(storageNode.getIndex(), minReplicationFactor);
        }

        return replicationPerNodeIndex;
    }

    /**
     * We want to check with the distributors to verify that it is safe to take down the storage node.
     * @param node the node to be checked
     * @param clusterStateVersion the cluster state we expect distributors to have
     */
    private Result checkClusterStateAndRedundancy(Node node, int clusterStateVersion) {
        if (clusterInfo.getDistributorNodeInfos().isEmpty())
            return disallow("Not aware of any distributors, probably not safe to upgrade?");

        for (DistributorNodeInfo distributorNodeInfo : clusterInfo.getDistributorNodeInfos()) {
            Integer distributorClusterStateVersion = distributorNodeInfo.getHostInfo().getClusterStateVersionOrNull();
            if (distributorClusterStateVersion == null)
                return disallow("Distributor node " + distributorNodeInfo.getNodeIndex() +
                                " has not reported any cluster state version yet.");
            if (distributorClusterStateVersion != clusterStateVersion) {
                return disallow("Distributor node " + distributorNodeInfo.getNodeIndex() +
                                " does not report same version (" +
                                distributorNodeInfo.getHostInfo().getClusterStateVersionOrNull() +
                                ") as fleetcontroller (" + clusterStateVersion + ")");
            }

            Result storageNodesResult = checkRedundancy(distributorNodeInfo, node);
            if (storageNodesResult.notAllowed()) {
                return storageNodesResult;
            }
        }

        return allow();
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

    private Group groupForThisIndex(int groupIndex) {
        return clusterInfo.getAllNodeInfos().stream()
                .map(NodeInfo::getGroup)
                .filter(Objects::nonNull)
                .filter(Group::isLeafGroup)
                .filter(group -> group.getIndex() == groupIndex)
                .findFirst()
                .orElseThrow();
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

    private List<ConfiguredNode> getNodesInGroup(int groupIndex) {
        return groupForThisIndex(groupIndex).getNodes();
    }

    public static class Result {

        public enum Action {
            ALLOWED,
            ALREADY_SET,
            DISALLOWED
        }

        private final Action action;
        private final String reason;

        private Result(Action action, String reason) {
            this.action = action;
            this.reason = reason;
        }

        public static Result disallow(String reason) {
            return new Result(Action.DISALLOWED, reason);
        }

        public static Result allow() {
            return new Result(Action.ALLOWED, "Preconditions fulfilled and new state different");
        }

        public static Result alreadySet() {
            return new Result(Action.ALREADY_SET, "Basic preconditions fulfilled and new state is already effective");
        }

        public boolean allowed() { return action == Action.ALLOWED; }

        public boolean notAllowed() { return ! allowed(); }

        public boolean isAlreadySet() {
            return action == Action.ALREADY_SET;
        }

        public String reason() {
            return reason;
        }

        public String toString() {
            return "action " + action + ": " + reason;
        }

    }

}
