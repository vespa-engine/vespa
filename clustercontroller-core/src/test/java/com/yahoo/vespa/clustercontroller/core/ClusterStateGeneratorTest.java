// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.clustercontroller.core.ClusterFixture.distributorNode;
import static com.yahoo.vespa.clustercontroller.core.ClusterFixture.storageNode;
import static com.yahoo.vespa.clustercontroller.core.matchers.HasStateReasonForNode.hasStateReasonForNode;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class ClusterStateGeneratorTest {

    private static AnnotatedClusterState generateFromFixtureWithDefaultParams(ClusterFixture fixture) {
        final ClusterStateGenerator.Params params = new ClusterStateGenerator.Params();
        params.cluster = fixture.cluster;
        params.transitionTimes = ClusterStateGenerator.Params.buildTransitionTimeMap(0, 0);
        params.currentTimeInMillis = 0;
        return ClusterStateGenerator.generatedStateFrom(params);
    }

    @Test
    void cluster_with_all_nodes_reported_down_has_state_down() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(6).markEntireClusterDown();
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.getClusterState().getClusterState(), is(State.DOWN));
        // The returned message in this case depends on which "is cluster down?" check
        // kicks in first. Currently, the minimum storage node count does.
        assertThat(state.getClusterStateReason(), equalTo(Optional.of(ClusterStateReason.TOO_FEW_STORAGE_NODES_AVAILABLE)));
    }

    @Test
    void cluster_with_all_nodes_up_state_correct_distributor_and_storage_count() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(6).bringEntireClusterUp();
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:6 storage:6"));
    }

    @Test
    void distributor_reported_states_reflected_in_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(9)
                .bringEntireClusterUp()
                .reportDistributorNodeState(2, State.DOWN)
                .reportDistributorNodeState(4, State.STOPPING);
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:9 .2.s:d .4.s:s storage:9"));
    }

    // NOTE: initializing state tested separately since it involves init progress state info
    @Test
    void storage_reported_states_reflected_in_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(9)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, State.DOWN)
                .reportStorageNodeState(4, State.STOPPING);
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:9 storage:9 .0.s:d .4.s:s"));
    }

    @Test
    void worse_distributor_wanted_state_overrides_reported_state() {
        // Maintenance mode is illegal for distributors and therefore not tested
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .proposeDistributorWantedState(5, State.DOWN) // Down worse than Up
                .reportDistributorNodeState(2, State.STOPPING)
                .proposeDistributorWantedState(2, State.DOWN); // Down worse than Stopping
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 .2.s:d .5.s:d storage:7"));
    }

    @Test
    void worse_storage_wanted_state_overrides_reported_state() {
        // Does not test all maintenance mode overrides; see maintenance_mode_overrides_reported_state
        // for that.
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .reportStorageNodeState(2, State.STOPPING)
                .proposeStorageNodeWantedState(2, State.MAINTENANCE) // Maintenance worse than Stopping
                .proposeStorageNodeWantedState(4, State.RETIRED) // Retired is "worse" than Up
                .proposeStorageNodeWantedState(5, State.DOWN); // Down worse than Up
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 storage:7 .2.s:m .4.s:r .5.s:d"));
    }

    @Test
    void better_distributor_wanted_state_does_not_override_reported_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .reportDistributorNodeState(0, State.DOWN)
                .proposeDistributorWantedState(0, State.UP);
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 .0.s:d storage:7"));
    }

    @Test
    void better_storage_wanted_state_does_not_override_reported_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .reportStorageNodeState(1, State.DOWN)
                .proposeStorageNodeWantedState(1, State.UP)
                .reportStorageNodeState(2, State.DOWN)
                .proposeStorageNodeWantedState(2, State.RETIRED);
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 storage:7 .1.s:d .2.s:d"));
    }

    /**
     * If we let a Retired node be published as Initializing when it is in init state, we run
     * the risk of having both feed and merge ops be sent towards it, which is not what we want.
     * Consequently we pretend such nodes are never in init state and just transition them
     * directly from Maintenance -> Up.
     */
    @Test
    void retired_node_in_init_state_is_set_to_maintenance() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(1, State.INITIALIZING)
                .proposeStorageNodeWantedState(1, State.RETIRED);
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:3 storage:3 .1.s:m"));
    }

    /**
     * A storage node will report itself as being in initializing mode immediately when
     * starting up. It can only accept external operations once it has finished listing
     * the set of buckets (but not necessarily their contents). As a consequence of this,
     * we have to map reported init state while bucket listing mode to Down. This will
     * prevent clients from thinking they can use the node and prevent distributors form
     * trying to fetch yet non-existent bucket sets from it.
     *
     * Detecting the bucket-listing stage is currently done by inspecting its init progress
     * value and triggering on a sufficiently low value.
     */
    @Test
    void storage_node_in_init_mode_while_listing_buckets_is_marked_down() {
        final NodeState initWhileListingBuckets = new NodeState(NodeType.STORAGE, State.INITIALIZING);
        initWhileListingBuckets.setInitProgress(0.0f);

        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(1, initWhileListingBuckets);

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("distributor:3 storage:3 .1.s:d"));
    }

    /**
     * Implicit down while reported as init should not kick into effect if the Wanted state
     * is set to Maintenance.
     */
    @Test
    void implicit_down_while_listing_buckets_does_not_override_wanted_state() {
        final NodeState initWhileListingBuckets = new NodeState(NodeType.STORAGE, State.INITIALIZING);
        initWhileListingBuckets.setInitProgress(0.0f);

        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(1, initWhileListingBuckets)
                .proposeStorageNodeWantedState(1, State.MAINTENANCE);

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("distributor:3 storage:3 .1.s:m"));
    }

    @Test
    void distributor_nodes_in_init_mode_are_not_mapped_to_down() {
        final NodeState initWhileListingBuckets = new NodeState(NodeType.DISTRIBUTOR, State.INITIALIZING);
        initWhileListingBuckets.setInitProgress(0.0f);

        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportDistributorNodeState(1, initWhileListingBuckets);

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("distributor:3 .1.s:i .1.i:0.0 storage:3"));
    }

    /**
     * Maintenance mode overrides all reported states, even Down.
     */
    @Test
    void maintenance_mode_wanted_state_overrides_reported_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(0, State.MAINTENANCE)
                .reportStorageNodeState(2, State.STOPPING)
                .proposeStorageNodeWantedState(2, State.MAINTENANCE)
                .reportStorageNodeState(3, State.DOWN)
                .proposeStorageNodeWantedState(3, State.MAINTENANCE)
                .reportStorageNodeState(4, State.INITIALIZING)
                .proposeStorageNodeWantedState(4, State.MAINTENANCE);
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:7 storage:7 .0.s:m .2.s:m .3.s:m .4.s:m"));
    }

    @Test
    void wanted_state_description_carries_over_to_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(7)
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(1, State.MAINTENANCE, "foo")
                .proposeStorageNodeWantedState(2, State.DOWN, "bar")
                .proposeStorageNodeWantedState(3, State.RETIRED, "baz");
        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        // We have to use toString(true) to get verbose printing including the descriptions,
        // as these are omitted by default.
        assertThat(state.toString(true), equalTo("distributor:7 storage:7 .1.s:m .1.m:foo " +
                ".2.s:d .2.m:bar .3.s:r .3.m:baz"));
    }

    @Test
    void config_retired_mode_is_reflected_in_generated_state() {
        ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .markNodeAsConfigRetired(2)
                .bringEntireClusterUp();

        AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:5 storage:5 .2.s:r"));
    }

    @Test
    void config_retired_mode_is_overridden_by_worse_wanted_state() {
        ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .markNodeAsConfigRetired(2)
                .markNodeAsConfigRetired(3)
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(2, State.DOWN)
                .proposeStorageNodeWantedState(3, State.MAINTENANCE);

        AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);

        assertThat(state.toString(), equalTo("distributor:5 storage:5 .2.s:d .3.s:m"));
    }

    private void do_test_change_within_node_transition_time_window_generates_maintenance(State reportedState) {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = fixture.generatorParams()
                .currentTimeInMillis(10_000)
                .transitionTimes(2000);

        fixture.reportStorageNodeState(1, reportedState);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        // Node 1 transitioned to reported `reportedState` at time 9000ms after epoch. This means that according to the
        // above transition time config, it should remain in generated maintenance mode until time 11000ms,
        // at which point it should finally transition to generated state Down.
        nodeInfo.setTransitionTime(9000);
        {
            final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
            assertThat(state.toString(), equalTo("distributor:5 storage:5 .1.s:m"));
        }

        nodeInfo.setTransitionTime(10999);
        {
            final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
            assertThat(state.toString(), equalTo("distributor:5 storage:5 .1.s:m"));
        }
    }

    @Test
    void reported_down_node_within_transition_time_has_maintenance_generated_state() {
        do_test_change_within_node_transition_time_window_generates_maintenance(State.DOWN);
    }

    @Test
    void reported_stopping_node_within_transition_time_has_maintenance_generated_state() {
        do_test_change_within_node_transition_time_window_generates_maintenance(State.STOPPING);
    }

    @Test
    void reported_node_down_after_transition_time_has_down_generated_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = fixture.generatorParams()
                .currentTimeInMillis(11_000)
                .transitionTimes(2000);

        fixture.reportStorageNodeState(1, State.DOWN);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        nodeInfo.setTransitionTime(9000);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .1.s:d"));
        assertThat(state.getNodeStateReasons(),
                hasStateReasonForNode(storageNode(1), NodeStateReason.NODE_NOT_BACK_UP_WITHIN_GRACE_PERIOD));
    }

    @Test
    void distributor_nodes_are_not_implicitly_transitioned_to_maintenance_mode() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = fixture.generatorParams()
                .currentTimeInMillis(10_000)
                .transitionTimes(2000);

        fixture.reportDistributorNodeState(2, State.DOWN);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.DISTRIBUTOR, 2));
        nodeInfo.setTransitionTime(9000);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:5 .2.s:d storage:5"));
        assertThat(state.getNodeStateReasons(),
                not(hasStateReasonForNode(distributorNode(1), NodeStateReason.NODE_NOT_BACK_UP_WITHIN_GRACE_PERIOD)));
    }

    @Test
    void transient_maintenance_mode_does_not_override_wanted_down_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = fixture.generatorParams()
                .currentTimeInMillis(10_000)
                .transitionTimes(2000);

        fixture.proposeStorageNodeWantedState(2, State.DOWN);
        fixture.reportStorageNodeState(2, State.DOWN);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 2));
        nodeInfo.setTransitionTime(9000);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        // Should _not_ be in maintenance mode, since we explicitly want it to stay down.
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .2.s:d"));
    }

    @Test
    void reported_down_retired_node_within_transition_time_transitions_to_maintenance() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = fixture.generatorParams()
                .currentTimeInMillis(10_000)
                .transitionTimes(2000);

        fixture.proposeStorageNodeWantedState(2, State.RETIRED);
        fixture.reportStorageNodeState(2, State.DOWN);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 2));
        nodeInfo.setTransitionTime(9000);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .2.s:m"));
    }

    @Test
    void crash_count_exceeding_limit_marks_node_as_down() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = fixture.generatorParams().maxPrematureCrashes(10);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 3));
        nodeInfo.setPrematureCrashCount(11);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .3.s:d"));
    }

    @Test
    void crash_count_not_exceeding_limit_does_not_mark_node_as_down() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5).bringEntireClusterUp();
        final ClusterStateGenerator.Params params = fixture.generatorParams().maxPrematureCrashes(10);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 3));
        nodeInfo.setPrematureCrashCount(10); // "Max crashes" range is inclusive

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:5 storage:5"));
    }

    @Test
    void exceeded_crash_count_does_not_override_wanted_maintenance_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(1, State.MAINTENANCE);
        final ClusterStateGenerator.Params params = fixture.generatorParams().maxPrematureCrashes(10);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        nodeInfo.setPrematureCrashCount(11);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .1.s:m"));
    }

    // Stopping -> Down is expected and does not indicate an unstable node.
    @Test
    void transition_from_controlled_stop_to_down_does_not_add_to_crash_counter() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(2)
                .bringEntireClusterUp()
                .reportStorageNodeState(1, State.STOPPING, "controlled shutdown") // urgh, string matching logic
                .reportStorageNodeState(1, State.DOWN);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        assertThat(nodeInfo.getPrematureCrashCount(), equalTo(0));
    }

    @Test
    void non_observed_storage_node_start_timestamp_is_included_in_state() {
        final NodeState nodeState = new NodeState(NodeType.STORAGE, State.UP);
        // A reported state timestamp that is not yet marked as observed in the NodeInfo
        // for the same node is considered not observed by other nodes and must therefore
        // be included in the generated cluster state
        nodeState.setStartTimestamp(5000);

        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, nodeState);

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .0.t:5000"));
    }

    @Test
    void non_observed_distributor_start_timestamp_is_included_in_state() {
        final NodeState nodeState = new NodeState(NodeType.DISTRIBUTOR, State.UP);
        nodeState.setStartTimestamp(6000);

        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .bringEntireClusterUp()
                .reportDistributorNodeState(1, nodeState);

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("distributor:5 .1.t:6000 storage:5"));
    }

    @Test
    void fully_observed_storage_node_timestamp_not_included_in_state() {
        final NodeState nodeState = new NodeState(NodeType.STORAGE, State.UP);
        nodeState.setStartTimestamp(5000);

        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, nodeState);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 0));
        nodeInfo.setStartTimestamp(5000);

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("distributor:5 storage:5"));
    }

    @Test
    void fully_observed_distributor_timestamp_not_included_in_state() {
        final NodeState nodeState = new NodeState(NodeType.DISTRIBUTOR, State.UP);
        nodeState.setStartTimestamp(6000);

        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .bringEntireClusterUp()
                .reportDistributorNodeState(0, nodeState);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.DISTRIBUTOR, 0));
        nodeInfo.setStartTimestamp(6000);

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("distributor:5 storage:5"));
    }

    @Test
    void cluster_down_if_less_than_min_count_of_storage_nodes_available() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, State.DOWN)
                .reportStorageNodeState(2, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minStorageNodesUp(2);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("cluster:d distributor:3 storage:2 .0.s:d"));
        assertThat(state.getClusterStateReason(), equalTo(Optional.of(ClusterStateReason.TOO_FEW_STORAGE_NODES_AVAILABLE)));
    }

    @Test
    void cluster_not_down_if_more_than_min_count_of_storage_nodes_are_available() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minStorageNodesUp(2);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:3 storage:3 .0.s:d"));
        assertThat(state.getClusterStateReason(), equalTo(Optional.empty()));
    }

    @Test
    void cluster_down_if_less_than_min_count_of_distributors_available() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportDistributorNodeState(0, State.DOWN)
                .reportDistributorNodeState(2, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minDistributorNodesUp(2);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("cluster:d distributor:2 .0.s:d storage:3"));
        assertThat(state.getClusterStateReason(), equalTo(Optional.of(ClusterStateReason.TOO_FEW_DISTRIBUTOR_NODES_AVAILABLE)));
    }

    @Test
    void cluster_not_down_if_more_than_min_count_of_distributors_are_available() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportDistributorNodeState(0, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minDistributorNodesUp(2);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:3 .0.s:d storage:3"));
        assertThat(state.getClusterStateReason(), equalTo(Optional.empty()));
    }

    @Test
    void maintenance_mode_counted_as_down_for_cluster_availability() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, State.DOWN)
                .proposeStorageNodeWantedState(2, State.MAINTENANCE);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minStorageNodesUp(2);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("cluster:d distributor:3 storage:3 .0.s:d .2.s:m"));
    }

    @Test
    void init_and_retired_counted_as_up_for_cluster_availability() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, State.INITIALIZING)
                .proposeStorageNodeWantedState(1, State.RETIRED);
        // Any node being treated as down should take down the cluster here
        final ClusterStateGenerator.Params params = fixture.generatorParams().minStorageNodesUp(3);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:3 storage:3 .0.s:i .0.i:1.0 .1.s:r"));
    }

    @Test
    void cluster_down_if_less_than_min_ratio_of_storage_nodes_available() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, State.DOWN)
                .reportStorageNodeState(2, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minRatioOfStorageNodesUp(0.5);

        // TODO de-dupe a lot of these tests?
        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("cluster:d distributor:3 storage:2 .0.s:d"));
        assertThat(state.getClusterStateReason(), equalTo(Optional.of(ClusterStateReason.TOO_LOW_AVAILABLE_STORAGE_NODE_RATIO)));
    }

    @Test
    void cluster_not_down_if_more_than_min_ratio_of_storage_nodes_available() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, State.DOWN);
        // Min node ratio is inclusive, i.e. 0.5 of 2 nodes is enough for cluster to be up.
        final ClusterStateGenerator.Params params = fixture.generatorParams().minRatioOfStorageNodesUp(0.5);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:3 storage:3 .0.s:d"));
        assertThat(state.getClusterStateReason(), equalTo(Optional.empty()));
    }

    @Test
    void cluster_down_if_less_than_min_ratio_of_distributors_available() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportDistributorNodeState(0, State.DOWN)
                .reportDistributorNodeState(2, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minRatioOfDistributorNodesUp(0.5);

        // TODO de-dupe a lot of these tests?
        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("cluster:d distributor:2 .0.s:d storage:3"));
        assertThat(state.getClusterStateReason(), equalTo(Optional.of(ClusterStateReason.TOO_LOW_AVAILABLE_DISTRIBUTOR_NODE_RATIO)));
    }

    @Test
    void cluster_not_down_if_more_than_min_ratio_of_distributors_available() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportDistributorNodeState(0, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minRatioOfDistributorNodesUp(0.5);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:3 .0.s:d storage:3"));
        assertThat(state.getClusterStateReason(), equalTo(Optional.empty()));
    }

    @Test
    void group_nodes_are_marked_down_if_group_availability_too_low() {
        final ClusterFixture fixture = ClusterFixture
                .forHierarchicCluster(DistributionBuilder.withGroups(3).eachWithNodeCount(3))
                .bringEntireClusterUp()
                .reportStorageNodeState(4, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minNodeRatioPerGroup(0.68);

        // Node 4 is down, which is more than 32% of nodes down in group #2. Nodes 3,5 should be implicitly
        // marked down as it is in the same group.
        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:9 storage:9 .3.s:d .4.s:d .5.s:d"));
    }

    @Test
    void group_nodes_are_not_marked_down_if_group_availability_sufficiently_high() {
        final ClusterFixture fixture = ClusterFixture
                .forHierarchicCluster(DistributionBuilder.withGroups(3).eachWithNodeCount(3))
                .bringEntireClusterUp()
                .reportStorageNodeState(4, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minNodeRatioPerGroup(0.65);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:9 storage:9 .4.s:d")); // No other nodes down implicitly
    }

    @Test
    void implicitly_downed_group_nodes_receive_a_state_description() {
        final ClusterFixture fixture = ClusterFixture
                .forHierarchicCluster(DistributionBuilder.withGroups(2).eachWithNodeCount(2))
                .bringEntireClusterUp()
                .reportStorageNodeState(3, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minNodeRatioPerGroup(0.51);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(true), equalTo("distributor:4 storage:4 " +
                ".2.s:d .2.m:group\\x20node\\x20availability\\x20below\\x20configured\\x20threshold " +
                ".3.s:d .3.m:mockdesc")); // Preserve description for non-implicitly taken down node
    }

    @Test
    void implicitly_downed_group_nodes_are_annotated_with_group_reason() {
        final ClusterFixture fixture = ClusterFixture
                .forHierarchicCluster(DistributionBuilder.withGroups(2).eachWithNodeCount(2))
                .bringEntireClusterUp()
                .reportStorageNodeState(3, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minNodeRatioPerGroup(0.51);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.getNodeStateReasons(),
                hasStateReasonForNode(storageNode(2), NodeStateReason.GROUP_IS_DOWN));
    }

    @Test
    void maintenance_nodes_in_downed_group_are_not_affected() {
        final ClusterFixture fixture = ClusterFixture
                .forHierarchicCluster(DistributionBuilder.withGroups(3).eachWithNodeCount(3))
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(3, State.MAINTENANCE)
                .reportStorageNodeState(4, State.DOWN);
        final ClusterStateGenerator.Params params = fixture.generatorParams().minNodeRatioPerGroup(0.68);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        // 4 is down by itself, 5 is down implicitly and 3 should happily stay in Maintenance mode.
        // Side note: most special cases for when a node should and should not be affected by group
        // down edges are covered in GroupAvailabilityCalculatorTest and GroupAutoTakedownTest.
        // We test this case explicitly since it's an assurance that code integration works as expected.
        assertThat(state.toString(), equalTo("distributor:9 storage:9 .3.s:m .4.s:d .5.s:d"));
    }

    @Test
    void group_nodes_are_marked_maintenance_if_group_availability_too_low_by_orchestrator() {
        final ClusterFixture fixture = ClusterFixture
                .forHierarchicCluster(DistributionBuilder.withGroups(3).eachWithNodeCount(3))
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(4, State.MAINTENANCE, NodeState.ORCHESTRATOR_RESERVED_DESCRIPTION)
                .proposeStorageNodeWantedState(5, State.MAINTENANCE, NodeState.ORCHESTRATOR_RESERVED_DESCRIPTION);
        final ClusterStateGenerator.Params params = fixture.generatorParams();

        // Both node 4 & 5 are in maintenance by Orchestrator, which will force the other nodes in the
        // group to maintenance (node 3).
        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:9 .3.s:d storage:9 .3.s:m .4.s:m .5.s:m"));
    }

    @Test
    void group_nodes_are_not_marked_maintenance_if_group_availability_high_by_orchestrator() {
        final ClusterFixture fixture = ClusterFixture
                .forHierarchicCluster(DistributionBuilder.withGroups(3).eachWithNodeCount(3))
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(4, State.MAINTENANCE, NodeState.ORCHESTRATOR_RESERVED_DESCRIPTION);
        final ClusterStateGenerator.Params params = fixture.generatorParams();

        // Node 4 is in maintenance by Orchestrator, which is not sufficient to force group into maintenance.
        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:9 storage:9 .4.s:m"));
    }

    /**
     * Cluster-wide distribution bit count cannot be higher than the lowest split bit
     * count reported by the set of storage nodes. This is because the distribution bit
     * directly impacts which level of the bucket tree is considered the root level,
     * and any buckets caught over this level would not be accessible in the data space.
     */
    @Test
    void distribution_bits_bounded_by_reported_min_bits_from_storage_node() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(1, new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(7));

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("bits:7 distributor:3 storage:3"));
    }

    @Test
    void distribution_bits_bounded_by_lowest_reporting_storage_node() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(6))
                .reportStorageNodeState(1, new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(5));

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("bits:5 distributor:3 storage:3"));
    }

    @Test
    void distribution_bits_bounded_by_config_parameter() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3).bringEntireClusterUp();

        final ClusterStateGenerator.Params params = fixture.generatorParams().idealDistributionBits(12);
        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("bits:12 distributor:3 storage:3"));
    }

    // TODO do we really want this behavior? It's the legacy one, but it seems... dangerous.. Especially for maintenance
    // TODO We generally want to avoid distribution bit decreases if at all possible, since "collapsing"
    // the top-level bucket space can cause data loss on timestamp collisions across super buckets.
    @Test
    void distribution_bit_not_influenced_by_nodes_down_or_in_maintenance() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(7))
                .reportStorageNodeState(1, new NodeState(NodeType.STORAGE, State.DOWN).setMinUsedBits(6))
                .reportStorageNodeState(2, new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(5))
                .proposeStorageNodeWantedState(2, State.MAINTENANCE);

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("bits:7 distributor:3 storage:3 .1.s:d .2.s:m"));
    }

    private String do_test_distribution_bit_watermark(int lowestObserved, int node0MinUsedBits) {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(node0MinUsedBits));

        final ClusterStateGenerator.Params params = fixture.generatorParams()
                .highestObservedDistributionBitCount(8) // TODO is this even needed for our current purposes?
                .lowestObservedDistributionBitCount(lowestObserved);

        return ClusterStateGenerator.generatedStateFrom(params).toString();
    }

    /**
     * Distribution bit increases should not take place incrementally. Doing so would
     * let e.g. a transition from 10 bits to 20 bits cause 10 interim full re-distributions.
     */
    @Test
    void published_distribution_bit_bound_by_low_watermark_when_nodes_report_less_than_config_bits() {
        assertThat(do_test_distribution_bit_watermark(5, 5),
                equalTo("bits:5 distributor:3 storage:3"));
        assertThat(do_test_distribution_bit_watermark(5, 6),
                equalTo("bits:5 distributor:3 storage:3"));
        assertThat(do_test_distribution_bit_watermark(5, 15),
                equalTo("bits:5 distributor:3 storage:3"));
    }

    @Test
    void published_state_jumps_to_configured_ideal_bits_when_all_nodes_report_it() {
        // Note: the rest of the mocked nodes always report 16 bits by default
        assertThat(do_test_distribution_bit_watermark(5, 16),
                equalTo("distributor:3 storage:3")); // "bits:16" implied
    }

    private String do_test_storage_node_with_no_init_progress(State wantedState) {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.5f))
                .proposeStorageNodeWantedState(0, wantedState);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 0));
        nodeInfo.setInitProgressTime(10_000);

        final ClusterStateGenerator.Params params = fixture.generatorParams()
                .maxInitProgressTime(1000)
                .currentTimeInMillis(11_000);
        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        return state.toString();
    }

    @Test
    void storage_node_with_no_init_progress_within_timeout_is_marked_down() {
        assertThat(do_test_storage_node_with_no_init_progress(State.UP),
                equalTo("distributor:3 storage:3 .0.s:d"));
    }

    /**
     * As per usual, we shouldn't transition implicitly to Down if Maintenance is set
     * as the wanted state.
     */
    @Test
    void maintenance_wanted_state_overrides_storage_node_with_no_init_progress() {
        assertThat(do_test_storage_node_with_no_init_progress(State.MAINTENANCE),
                equalTo("distributor:3 storage:3 .0.s:m"));
    }

    /**
     * Legacy behavior: if a node has crashed (i.e. transition into Down) at least once
     * while in Init mode, its subsequent init mode will not be made public.
     * This means the node will remain in a Down-state until it has finished
     * initializing. This is presumably because unstable nodes may not be able to finish
     * their init stage and would otherwise pop in and out of the cluster state.
     */
    @Test
    void unstable_init_storage_node_has_init_state_substituted_by_down() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, State.INITIALIZING)
                .reportStorageNodeState(0, State.DOWN) // Init -> Down triggers unstable init flag
                .reportStorageNodeState(0, new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.5f));

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .0.s:d"));
    }

    @Test
    void storage_node_with_crashes_but_not_unstable_init_does_not_have_init_state_substituted_by_down() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.5f));
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 0));
        nodeInfo.setPrematureCrashCount(5);

        final AnnotatedClusterState state = generateFromFixtureWithDefaultParams(fixture);
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .0.s:i .0.i:0.5"));
    }

    /**
     * The generated state must be considered over the Reported state when deciding whether
     * to override it with the Wanted state. Otherwise, an unstable retired node could have
     * its generated state be Retired instead of Down. We want it to stay down instead of
     * potentially contributing additional instability to the cluster.
     */
    @Test
    void unstable_retired_node_should_be_marked_down() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(5)
                .bringEntireClusterUp()
                .proposeStorageNodeWantedState(3, State.RETIRED);
        final ClusterStateGenerator.Params params = fixture.generatorParams().maxPrematureCrashes(10);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 3));
        nodeInfo.setPrematureCrashCount(11);

        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:5 storage:5 .3.s:d"));
    }

    @Test
    void generator_params_can_inherit_values_from_controller_options() {
        FleetControllerOptions options = new FleetControllerOptions.Builder("foocluster", Set.of(new ConfiguredNode(0, false)))
                .setMaxPrematureCrashes(1)
                .setMinStorageNodesUp(2)
                .setMinDistributorNodesUp(3)
                .setMinRatioOfStorageNodesUp(0.4)
                .setMinRatioOfDistributorNodesUp(0.5)
                .setMinNodeRatioPerGroup(0.6)
                .setDistributionBits(7)
                .setMaxTransitionTime(NodeType.DISTRIBUTOR, 1000)
                .setMaxTransitionTime(NodeType.STORAGE, 2000)
                .setZooKeeperServerAddress("localhost:2181")
                .build();

        final ClusterStateGenerator.Params params = ClusterStateGenerator.Params.fromOptions(options);
        assertThat(params.maxPrematureCrashes, equalTo(options.maxPrematureCrashes()));
        assertThat(params.minStorageNodesUp, equalTo(options.minStorageNodesUp()));
        assertThat(params.minDistributorNodesUp, equalTo(options.minDistributorNodesUp()));
        assertThat(params.minRatioOfStorageNodesUp, equalTo(options.minRatioOfStorageNodesUp()));
        assertThat(params.minRatioOfDistributorNodesUp, equalTo(options.minRatioOfDistributorNodesUp()));
        assertThat(params.minNodeRatioPerGroup, equalTo(options.minNodeRatioPerGroup()));
        assertThat(params.transitionTimes, equalTo(options.maxTransitionTime()));
    }

    @Test
    void configured_zero_init_progress_time_disables_auto_init_to_down_feature() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .bringEntireClusterUp()
                .reportStorageNodeState(0, new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.5f));

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 0));
        nodeInfo.setInitProgressTime(10_000);

        final ClusterStateGenerator.Params params = fixture.generatorParams()
                .maxInitProgressTime(0)
                .currentTimeInMillis(11_000);
        final AnnotatedClusterState state = ClusterStateGenerator.generatedStateFrom(params);
        assertThat(state.toString(), equalTo("distributor:3 storage:3 .0.s:i .0.i:0.5"));
    }

}
