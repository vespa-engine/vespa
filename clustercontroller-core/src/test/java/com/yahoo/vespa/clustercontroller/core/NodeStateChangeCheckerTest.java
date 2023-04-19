// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.yahoo.vdslib.state.NodeType.DISTRIBUTOR;
import static com.yahoo.vdslib.state.NodeType.STORAGE;
import static com.yahoo.vdslib.state.State.DOWN;
import static com.yahoo.vdslib.state.State.INITIALIZING;
import static com.yahoo.vdslib.state.State.MAINTENANCE;
import static com.yahoo.vdslib.state.State.UP;
import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result;
import static com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest.Condition.FORCE;
import static com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest.Condition.SAFE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodeStateChangeCheckerTest {

    private static final int requiredRedundancy = 4;
    private static final int currentClusterStateVersion = 2;

    private static final Node nodeDistributor = new Node(DISTRIBUTOR, 1);
    private static final Node nodeStorage = new Node(STORAGE, 1);

    private static final NodeState UP_NODE_STATE = new NodeState(STORAGE, UP);
    private static final NodeState MAINTENANCE_NODE_STATE = createNodeState(MAINTENANCE, "Orchestrator");
    private static final NodeState DOWN_NODE_STATE = createNodeState(DOWN, "RetireEarlyExpirer");

    private static NodeState createNodeState(State state, String description) {
        return new NodeState(STORAGE, state).setDescription(description);
    }

    private static ClusterState clusterState(String state) { return ClusterState.stateFromString(state); }

    private static ClusterState defaultAllUpClusterState() {
        return defaultAllUpClusterState(4);
    }

    private static ClusterState defaultAllUpClusterState(int nodeCount) {
        return clusterState(String.format("version:%d distributor:%d storage:%d",
                                          currentClusterStateVersion,
                                          nodeCount ,
                                          nodeCount));
    }

    private NodeStateChangeChecker createChangeChecker(ContentCluster cluster) {
        return new NodeStateChangeChecker(cluster, false);
    }

    private ContentCluster createCluster(int nodeCount, int maxNumberOfGroupsAllowedToBeDown) {
        return createCluster(nodeCount, 1, maxNumberOfGroupsAllowedToBeDown);
    }

    private ContentCluster createCluster(int nodeCount, int groupCount, int maxNumberOfGroupsAllowedToBeDown) {
        List<ConfiguredNode> nodes = createNodes(nodeCount);
        Distribution distribution = new Distribution(createDistributionConfig(nodeCount, groupCount));
        return new ContentCluster("Clustername", nodes, distribution, maxNumberOfGroupsAllowedToBeDown);
    }

    private String createDistributorHostInfo(int replicationfactor1, int replicationfactor2, int replicationfactor3) {
        return "{\n" +
                "    \"cluster-state-version\": 2,\n" +
                "    \"distributor\": {\n" +
                "        \"storage-nodes\": [\n" +
                "            {\n" +
                "                \"node-index\": 0,\n" +
                "                \"min-current-replication-factor\": " + replicationfactor1 + "\n" +
                "            },\n" +
                "            {\n" +
                "                \"node-index\": 1,\n" +
                "                \"min-current-replication-factor\": " + replicationfactor2 + "\n" +
                "            },\n" +
                "            {\n" +
                "                \"node-index\": 2,\n" +
                "                \"min-current-replication-factor\": " + replicationfactor3 + "\n" +
                "            },\n" +
                "            {\n" +
                "                \"node-index\": 3\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}\n";
    }

    private void markAllNodesAsReportingStateUp(ContentCluster cluster) {
        final ClusterInfo clusterInfo = cluster.clusterInfo();
        final int configuredNodeCount = cluster.clusterInfo().getConfiguredNodes().size();
        for (int i = 0; i < configuredNodeCount; i++) {
            clusterInfo.getDistributorNodeInfo(i).setReportedState(new NodeState(DISTRIBUTOR, UP), 0);
            clusterInfo.getDistributorNodeInfo(i).setHostInfo(HostInfo.createHostInfo(createDistributorHostInfo(4, 5, 6)));
            clusterInfo.getStorageNodeInfo(i).setReportedState(new NodeState(STORAGE, UP), 0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanUpgradeWithForce(int maxNumberOfGroupsAllowedToBeDown) {
        var nodeStateChangeChecker = createChangeChecker(createCluster(1, maxNumberOfGroupsAllowedToBeDown));
        NodeState newState = new NodeState(STORAGE, INITIALIZING);
        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeDistributor, defaultAllUpClusterState(), FORCE,
                UP_NODE_STATE, newState);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testDeniedInMoratorium(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        var nodeStateChangeChecker = new NodeStateChangeChecker(cluster, true);
        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 10), defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Master cluster controller is bootstrapping and in moratorium", result.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testUnknownStorageNode(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        var nodeStateChangeChecker = createChangeChecker(cluster);
        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 10), defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Unknown node storage.10", result.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testSafeMaintenanceDisallowedWhenOtherStorageNodeInFlatClusterIsSuspended(int maxNumberOfGroupsAllowedToBeDown) {
        // Nodes 0-3, storage node 0 being in maintenance with "Orchestrator" description.
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        setStorageNodeWantedStateToMaintenance(cluster, 0);
        var nodeStateChangeChecker = createChangeChecker(cluster);
        ClusterState clusterStateWith0InMaintenance = clusterState(String.format(
                "version:%d distributor:4 storage:4 .0.s:m",
                currentClusterStateVersion));

        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 1), clusterStateWith0InMaintenance,
                SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("At most one node can have a wanted state when #groups = 1: Other storage node 0 has wanted state Maintenance",
                result.getReason());
    }

    @Test
    void testMaintenanceAllowedFor2Of4Groups() {
        // 4 groups with 1 node in each group
        Collection<ConfiguredNode> nodes = createNodes(4);
        StorDistributionConfig config = createDistributionConfig(4, 4);

        int maxNumberOfGroupsAllowedToBeDown = 2;
        var cluster = new ContentCluster("Clustername", nodes, new Distribution(config), maxNumberOfGroupsAllowedToBeDown);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 5, 6)));
        var nodeStateChangeChecker = createChangeChecker(cluster);

        // All nodes up, set a storage node in group 0 to maintenance
        {
            int nodeIndex = 0;
            checkSettingToMaintenanceIsAllowed(nodeIndex, nodeStateChangeChecker, defaultAllUpClusterState());
            setStorageNodeWantedStateToMaintenance(cluster, nodeIndex);
        }

        // Node in group 0 in maintenance, set storage node in group 1 to maintenance
        {
            ClusterState clusterState = clusterState(String.format("version:%d distributor:4 .0.s:d storage:4 .0.s:m", currentClusterStateVersion));
            int nodeIndex = 1;
            checkSettingToMaintenanceIsAllowed(nodeIndex, nodeStateChangeChecker, clusterState);
            setStorageNodeWantedStateToMaintenance(cluster, nodeIndex);
        }

        // Nodes in group 0 and 1 in maintenance, try to set storage node in group 2 to maintenance while storage node 2 is down, should fail
        {
            ClusterState clusterState = clusterState(String.format("version:%d distributor:4 storage:4 .0.s:m .1.s:m .2.s:d", currentClusterStateVersion));
            int nodeIndex = 2;
            cluster.clusterInfo().getStorageNodeInfo(nodeIndex).setReportedState(new NodeState(STORAGE, DOWN), 0);
            Node node = new Node(STORAGE, nodeIndex);
            Result result = nodeStateChangeChecker.evaluateTransition(node, clusterState, SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertFalse(result.settingWantedStateIsAllowed(), result.toString());
            assertFalse(result.wantedStateAlreadySet());
            assertEquals("At most 2 groups can have wanted state: [0, 1, 2]", result.getReason());
        }

        // Nodes in group 0 and 1 in maintenance, try to set storage node in group 2 to maintenance, should fail
        {
            ClusterState clusterState = clusterState(String.format("version:%d distributor:4 storage:4 .0.s:m .1.s:m", currentClusterStateVersion));
            int nodeIndex = 2;
            Node node = new Node(STORAGE, nodeIndex);
            Result result = nodeStateChangeChecker.evaluateTransition(node, clusterState, SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertFalse(result.settingWantedStateIsAllowed(), result.toString());
            assertFalse(result.wantedStateAlreadySet());
            assertEquals("At most 2 groups can have wanted state: [0, 1]", result.getReason());
        }

    }

    @Test
    void testMaintenanceAllowedFor2Of4Groups8Nodes() {
        // 4 groups with 2 nodes in each group
        Collection<ConfiguredNode> nodes = createNodes(8);
        StorDistributionConfig config = createDistributionConfig(8, 4);

        int maxNumberOfGroupsAllowedToBeDown = 2;
        var cluster = new ContentCluster("Clustername", nodes, new Distribution(config), maxNumberOfGroupsAllowedToBeDown);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 5, 6)));
        var nodeStateChangeChecker = createChangeChecker(cluster);

        // All nodes up, set a storage node in group 0 to maintenance
        {
            ClusterState clusterState = defaultAllUpClusterState(8);
            int nodeIndex = 0;
            checkSettingToMaintenanceIsAllowed(nodeIndex, nodeStateChangeChecker, clusterState);
            setStorageNodeWantedStateToMaintenance(cluster, nodeIndex);
        }

        // 1 Node in group 0 in maintenance, try to set node 1 in group 0 to maintenance
        {
            ClusterState clusterState = clusterState(String.format("version:%d distributor:8 .0.s:d storage:8 .0.s:m", currentClusterStateVersion));
            int nodeIndex = 1;
            checkSettingToMaintenanceIsAllowed(nodeIndex, nodeStateChangeChecker, clusterState);
            setStorageNodeWantedStateToMaintenance(cluster, nodeIndex);
        }

        // 2 nodes in group 0 in maintenance, try to set storage node 2 in group 1 to maintenance
        {
            ClusterState clusterState = clusterState(String.format("version:%d distributor:8 storage:8 .0.s:m .1.s:m", currentClusterStateVersion));
            int nodeIndex = 2;
            checkSettingToMaintenanceIsAllowed(nodeIndex, nodeStateChangeChecker, clusterState);
            setStorageNodeWantedStateToMaintenance(cluster, nodeIndex);
        }

        // 2 nodes in group 0 and 1 in group 1 in maintenance, try to set storage node 4 in group 2 to maintenance, should fail (different group)
        {
            ClusterState clusterState = clusterState(String.format("version:%d distributor:8 storage:8 .0.s:m .1.s:m .2.s:m", currentClusterStateVersion));
            int nodeIndex = 4;
            Node node = new Node(STORAGE, nodeIndex);
            Result result = nodeStateChangeChecker.evaluateTransition(node, clusterState, SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertFalse(result.settingWantedStateIsAllowed(), result.toString());
            assertFalse(result.wantedStateAlreadySet());
            assertEquals("At most 2 groups can have wanted state: [0, 1]", result.getReason());
        }

        // 2 nodes in group 0 and 1 in group 1 in maintenance, try to set storage node 3 in group 1 to maintenance
        {
            ClusterState clusterState = clusterState(String.format("version:%d distributor:8 storage:8 .0.s:m .1.s:m .2.s:m", currentClusterStateVersion));
            int nodeIndex = 3;
            checkSettingToMaintenanceIsAllowed(nodeIndex, nodeStateChangeChecker, clusterState);
            setStorageNodeWantedStateToMaintenance(cluster, nodeIndex);
        }

        // 2 nodes in group 0 in maintenance, storage node 3 in group 1 is in maintenance with another description
        // (set in maintenance by operator), try to set storage node 3 in group 1 to maintenance, should bew allowed
        {
            ClusterState clusterState = clusterState(String.format("version:%d distributor:8 storage:8 .0.s:m .1.s:m .3.s:m", currentClusterStateVersion));
            setStorageNodeWantedState(cluster, 3, MAINTENANCE, "Maintenance, set by operator");  // Set to another description
            setStorageNodeWantedState(cluster, 2, UP, ""); // Set back to UP, want to set this to maintenance again
            int nodeIndex = 2;
            Node node = new Node(STORAGE, nodeIndex);
            Result result = nodeStateChangeChecker.evaluateTransition(node, clusterState, SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertTrue(result.settingWantedStateIsAllowed(), result.toString());
            assertFalse(result.wantedStateAlreadySet());
        }

    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testSafeMaintenanceDisallowedWhenOtherDistributorInFlatClusterIsSuspended(int maxNumberOfGroupsAllowedToBeDown) {
        // Nodes 0-3, distributor 0 being down with "Orchestrator" description.
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        setDistributorNodeWantedState(cluster, 0, DOWN, "Orchestrator");
        var nodeStateChangeChecker = createChangeChecker(cluster);
        ClusterState clusterStateWith0InMaintenance = clusterState(String.format(
                "version:%d distributor:4 .0.s:d storage:4",
                currentClusterStateVersion));

        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 1), clusterStateWith0InMaintenance,
                SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("At most one node can have a wanted state when #groups = 1: Other distributor 0 has wanted state Down",
                result.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testSafeMaintenanceDisallowedWhenDistributorInGroupIsDown(int maxNumberOfGroupsAllowedToBeDown) {
        // Nodes 0-3, distributor 0 being in maintenance with "Orchestrator" description.
        // 2 groups: nodes 0-1 is group 0, 2-3 is group 1.
        ContentCluster cluster = createCluster(4, 2, maxNumberOfGroupsAllowedToBeDown);
        setDistributorNodeWantedState(cluster, 0, DOWN, "Orchestrator");
        var nodeStateChangeChecker = new NodeStateChangeChecker(cluster, false);
        ClusterState clusterStateWith0InMaintenance = clusterState(String.format(
                "version:%d distributor:4 .0.s:d storage:4",
                currentClusterStateVersion));

        {
            // Denied for node 2 in group 1, since distributor 0 in group 0 is down
            Result result = nodeStateChangeChecker.evaluateTransition(
                    new Node(STORAGE, 2), clusterStateWith0InMaintenance,
                    SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertFalse(result.settingWantedStateIsAllowed());
            assertFalse(result.wantedStateAlreadySet());
            if (maxNumberOfGroupsAllowedToBeDown >= 1)
                assertEquals("Wanted state already set for another node in groups: [0]", result.getReason());
            else
                assertEquals("At most one group can have wanted state: Other distributor 0 in group 0 has wanted state Down", result.getReason());
        }

        {
            // Even node 1 of group 0 is not permitted, as node 0 is not considered
            // suspended since only the distributor has been set down.
            Result result = nodeStateChangeChecker.evaluateTransition(
                    new Node(STORAGE, 1), clusterStateWith0InMaintenance,
                    SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            if (maxNumberOfGroupsAllowedToBeDown >= 1) {
                assertFalse(result.settingWantedStateIsAllowed(), result.getReason());
                assertEquals("Wanted state already set for another node in groups: [0]", result.getReason());
            } else {
                assertFalse(result.settingWantedStateIsAllowed(), result.getReason());
                assertEquals("Another distributor wants state DOWN: 0", result.getReason());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testSafeMaintenanceWhenOtherStorageNodeInGroupIsSuspended(int maxNumberOfGroupsAllowedToBeDown) {
        // Nodes 0-3, storage node 0 being in maintenance with "Orchestrator" description.
        // 2 groups: nodes 0-1 is group 0, 2-3 is group 1.
        ContentCluster cluster = createCluster(4, 2, maxNumberOfGroupsAllowedToBeDown);
        setStorageNodeWantedState(cluster, 0, MAINTENANCE, "Orchestrator");
        var nodeStateChangeChecker = new NodeStateChangeChecker(cluster, false);
        ClusterState clusterStateWith0InMaintenance = clusterState(String.format(
                "version:%d distributor:4 storage:4 .0.s:m",
                currentClusterStateVersion));

        {
            // Denied for node 2 in group 1, since node 0 in group 0 is in maintenance
            Result result = nodeStateChangeChecker.evaluateTransition(
                    new Node(STORAGE, 2), clusterStateWith0InMaintenance,
                    SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertFalse(result.settingWantedStateIsAllowed());
            assertFalse(result.wantedStateAlreadySet());
            if (maxNumberOfGroupsAllowedToBeDown >= 1)
                assertEquals("At most 1 groups can have wanted state: [0]", result.getReason());
            else
                assertEquals("At most one group can have wanted state: Other storage node 0 in group 0 has wanted state Maintenance",
                             result.getReason());
        }

        {
            // Permitted for node 1 in group 0, since node 0 is already in maintenance with
            // description Orchestrator, and it is in the same group
            Result result = nodeStateChangeChecker.evaluateTransition(
                    new Node(STORAGE, 1), clusterStateWith0InMaintenance,
                    SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertTrue(result.settingWantedStateIsAllowed(), result.getReason());
            assertFalse(result.wantedStateAlreadySet());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testSafeSetStateDistributors(int maxNumberOfGroupsAllowedToBeDown) {
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(createCluster(1, 1, maxNumberOfGroupsAllowedToBeDown));
        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeDistributor, defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertTrue(result.getReason().contains("Safe-set of node state is only supported for storage nodes"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanUpgradeSafeMissingStorage(int maxNumberOfGroupsAllowedToBeDown) {
        // Create a content cluster with 4 nodes, and storage node with index 3 down.
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 5, 6)));
        cluster.clusterInfo().getStorageNodeInfo(3).setReportedState(new NodeState(STORAGE, DOWN), 0);
        ClusterState clusterStateWith3Down = clusterState(String.format(
                "version:%d distributor:4 storage:4 .3.s:d",
                currentClusterStateVersion));

        // We should then be denied setting storage node 1 safely to maintenance.
        var nodeStateChangeChecker = createChangeChecker(cluster);
        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, clusterStateWith3Down, SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Another storage node has state DOWN: 3", result.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanUpgradeStorageSafeYes(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = transitionToMaintenanceWithNoStorageNodesDown(createCluster(4, 1, maxNumberOfGroupsAllowedToBeDown), defaultAllUpClusterState());
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testSetUpFailsIfReportedIsDown(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        // Not setting nodes up -> all are down

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE,
                MAINTENANCE_NODE_STATE, UP_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    // A node may be reported as Up but have a generated state of Down if it's part of
    // nodes taken down implicitly due to a group having too low node availability.
    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testSetUpSucceedsIfReportedIsUpButGeneratedIsDown(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        markAllNodesAsReportingStateUp(cluster);

        ClusterState stateWithNodeDown = clusterState(String.format(
                "version:%d distributor:4 storage:4 .%d.s:d",
                currentClusterStateVersion, nodeStorage.getIndex()));

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, stateWithNodeDown, SAFE,
                MAINTENANCE_NODE_STATE, UP_NODE_STATE);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanSetUpEvenIfOldWantedStateIsDown(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 3, 6)));

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE,
                new NodeState(STORAGE, DOWN), UP_NODE_STATE);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanUpgradeStorageSafeNo(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 3, 6)));

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Distributor 0 says storage node 1 has buckets with redundancy as low as 3, but we require at least 4",
                result.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanUpgradeIfMissingMinReplicationFactor(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 3, 6)));

        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 3), defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanUpgradeIfStorageNodeMissingFromNodeInfo(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        String hostInfo = "{\n" +
                "    \"cluster-state-version\": 2,\n" +
                "    \"distributor\": {\n" +
                "        \"storage-nodes\": [\n" +
                "            {\n" +
                "                \"node-index\": 0,\n" +
                "                \"min-current-replication-factor\": " + requiredRedundancy + "\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}\n";
        setAllNodesUp(cluster, HostInfo.createHostInfo(hostInfo));

        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 1), defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testMissingDistributorState(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        cluster.clusterInfo().getStorageNodeInfo(1).setReportedState(new NodeState(STORAGE, UP), 0);

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Distributor node 0 has not reported any cluster state version yet.", result.getReason());
    }

    private Result transitionToSameState(State state, String oldDescription, String newDescription, int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        NodeState currentNodeState = createNodeState(state, oldDescription);
        NodeState newNodeState = createNodeState(state, newDescription);
        return nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE,
                currentNodeState, newNodeState);
    }

    private Result transitionToSameState(String oldDescription, String newDescription, int maxNumberOfGroupsAllowedToBeDown) {
        return transitionToSameState(MAINTENANCE, oldDescription, newDescription, maxNumberOfGroupsAllowedToBeDown);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testSettingUpWhenUpCausesAlreadySet(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = transitionToSameState(UP, "foo", "bar", maxNumberOfGroupsAllowedToBeDown);
        assertTrue(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testSettingAlreadySetState(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = transitionToSameState("foo", "foo", maxNumberOfGroupsAllowedToBeDown);
        assertFalse(result.settingWantedStateIsAllowed());
        assertTrue(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testDifferentDescriptionImpliesDenied(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = transitionToSameState("foo", "bar", maxNumberOfGroupsAllowedToBeDown);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    private Result transitionToMaintenanceWithOneStorageNodeDown(ContentCluster cluster, ClusterState clusterState) {
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        for (int x = 0; x < cluster.clusterInfo().getConfiguredNodes().size(); x++) {
            cluster.clusterInfo().getDistributorNodeInfo(x).setReportedState(new NodeState(DISTRIBUTOR, UP), 0);
            cluster.clusterInfo().getDistributorNodeInfo(x).setHostInfo(HostInfo.createHostInfo(createDistributorHostInfo(4, 5, 6)));
            cluster.clusterInfo().getStorageNodeInfo(x).setReportedState(new NodeState(STORAGE, UP), 0);
        }

        return nodeStateChangeChecker.evaluateTransition(
                nodeStorage, clusterState, SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
    }

    private void setAllNodesUp(ContentCluster cluster, HostInfo distributorHostInfo) {
        for (int x = 0; x < cluster.clusterInfo().getConfiguredNodes().size(); x++) {
            State state = UP;
            cluster.clusterInfo().getDistributorNodeInfo(x).setReportedState(new NodeState(DISTRIBUTOR, state), 0);
            cluster.clusterInfo().getDistributorNodeInfo(x).setHostInfo(distributorHostInfo);
            cluster.clusterInfo().getStorageNodeInfo(x).setReportedState(new NodeState(STORAGE, state), 0);
        }
    }

    private Result transitionToMaintenanceWithNoStorageNodesDown(ContentCluster cluster, ClusterState clusterState) {
        return transitionToMaintenanceWithOneStorageNodeDown(cluster, clusterState);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanUpgradeWhenAllUp(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = transitionToMaintenanceWithNoStorageNodesDown(createCluster(4, maxNumberOfGroupsAllowedToBeDown), defaultAllUpClusterState());
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanUpgradeWhenAllUpOrRetired(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = transitionToMaintenanceWithNoStorageNodesDown(createCluster(4, maxNumberOfGroupsAllowedToBeDown), defaultAllUpClusterState());
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCanUpgradeWhenStorageIsDown(int maxNumberOfGroupsAllowedToBeDown) {
        ClusterState clusterState = defaultAllUpClusterState();
        var storageNodeIndex = nodeStorage.getIndex();

        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeState downNodeState = new NodeState(STORAGE, DOWN);
        cluster.clusterInfo().getStorageNodeInfo(storageNodeIndex).setReportedState(downNodeState, 4 /* time */);
        clusterState.setNodeState(new Node(STORAGE, storageNodeIndex), downNodeState);

        Result result = transitionToMaintenanceWithOneStorageNodeDown(cluster, clusterState);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testCannotUpgradeWhenOtherStorageIsDown(int maxNumberOfGroupsAllowedToBeDown) {
        int otherIndex = 2;
        // If this fails, just set otherIndex to some other valid index.
        assertNotEquals(nodeStorage.getIndex(), otherIndex);

        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        ClusterState clusterState = defaultAllUpClusterState();
        NodeState downNodeState = new NodeState(STORAGE, DOWN);
        cluster.clusterInfo().getStorageNodeInfo(otherIndex).setReportedState(downNodeState, 4 /* time */);
        clusterState.setNodeState(new Node(STORAGE, otherIndex), downNodeState);

        Result result = transitionToMaintenanceWithOneStorageNodeDown(cluster, clusterState);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertTrue(result.getReason().contains("Another storage node has state DOWN: 2"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testNodeRatioRequirementConsidersGeneratedNodeStates(int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        markAllNodesAsReportingStateUp(cluster);

        // Both minRatioOfStorageNodesUp and minStorageNodesUp imply that a single node being
        // in state Down should halt the upgrade. This must also take into account the generated
        // state, not just the reported state. In this case, all nodes are reported as being Up
        // but one node has a generated state of Down.
        ClusterState stateWithNodeDown = clusterState(String.format(
                "version:%d distributor:4 storage:4 .3.s:d",
                currentClusterStateVersion));

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, stateWithNodeDown, SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testDownDisallowedByNonRetiredState(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = evaluateDownTransition(
                defaultAllUpClusterState(),
                UP,
                currentClusterStateVersion,
                0,
                maxNumberOfGroupsAllowedToBeDown);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Only retired nodes are allowed to be set to DOWN in safe mode - is Up", result.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testDownDisallowedByBuckets(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = evaluateDownTransition(
                retiredClusterStateSuffix(),
                UP,
                currentClusterStateVersion,
                1,
                maxNumberOfGroupsAllowedToBeDown);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("The storage node manages 1 buckets", result.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testDownDisallowedByReportedState(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = evaluateDownTransition(
                retiredClusterStateSuffix(),
                INITIALIZING,
                currentClusterStateVersion,
                0,
                maxNumberOfGroupsAllowedToBeDown);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Reported state (Initializing) is not UP, so no bucket data is available", result.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testDownDisallowedByVersionMismatch(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = evaluateDownTransition(
                retiredClusterStateSuffix(),
                UP,
                currentClusterStateVersion - 1,
                0,
                maxNumberOfGroupsAllowedToBeDown);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Cluster controller at version 2 got info for storage node 1 at a different version 1",
                result.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void testAllowedToSetDown(int maxNumberOfGroupsAllowedToBeDown) {
        Result result = evaluateDownTransition(
                retiredClusterStateSuffix(),
                UP,
                currentClusterStateVersion,
                0,
                maxNumberOfGroupsAllowedToBeDown);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    private Result evaluateDownTransition(ClusterState clusterState,
                                          State reportedState,
                                          int hostInfoClusterStateVersion,
                                          int lastAlldisksBuckets,
                                          int maxNumberOfGroupsAllowedToBeDown) {
        ContentCluster cluster = createCluster(4, maxNumberOfGroupsAllowedToBeDown);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        StorageNodeInfo nodeInfo = cluster.clusterInfo().getStorageNodeInfo(nodeStorage.getIndex());
        nodeInfo.setReportedState(new NodeState(STORAGE, reportedState), 0);
        nodeInfo.setHostInfo(createHostInfoWithMetrics(hostInfoClusterStateVersion, lastAlldisksBuckets));

        return nodeStateChangeChecker.evaluateTransition(
                nodeStorage, clusterState, SAFE,
                UP_NODE_STATE, DOWN_NODE_STATE);
    }

    private ClusterState retiredClusterStateSuffix() {
        return clusterState(String.format("version:%d distributor:4 storage:4 .%d.s:r",
                currentClusterStateVersion,
                nodeStorage.getIndex()));
    }

    private static HostInfo createHostInfoWithMetrics(int clusterStateVersion, int lastAlldisksBuckets) {
        return HostInfo.createHostInfo(String.format("{\n" +
                        "  \"metrics\":\n" +
                        "  {\n" +
                        "    \"snapshot\":\n" +
                        "    {\n" +
                        "      \"from\":1494940706,\n" +
                        "      \"to\":1494940766\n" +
                        "    },\n" +
                        "    \"values\":\n" +
                        "    [\n" +
                        "      {\n" +
                        "        \"name\":\"vds.datastored.alldisks.buckets\",\n" +
                        "        \"description\":\"buckets managed\",\n" +
                        "        \"values\":\n" +
                        "        {\n" +
                        "          \"average\":262144.0,\n" +
                        "          \"count\":1,\n" +
                        "          \"rate\":0.016666,\n" +
                        "          \"min\":262144,\n" +
                        "          \"max\":262144,\n" +
                        "          \"last\":%d\n" +
                        "        },\n" +
                        "        \"dimensions\":\n" +
                        "        {\n" +
                        "        }\n" +
                        "      },\n" +
                        "      {\n" +
                        "        \"name\":\"vds.datastored.alldisks.docs\",\n" +
                        "        \"description\":\"documents stored\",\n" +
                        "        \"values\":\n" +
                        "        {\n" +
                        "          \"average\":154689587.0,\n" +
                        "          \"count\":1,\n" +
                        "          \"rate\":0.016666,\n" +
                        "          \"min\":154689587,\n" +
                        "          \"max\":154689587,\n" +
                        "          \"last\":154689587\n" +
                        "        },\n" +
                        "        \"dimensions\":\n" +
                        "        {\n" +
                        "        }\n" +
                        "      },\n" +
                        "      {\n" +
                        "        \"name\":\"vds.datastored.bucket_space.buckets_total\",\n" +
                        "        \"description\":\"Total number buckets present in the bucket space (ready + not ready)\",\n" +
                        "        \"values\":\n" +
                        "        {\n" +
                        "          \"average\":0.0,\n" +
                        "          \"sum\":0.0,\n" +
                        "          \"count\":1,\n" +
                        "          \"rate\":0.016666,\n" +
                        "          \"min\":0,\n" +
                        "          \"max\":0,\n" +
                        "          \"last\":0\n" +
                        "        },\n" +
                        "        \"dimensions\":\n" +
                        "        {\n" +
                        "          \"bucketSpace\":\"global\"\n" +
                        "        }\n" +
                        "      },\n" +
                        "      {\n" +
                        "        \"name\":\"vds.datastored.bucket_space.buckets_total\",\n" +
                        "        \"description\":\"Total number buckets present in the bucket space (ready + not ready)\",\n" +
                        "        \"values\":\n" +
                        "        {\n" +
                        "          \"average\":129.0,\n" +
                        "          \"sum\":129.0,\n" +
                        "          \"count\":1,\n" +
                        "          \"rate\":0.016666,\n" +
                        "          \"min\":129,\n" +
                        "          \"max\":129,\n" +
                        "          \"last\":%d\n" +
                        "        },\n" +
                        "        \"dimensions\":\n" +
                        "        {\n" +
                        "          \"bucketSpace\":\"default\"\n" +
                        "        }\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  },\n" +
                        "  \"cluster-state-version\":%d\n" +
                        "}",
                lastAlldisksBuckets, lastAlldisksBuckets, clusterStateVersion));
    }

    private List<ConfiguredNode> createNodes(int count) {
        List<ConfiguredNode> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++)
            nodes.add(new ConfiguredNode(i, false));
        return nodes;
    }

    private StorDistributionConfig createDistributionConfig(int nodes) {
        var configBuilder = new StorDistributionConfig.Builder()
                .ready_copies(requiredRedundancy)
                .redundancy(requiredRedundancy)
                .initial_redundancy(requiredRedundancy);

        var groupBuilder = new StorDistributionConfig.Group.Builder()
                                    .index("invalid")
                                    .name("invalid")
                                    .capacity(nodes);
        int nodeIndex = 0;
        for (int j = 0; j < nodes; ++j, ++nodeIndex) {
            groupBuilder.nodes(new StorDistributionConfig.Group.Nodes.Builder()
                                       .index(nodeIndex));
        }
        configBuilder.group(groupBuilder);

        return configBuilder.build();
    }

    // When more than 1 group
    private StorDistributionConfig createDistributionConfig(int nodes, int groups) {
        if (groups == 1) return createDistributionConfig(nodes);

        if (nodes % groups != 0)
            throw new IllegalArgumentException("Cannot have " + groups + " groups with an odd number of nodes: " + nodes);

        int nodesPerGroup = nodes / groups;

        var configBuilder = new StorDistributionConfig.Builder()
                .active_per_leaf_group(true)
                .ready_copies(groups)
                .redundancy(groups)
                .initial_redundancy(groups);

        configBuilder.group(new StorDistributionConfig.Group.Builder()
                                    .index("invalid")
                                    .name("invalid")
                                    .capacity(nodes)
                                    .partitions("1|*"));

        int nodeIndex = 0;
        for (int i = 0; i < groups; ++i) {
            var groupBuilder = new StorDistributionConfig.Group.Builder()
                    .index(String.valueOf(i))
                    .name(String.valueOf(i))
                    .capacity(nodesPerGroup)
                    .partitions("");
            for (int j = 0; j < nodesPerGroup; ++j, ++nodeIndex) {
                groupBuilder.nodes(new StorDistributionConfig.Group.Nodes.Builder()
                                           .index(nodeIndex));
            }
            configBuilder.group(groupBuilder);
        }
        return configBuilder.build();
    }

    private void checkSettingToMaintenanceIsAllowed(int nodeIndex, NodeStateChangeChecker nodeStateChangeChecker, ClusterState clusterState) {
        Node node = new Node(STORAGE, nodeIndex);
        Result result = nodeStateChangeChecker.evaluateTransition(node, clusterState, SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertTrue(result.settingWantedStateIsAllowed(), result.toString());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Preconditions fulfilled and new state different", result.getReason());
    }

    private void setStorageNodeWantedStateToMaintenance(ContentCluster cluster, int nodeIndex) {
        setStorageNodeWantedState(cluster, nodeIndex, MAINTENANCE, "Orchestrator");
    }

    private void setStorageNodeWantedState(ContentCluster cluster, int nodeIndex, State state, String description) {
        NodeState nodeState = new NodeState(STORAGE, state);
        cluster.clusterInfo().getStorageNodeInfo(nodeIndex).setWantedState(nodeState.setDescription(description));
    }

    private void setDistributorNodeWantedState(ContentCluster cluster, int nodeIndex, State state, String description) {
        NodeState nodeState = new NodeState(DISTRIBUTOR, state);
        cluster.clusterInfo().getDistributorNodeInfo(nodeIndex).setWantedState(nodeState.setDescription(description));
    }

}
