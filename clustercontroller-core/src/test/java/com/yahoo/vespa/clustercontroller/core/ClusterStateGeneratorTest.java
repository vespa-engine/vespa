// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.DiskState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ClusterStateGeneratorTest {

    private static ClusterState generateFromFixtureWithDefaultParams(ClusterFixture fixture) {
        final ClusterStateGenerator.Params params = new ClusterStateGenerator.Params();
        params.cluster = fixture.cluster;
        params.transitionTimes = ClusterFixture.buildTransitionTimeMap(0, 0);
        params.currentTimeInMillis = 0;
        return ClusterStateGenerator.generatedStateFrom(params);
    }

    @Test
    public void cluster_with_all_nodes_reported_down_has_state_down() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(6).markEntireClusterDown();
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.getClusterState(), is(State.DOWN));
    }

    @Test
    public void cluster_with_all_nodes_up_state_correct_distributor_and_storage_count() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(6).bringEntireClusterUp();
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:6 storage:6"));
    }

    @Test
    public void distributor_reported_states_reflected_in_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(9)
                .bringEntireClusterUp()
                .reportDistributorNodeState(2, State.DOWN)
                .reportDistributorNodeState(4, State.STOPPING);
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:9 .2.s:d .4.s:s storage:9"));
    }

    // NOTE: initializing state tested separately since it involves init progress state info
    @Test
    public void storage_reported_states_reflected_in_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(9)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, State.DOWN)
                .reportStorageNodeState(4, State.STOPPING);
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:9 storage:9 .0.s:d .4.s:s"));
    }

    @Test
    public void storage_reported_disk_state_included_in_generated_state() {
        final NodeState stateWithDisks = new NodeState(NodeType.STORAGE, State.UP);
        stateWithDisks.setDiskCount(7);
        stateWithDisks.setDiskState(5, new DiskState(State.DOWN));

        final ClusterFixture fixture = ClusterFixture.forFlatCluster(9)
                .bringEntireClusterUp()
                .reportStorageNodeState(2, stateWithDisks);
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:9 storage:9 .2.d:7 .2.d.5.s:d"));
    }

    @Test
    public void worse_distributor_wanted_state_overrides_reported_state() {
        // Maintenance mode is illegal for distributors and therefore not tested
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .proposeDistributorWantedState(5, State.DOWN) // Down worse than Up
                .reportDistributorNodeState(2, State.STOPPING)
                .proposeDistributorWantedState(2, State.DOWN); // Down worse than Stopping
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 .2.s:d .5.s:d storage:7"));
    }

    @Test
    public void worse_storage_wanted_state_overrides_reported_state() {
        // Does not test all maintenance mode overrides; see maintenance_mode_overrides_reported_state
        // for that.
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .reportStorageNodeState(2, State.STOPPING)
                .proposeStorageNodeWantedState(2, State.MAINTENANCE) // Maintenance worse than Stopping
                .proposeStorageNodeWantedState(4, State.RETIRED) // Retired is "worse" than Up
                .proposeStorageNodeWantedState(5, State.DOWN); // Down worse than Up
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 storage:7 .2.s:m .4.s:r .5.s:d"));
    }

    @Test
    public void better_distributor_wanted_state_does_not_override_reported_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .reportDistributorNodeState(0, State.DOWN)
                .proposeDistributorWantedState(0, State.UP);
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 .0.s:d storage:7"));
    }

    @Test
    public void better_storage_wanted_state_does_not_override_reported_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .reportStorageNodeState(1, State.DOWN)
                .proposeStorageNodeWantedState(1, State.UP)
                .reportStorageNodeState(2, State.DOWN)
                .proposeStorageNodeWantedState(2, State.RETIRED);
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 storage:7 .1.s:d .2.s:d"));
    }

    /**
     * If we let a Retired node be published as Initializing when it is in init state, we run
     * the risk of having both feed and merge ops be sent towards it, which is not what we want.
     * Consequently we pretend such nodes are never in init state and just transition them
     * directly from Maintenance -> Up.
     */
    @Test
    public void retired_node_in_init_state_is_set_to_maintenance() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(1, State.INITIALIZING)
                .proposeStorageNodeWantedState(1, State.RETIRED);
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:3 storage:3 .1.s:m"));
    }

    /**
     * Maintenance mode overrides all reported states, even Down.
     */
    @Test
    public void maintenance_mode_wanted_state_overrides_reported_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(0, State.MAINTENANCE)
                .reportStorageNodeState(2, State.STOPPING)
                .proposeStorageNodeWantedState(2, State.MAINTENANCE)
                .reportStorageNodeState(3, State.DOWN)
                .proposeStorageNodeWantedState(3, State.MAINTENANCE)
                .reportStorageNodeState(4, State.INITIALIZING)
                .proposeStorageNodeWantedState(4, State.MAINTENANCE);
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 storage:7 .0.s:m .2.s:m .3.s:m .4.s:m"));
    }

    @Test
    public void wanted_state_description_carries_over_to_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(1, State.MAINTENANCE, "foo")
                .proposeStorageNodeWantedState(2, State.DOWN, "bar")
                .proposeStorageNodeWantedState(3, State.RETIRED, "baz");
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        // We have to use toString(true) to get verbose printing including the descriptions,
        // as these are omitted by default.
        assertThat(state.toString(true), equalTo("distributor:7 storage:7 .1.s:m .1.m:foo " +
                ".2.s:d .2.m:bar .3.s:r .3.m:baz"));
    }

    // TODO verify behavior against legacy impl
    @Test
    public void reported_disk_state_not_hidden_by_wanted_state() {
        final NodeState stateWithDisks = new NodeState(NodeType.STORAGE, State.UP);
        stateWithDisks.setDiskCount(5);
        stateWithDisks.setDiskState(3, new DiskState(State.DOWN));

        final ClusterFixture fixture = ClusterFixture.forFlatCluster(9)
                .bringEntireClusterUp()
                .reportStorageNodeState(2, stateWithDisks)
                .proposeStorageNodeWantedState(2, State.RETIRED)
                .reportStorageNodeState(3, stateWithDisks)
                .proposeStorageNodeWantedState(3, State.MAINTENANCE);
        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        // TODO verify that it's correct that Down nodes don't report disk states..!
        assertThat(state.toString(), equalTo("distributor:9 storage:9 .2.s:r .2.d:5 .2.d.3.s:d " +
                ".3.s:m .3.d:5 .3.d.3.s:d"));
    }

    @Test
    public void config_retired_mode_is_reflected_in_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        List<ConfiguredNode> nodes = DistributionBuilder.buildConfiguredNodes(5);
        nodes.set(2, new ConfiguredNode(2, true));
        fixture.cluster.setNodes(nodes);

        final ClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:5 storage:5 .2.s:r"));
    }

    @Test
    public void reported_down_node_within_transition_time_has_maintenance_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = new ClusterStateGenerator.Params();
        params.cluster = fixture.cluster;
        // FIXME why do we even have transition times for distributors when they inherently
        // cannot go into maintenance mode..? Must only be for up -> down edge.
        params.transitionTimes = ClusterFixture.buildTransitionTimeMap(2000, 2000);
        params.currentTimeInMillis = 10_000;

        fixture.reportStorageNodeState(1, State.DOWN);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        // Node 1 transitioned to reported Down at time 9000ms after epoch. This means that according to the
        // above transition time config, it should remain in generated maintenance mode until time 11000ms,
        // at which point it should finally transition to generated state Down.
        nodeInfo.setTransitionTime(9000);
        {
            final ClusterState state = ClusterStateGenerator.generatedStateFrom(params);
            assertThat(state.toString(), equalTo("distributor:5 storage:5 .1.s:m"));
        }

        nodeInfo.setTransitionTime(10999);
        {
            final ClusterState state = ClusterStateGenerator.generatedStateFrom(params);
            assertThat(state.toString(), equalTo("distributor:5 storage:5 .1.s:m"));
        }
    }

    @Test
    public void reported_node_down_after_transition_time_has_down_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = new ClusterStateGenerator.Params();
        params.cluster = fixture.cluster;
        // FIXME why do we even have transition times for distributors when they inherently
        // cannot go into maintenance mode..? Must only be for up -> down edge.
        // FIXME de-dupe
        params.transitionTimes = ClusterFixture.buildTransitionTimeMap(2000, 2000);
        params.currentTimeInMillis = 11_000;

        fixture.reportStorageNodeState(1, State.DOWN);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        nodeInfo.setTransitionTime(9000);

        final ClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .1.s:d"));
    }

    @Test
    public void distributor_nodes_are_not_implicitly_transitioned_to_maintenance_mode() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = new ClusterStateGenerator.Params();
        params.cluster = fixture.cluster;
        // FIXME de-dupe
        params.transitionTimes = ClusterFixture.buildTransitionTimeMap(2000, 2000);
        params.currentTimeInMillis = 10_000;

        fixture.reportDistributorNodeState(2, State.DOWN);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.DISTRIBUTOR, 2));
        nodeInfo.setTransitionTime(9000);

        final ClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:5 .2.s:d storage:5"));
    }

    @Test
    public void transient_maintenance_mode_does_not_override_wanted_down_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = new ClusterStateGenerator.Params();
        params.cluster = fixture.cluster;
        // FIXME de-dupe
        params.transitionTimes = ClusterFixture.buildTransitionTimeMap(2000, 2000);
        params.currentTimeInMillis = 10_000;

        fixture.proposeStorageNodeWantedState(2, State.DOWN);
        fixture.reportStorageNodeState(2, State.DOWN);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 2));
        nodeInfo.setTransitionTime(9000);

        final ClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        // Should _not_ be in maintenance mode, since we explicitly want it to stay down.
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .2.s:d"));
    }

    // TODO deal with isRpcAddressOutdated() for implicit -> Down transitions?
}
