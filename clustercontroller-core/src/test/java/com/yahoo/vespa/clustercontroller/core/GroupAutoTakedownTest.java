// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.DiskState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;

import static com.yahoo.vespa.clustercontroller.core.matchers.EventForNode.eventForNode;
import static com.yahoo.vespa.clustercontroller.core.matchers.NodeEventWithDescription.nodeEventWithDescription;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        fixture.setMinNodeRatioPerGroup(minNodeRatioPerGroup);
        fixture.disableTransientMaintenanceModeOnDown();
        fixture.disableAutoClusterTakedown();
        fixture.bringEntireClusterUp();
    }

    private String stateAfterStorageTransition(ClusterFixture fixture, final int index, final State state) {
        transitionStorageNodeToState(fixture, index, state);
        return fixture.generatedClusterState();
    }

    private String verboseStateAfterStorageTransition(ClusterFixture fixture, final int index, final State state) {
        transitionStorageNodeToState(fixture, index, state);
        return fixture.verboseGeneratedClusterState();
    }

    private void transitionStorageNodeToState(ClusterFixture fixture, int index, State state) {
        fixture.reportStorageNodeState(index, state);
    }

    private AnnotatedClusterState annotatedStateAfterStorageTransition(ClusterFixture fixture, final int index, final State state) {
        transitionStorageNodeToState(fixture, index, state);
        return fixture.annotatedGeneratedClusterState();
    }

    /**
     * Use a per-group availability requirement ratio of 99%. Ensure that taking down a single
     * node out of 5 in a flat hierarchy does not take down the cluster, i.e. the config should
     * not apply to a flat structure.
     */
    @Test
    public void config_does_not_apply_to_flat_hierarchy_clusters() {
        ClusterFixture fixture = createFixtureForAllUpFlatCluster(5, 0.99);

        assertEquals("distributor:5 storage:5", fixture.generatedClusterState());

        assertEquals("distributor:5 storage:5 .1.s:d",
                     stateAfterStorageTransition(fixture, 1, State.DOWN));
    }

    @Test
    public void group_node_down_edge_implicitly_marks_down_rest_of_nodes_in_group() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        assertEquals("distributor:6 storage:6", fixture.generatedClusterState());

        // Same group as node 4
        assertEquals("distributor:6 storage:4",
                     stateAfterStorageTransition(fixture, 5, State.DOWN));
        // Same group as node 1
        assertEquals("distributor:6 storage:4 .0.s:d .1.s:d",
                     stateAfterStorageTransition(fixture, 0, State.DOWN));
    }

    @Test
    public void restored_group_node_availability_takes_group_back_up_automatically() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        // Group #2 -> down
        assertEquals("distributor:6 storage:4",
                     stateAfterStorageTransition(fixture, 5, State.DOWN));

        // Group #2 -> back up again
        assertEquals("distributor:6 storage:6",
                     stateAfterStorageTransition(fixture, 5, State.UP));
    }

    @Test
    public void no_op_for_downed_nodes_in_already_downed_group() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        assertEquals("distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));

        // 4, 5 in same group; this should not cause a new state since it's already implicitly down
        fixture.reportStorageNodeState(4, State.DOWN);
        assertEquals("distributor:6 storage:4", fixture.generatedClusterState());
    }

    @Test
    public void verbose_node_state_description_updated_for_implicitly_downed_nodes() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.75);

        // Nodes 6 and 7 are taken down implicitly and should have a message reflecting this.
        // Node 8 is taken down by the fixture and gets a fixture-assigned message that
        // we should _not_ lose/overwrite.
        assertEquals("distributor:9 storage:9 .6.s:d " +
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
        fixture.setMinNodesUp(5, 5, 0.51, 0.51);

        // Taking down a node in a group forces the entire group down, which leaves us with
        // only 4 content nodes (vs. minimum of 5 as specified above). The entire cluster
        // should be marked as down in this case.
        assertEquals("cluster:d distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));
    }

    @Test
    public void maintenance_wanted_state_not_overwritten() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.99);

        fixture.proposeStorageNodeWantedState(5, State.MAINTENANCE);
        // Maintenance not counted as down, so group still up
        assertEquals("distributor:9 storage:9 .5.s:m", fixture.generatedClusterState());

        // Group goes down, but maintenance node should still be in maintenance
        assertEquals("distributor:9 storage:9 .3.s:d .4.s:d .5.s:m",
                stateAfterStorageTransition(fixture, 4, State.DOWN));
    }

    @Test
    public void transient_maintenance_mode_on_down_edge_does_not_take_down_group() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.99);
        fixture.enableTransientMaintenanceModeOnDown(1000);

        // Our timers are mocked, so taking down node 4 will deterministically transition to
        // a transient maintenance mode. Group should not be taken down here.
        assertEquals("distributor:9 storage:9 .4.s:m",
                stateAfterStorageTransition(fixture, 4, State.DOWN));

        // However, once grace period expires the group should be taken down.
        fixture.timer.advanceTime(1001);
        NodeStateOrHostInfoChangeHandler changeListener = mock(NodeStateOrHostInfoChangeHandler.class);
        fixture.nodeStateChangeHandler.watchTimers(
                fixture.cluster, fixture.annotatedGeneratedClusterState().getClusterState(), changeListener);

        assertEquals("distributor:9 storage:9 .3.s:d .4.s:d .5.s:d", fixture.generatedClusterState());
    }

    private static Node contentNode(int index) {
        return new Node(NodeType.STORAGE, index);
    }

    @Test
    public void taking_down_node_adds_node_specific_event() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        final List<Event> events = EventDiffCalculator.computeEventDiff(EventDiffCalculator.params()
                .cluster(fixture.cluster)
                .fromState(ClusterStateBundle.ofBaselineOnly(fixture.annotatedGeneratedClusterState()))
                .toState(ClusterStateBundle.ofBaselineOnly(annotatedStateAfterStorageTransition(fixture, 5, State.DOWN))));

        assertThat(events, hasItem(allOf(
                nodeEventWithDescription("Group node availability is below configured threshold"),
                eventForNode(contentNode(4)))));
    }

    @Test
    public void bringing_node_back_up_adds_node_specific_event() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        assertEquals("distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));

        final List<Event> events = EventDiffCalculator.computeEventDiff(EventDiffCalculator.params()
                .cluster(fixture.cluster)
                .fromState(ClusterStateBundle.ofBaselineOnly(fixture.annotatedGeneratedClusterState()))
                .toState(ClusterStateBundle.ofBaselineOnly(annotatedStateAfterStorageTransition(fixture, 5, State.UP))));

        assertThat(events, hasItem(allOf(
                nodeEventWithDescription("Group node availability has been restored"),
                eventForNode(contentNode(4)))));
    }

    @Test
    public void wanted_state_retired_implicitly_down_node_is_transitioned_to_retired_mode_immediately() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.99);

        assertEquals("distributor:9 storage:6",
                stateAfterStorageTransition(fixture, 6, State.DOWN));
        // Node 7 is implicitly down. Mark wanted state as retired. It should now be Retired
        // but not Down.
        fixture.proposeStorageNodeWantedState(7, State.RETIRED);

        assertEquals("distributor:9 storage:8 .6.s:d .7.s:r", fixture.generatedClusterState());
    }

    @Test
    public void downed_config_retired_node_transitions_back_to_retired_on_up_edge() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.49);

        assertEquals("distributor:6 storage:6 .4.s:d",
                stateAfterStorageTransition(fixture, 4, State.DOWN));
        assertEquals("distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));

        // Node 5 gets config-retired under our feet.
        Set<ConfiguredNode> nodes = new HashSet<>(fixture.cluster.clusterInfo().getConfiguredNodes().values());
        nodes.remove(new ConfiguredNode(5, false));
        nodes.add(new ConfiguredNode(5, true));
        // TODO this should ideally also set the retired flag in the distribution
        // config, but only the ConfiguredNodes are actually looked at currently.
        fixture.cluster.setNodes(nodes);

        assertEquals("distributor:6 storage:6 .4.s:d .5.s:r",
                stateAfterStorageTransition(fixture, 5, State.UP));
    }

    @Test
    public void init_progress_is_preserved_across_group_down_up_edge() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        final NodeState newState = new NodeState(NodeType.STORAGE, State.INITIALIZING);
        newState.setInitProgress(0.5);

        fixture.reportStorageNodeState(4, newState);

        assertEquals("distributor:6 storage:6 .4.s:i .4.i:0.5", fixture.generatedClusterState());

        assertEquals("distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));
        assertEquals("distributor:6 storage:6 .4.s:i .4.i:0.5",
                stateAfterStorageTransition(fixture, 5, State.UP));
    }

    @Test
    public void disk_states_are_preserved_across_group_down_up_edge() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        final NodeState newState = new NodeState(NodeType.STORAGE, State.UP);
        newState.setDiskCount(7);
        newState.setDiskState(5, new DiskState(State.DOWN));

        fixture.reportStorageNodeState(4, newState);

        assertEquals("distributor:6 storage:6 .4.d:7 .4.d.5.s:d", fixture.generatedClusterState());

        assertEquals("distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));
        assertEquals("distributor:6 storage:6 .4.d:7 .4.d.5.s:d",
                stateAfterStorageTransition(fixture, 5, State.UP));
    }

    @Test
    public void down_wanted_state_is_preserved_across_group_down_up_edge() {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.60);

        fixture.proposeStorageNodeWantedState(5, State.DOWN, "borkbork");

        assertEquals("distributor:9 storage:9 .5.s:d .5.m:borkbork", fixture.verboseGeneratedClusterState());

        assertEquals("distributor:9 storage:9 " +
                ".3.s:d .3.m:group\\x20node\\x20availability\\x20below\\x20configured\\x20threshold " +
                ".4.s:d .4.m:mockdesc .5.s:d .5.m:borkbork",
                verboseStateAfterStorageTransition(fixture, 4, State.DOWN));
        assertEquals("distributor:9 storage:9 .5.s:d .5.m:borkbork",
                verboseStateAfterStorageTransition(fixture, 4, State.UP));
    }

    @Test
    public void previously_cleared_start_timestamps_are_not_reintroduced_on_up_edge() throws Exception {
        ClusterFixture fixture = createFixtureForAllUpHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        final NodeState newState = new NodeState(NodeType.STORAGE, State.UP);
        newState.setStartTimestamp(123456);

        fixture.reportStorageNodeState(4, newState);

        assertEquals("distributor:6 storage:6 .4.t:123456", fixture.generatedClusterState());

        DatabaseHandler handler = mock(DatabaseHandler.class);
        DatabaseHandler.Context context = mock(DatabaseHandler.Context.class);
        when(context.getCluster()).thenReturn(fixture.cluster);

        Set<ConfiguredNode> nodes = new HashSet<>(fixture.cluster.clusterInfo().getConfiguredNodes().values());
        fixture.nodeStateChangeHandler.handleAllDistributorsInSync(
                fixture.annotatedGeneratedClusterState().getClusterState(), nodes, handler, context);

        // Timestamp should now be cleared from state
        assertEquals("distributor:6 storage:6", fixture.generatedClusterState());

        // Trigger a group down+up edge. Timestamp should _not_ be reintroduced since it was previously cleared.
        assertEquals("distributor:6 storage:4",
                stateAfterStorageTransition(fixture, 5, State.DOWN));
        assertEquals("distributor:6 storage:6",
                stateAfterStorageTransition(fixture, 5, State.UP));
    }

}
