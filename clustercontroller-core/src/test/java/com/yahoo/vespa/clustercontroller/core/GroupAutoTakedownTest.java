// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.DiskState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.SystemStateListener;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class GroupAutoTakedownTest {

    private static ClusterFixture createFixtureForAllUpFlatCluster(int nodeCount, double minNodeRatioPerGroup) {
        ClusterFixture fixture = ClusterFixture.forFlatCluster(nodeCount);
        setSharedFixtureOptions(fixture, minNodeRatioPerGroup);
        return fixture;
    }

    private static ClusterFixture createFixtureForAllUpHierarchicCluster(
            DistributionBuilder.GroupBuilder root, double minNodeRatioPerGroup)
    {
        ClusterFixture fixture = ClusterFixture.forHierarchicCluster(root);
        setSharedFixtureOptions(fixture, minNodeRatioPerGroup);
        return fixture;
    }

    private static void setSharedFixtureOptions(ClusterFixture fixture, double minNodeRatioPerGroup) {
        fixture.generator.setMinNodeRatioPerGroup(minNodeRatioPerGroup);
        fixture.disableTransientMaintenanceModeOnDown();
        fixture.disableAutoClusterTakedown();
        fixture.bringEntireClusterUp();
    }

    private String stateAfterStorageTransition(ClusterFixture fixture, final int index, final State state) {
        transitionStoreNodeToState(fixture, index, state);
        return fixture.generatedClusterState();
    }

    private String verboseStateAfterStorageTransition(ClusterFixture fixture, final int index, final State state) {
        transitionStoreNodeToState(fixture, index, state);
        return fixture.verboseGeneratedClusterState();
    }

    private void transitionStoreNodeToState(ClusterFixture fixture, int index, State state) {
        fixture.reportStorageNodeState(index, state);
        SystemStateListener listener = mock(SystemStateListener.class);
        assertTrue(fixture.generator.notifyIfNewSystemState(fixture.cluster, listener));
    }

    /**
     * Use a per-group availability requirement ratio of 99%. Ensure that taking down a single
     * node out of 5 in a flat hierarchy does not take down the cluster, i.e. the config should
     * not apply to a flat structure.
     */
    @Test
    public void config_does_not_apply_to_flat_hierarchy_clusters() {
        ClusterFixture fixture = createFixtureForAllUpFlatCluster(5, 0.99);

        SystemStateListener listener = mock(SystemStateListener.class);
        // First invocation; generates initial state and clears "new state" flag
        assertTrue(fixture.generator.notifyIfNewSystemState(fixture.cluster, listener));
        assertEquals("version:1 distributor:5 storage:5", fixture.generatedClusterState());

        assertEquals("version:2 distributor:5 storage:5 .1.s:d",
                     stateAfterStorageTransition(fixture, 1, State.DOWN));
    }

    @Test
    public void group_node_down_edge_implicitly_marks_down_rest_of_nodes_in_group() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        SystemStateListener listener = mock(SystemStateListener.class);
        assertTrue(fixture.generator.notifyIfNewSystemState(fixture.cluster, listener));
        assertEquals("version:1 distributor:6 storage:6", fixture.generatedClusterState());

        // Same group as node 4
        assertEquals("version:2 distributor:6 storage:4",
                     stateAfterStorageTransition(fixture, 5, State.DOWN));
        // Same group as node 1
        assertEquals("version:3 distributor:6 storage:4 .0.s:d .1.s:d",
                     stateAfterStorageTransition(fixture, 0, State.DOWN));
    }

    @Test
    public void restored_group_node_availability_takes_group_back_up_automatically() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        // Group #2 -> down
        assertEquals("version:1 distributor:6 storage:4",
                     stateAfterStorageTransition(fixture, 5, State.DOWN));

        // Group #2 -> back up again
        assertEquals("version:2 distributor:6 storage:6",
                     stateAfterStorageTransition(fixture, 5, State.UP));
    }

    @Test
    public void no_op_for_downed_nodes_in_already_downed_group() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        assertEquals("version:1 distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));

        // 4, 5 in same group; this should not cause a new state since it's already implicitly down
        fixture.reportStorageNodeState(4, State.DOWN);

        SystemStateListener listener = mock(SystemStateListener.class);
        assertFalse(fixture.generator.notifyIfNewSystemState(fixture.cluster, listener));

        assertEquals("version:1 distributor:6 storage:4", fixture.generatedClusterState());
    }

    @Test
    public void verbose_node_state_description_updated_for_implicitly_downed_nodes() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.75);

        // Nodes 6 and 7 are taken down implicitly and should have a message reflecting this.
        // Node 8 is taken down by the fixture and gets a fixture-assigned message that
        // we should _not_ lose/overwrite.
        assertEquals("version:1 distributor:9 storage:9 .6.s:d " +
                        ".6.m:group\\x20node\\x20availability\\x20below\\x20configured\\x20threshold " +
                        ".7.s:d " +
                        ".7.m:group\\x20node\\x20availability\\x20below\\x20configured\\x20threshold " +
                        ".8.s:d .8.m:mockdesc",
                     verboseStateAfterStorageTransition(fixture, 8, State.DOWN));
    }

    @Test
    public void legacy_cluster_wide_availabilty_ratio_is_computed_after_group_takedowns() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);
        fixture.generator.setMinNodesUp(5, 5, 0.51, 0.51);

        // Taking down a node in a group forces the entire group down, which leaves us with
        // only 4 content nodes (vs. minimum of 5 as specified above). The entire cluster
        // should be marked as down in this case.
        assertEquals("version:1 cluster:d distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));
    }

    @Test
    public void maintenance_wanted_state_not_overwritten() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.99);

        NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 5));
        fixture.generator.proposeNewNodeState(nodeInfo, new NodeState(NodeType.STORAGE, State.MAINTENANCE));
        SystemStateListener listener = mock(SystemStateListener.class);
        assertTrue(fixture.generator.notifyIfNewSystemState(fixture.cluster, listener));

        // Maintenance not counted as down, so group still up
        assertEquals("version:1 distributor:9 storage:9 .5.s:m", fixture.generatedClusterState());

        // Group goes down, but maintenance node should still be in maintenance
        assertEquals("version:2 distributor:9 storage:9 .3.s:d .4.s:d .5.s:m",
                stateAfterStorageTransition(fixture, 4, State.DOWN));
    }

    @Test
    public void transient_maintenance_mode_on_down_edge_does_not_take_down_group() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.99);
        fixture.enableTransientMaintenanceModeOnDown(1000);

        // Our timers are mocked, so taking down node 4 will deterministically transition to
        // a transient maintenance mode. Group should not be taken down here.
        assertEquals("version:1 distributor:9 storage:9 .4.s:m",
                stateAfterStorageTransition(fixture, 4, State.DOWN));

        // However, once grace period expires the group should be taken down.
        fixture.timer.advanceTime(1001);
        NodeStateOrHostInfoChangeHandler changeListener = mock(NodeStateOrHostInfoChangeHandler.class);
        fixture.generator.watchTimers(fixture.cluster, changeListener);
        SystemStateListener stateListener = mock(SystemStateListener.class);
        assertTrue(fixture.generator.notifyIfNewSystemState(fixture.cluster, stateListener));

        assertEquals("version:2 distributor:9 storage:9 .3.s:d .4.s:d .5.s:d", fixture.generatedClusterState());
    }

    private static class NodeEventWithDescription extends ArgumentMatcher<NodeEvent> {
        private final String expected;

        NodeEventWithDescription(String expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(Object o) {
            return expected.equals(((NodeEvent)o).getDescription());
        }
    }

    private static NodeEventWithDescription nodeEventWithDescription(String description) {
        return new NodeEventWithDescription(description);
    }

    private static class EventForNode extends ArgumentMatcher<NodeEvent> {
        private final Node expected;

        EventForNode(Node expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(Object o) {
            return ((NodeEvent)o).getNode().getNode().equals(expected);
        }
    }

    private static EventForNode eventForNode(Node expected) {
        return new EventForNode(expected);
    }

    private static Node contentNode(int index) {
        return new Node(NodeType.STORAGE, index);
    }

    @Test
    public void taking_down_node_adds_node_specific_event() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        assertEquals("version:1 distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));

        verify(fixture.eventLog).addNodeOnlyEvent(argThat(allOf(
                nodeEventWithDescription("Setting node down as the total availability of its group is " +
                        "below the configured threshold"),
                eventForNode(contentNode(4)))), any());
    }

    @Test
    public void bringing_node_back_up_adds_node_specific_event() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        assertEquals("version:1 distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));
        assertEquals("version:2 distributor:6 storage:6",
                stateAfterStorageTransition(fixture, 5, State.UP));

        verify(fixture.eventLog).addNodeOnlyEvent(argThat(allOf(
                nodeEventWithDescription("Group availability restored; taking node back up"),
                eventForNode(contentNode(4)))), any());
    }

    @Test
    public void wanted_state_retired_implicitly_down_node_transitioned_it_to_retired_mode_immediately() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.99);

        assertEquals("version:1 distributor:9 storage:6",
                stateAfterStorageTransition(fixture, 6, State.DOWN));
        // Node 7 is implicitly down. Mark wanted state as retired. It should now be Retired
        // but not Down.
        fixture.proposeStorageNodeWantedState(7, State.RETIRED);

        SystemStateListener stateListener = mock(SystemStateListener.class);
        assertTrue(fixture.generator.notifyIfNewSystemState(fixture.cluster, stateListener));
        assertEquals("version:2 distributor:9 storage:8 .6.s:d .7.s:r", fixture.generatedClusterState());
    }

    @Test
    public void downed_config_retired_node_transitions_back_to_retired_on_up_edge() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.49);

        assertEquals("version:1 distributor:6 storage:6 .4.s:d",
                stateAfterStorageTransition(fixture, 4, State.DOWN));
        assertEquals("version:2 distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));

        // Node 5 gets config-retired under our feet.
        Set<ConfiguredNode> nodes = new HashSet<>(fixture.cluster.clusterInfo().getConfiguredNodes().values());
        nodes.remove(new ConfiguredNode(5, false));
        nodes.add(new ConfiguredNode(5, true));
        // TODO this should ideally also set the retired flag in the distribution
        // config, but only the ConfiguredNodes are actually looked at currently.
        fixture.cluster.setNodes(nodes);
        fixture.generator.setNodes(fixture.cluster.clusterInfo());

        assertEquals("version:3 distributor:6 storage:6 .4.s:d .5.s:r",
                stateAfterStorageTransition(fixture, 5, State.UP));
    }

    @Test
    public void init_progress_is_preserved_across_group_down_up_edge() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        final Node node = new Node(NodeType.STORAGE, 4);
        final NodeState newState = new NodeState(NodeType.STORAGE, State.INITIALIZING);
        newState.setInitProgress(0.5);

        fixture.reportStorageNodeState(4, newState);
        SystemStateListener stateListener = mock(SystemStateListener.class);
        assertTrue(fixture.generator.notifyIfNewSystemState(fixture.cluster, stateListener));

        assertEquals("version:1 distributor:6 storage:6 .4.s:i .4.i:0.5", fixture.generatedClusterState());

        assertEquals("version:2 distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));
        assertEquals("version:3 distributor:6 storage:6 .4.s:i .4.i:0.5",
                stateAfterStorageTransition(fixture, 5, State.UP));
    }

    @Test
    public void disk_states_are_preserved_across_group_down_up_edge() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        final Node node = new Node(NodeType.STORAGE, 4);
        final NodeState newState = new NodeState(NodeType.STORAGE, State.UP);
        newState.setDiskCount(7);
        newState.setDiskState(5, new DiskState(State.DOWN));

        fixture.reportStorageNodeState(4, newState);
        SystemStateListener stateListener = mock(SystemStateListener.class);
        assertTrue(fixture.generator.notifyIfNewSystemState(fixture.cluster, stateListener));

        assertEquals("version:1 distributor:6 storage:6 .4.d:7 .4.d.5.s:d", fixture.generatedClusterState());

        assertEquals("version:2 distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));
        assertEquals("version:3 distributor:6 storage:6 .4.d:7 .4.d.5.s:d",
                stateAfterStorageTransition(fixture, 5, State.UP));
    }

    @Test
    public void down_wanted_state_is_preserved_across_group_down_up_edge() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.60);

        NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 5));
        nodeInfo.setWantedState(new NodeState(NodeType.STORAGE, State.DOWN).setDescription("borkbork"));
        fixture.generator.proposeNewNodeState(nodeInfo, nodeInfo.getWantedState());
        SystemStateListener listener = mock(SystemStateListener.class);
        assertTrue(fixture.generator.notifyIfNewSystemState(fixture.cluster, listener));

        assertEquals("version:1 distributor:9 storage:9 .5.s:d .5.m:borkbork", fixture.verboseGeneratedClusterState());

        assertEquals("version:2 distributor:9 storage:9 " +
                ".3.s:d .3.m:group\\x20node\\x20availability\\x20below\\x20configured\\x20threshold " +
                ".4.s:d .4.m:mockdesc .5.s:d .5.m:borkbork",
                verboseStateAfterStorageTransition(fixture, 4, State.DOWN));
        assertEquals("version:3 distributor:9 storage:9 .5.s:d .5.m:borkbork",
                verboseStateAfterStorageTransition(fixture, 4, State.UP));
    }

}
