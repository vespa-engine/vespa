// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import org.junit.Test;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StateVersionTrackerTest {

    private static AnnotatedClusterState stateWithoutAnnotations(String stateStr) {
        final ClusterState state = ClusterStateUtil.stateFromString(stateStr);
        return new AnnotatedClusterState(state, null/*TODO*/, AnnotatedClusterState.emptyNodeStateReasons());
    }

    @Test
    public void version_is_incremented_when_new_state_is_applied() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        versionTracker.setVersionRetrievedFromZooKeeper(100);
        versionTracker.applyAndVersionNewState(stateWithoutAnnotations("distributor:2 storage:2"));
        assertThat(versionTracker.getCurrentVersion(), equalTo(101));
        assertThat(versionTracker.getVersionedClusterState().toString(), equalTo("version:101 distributor:2 storage:2"));
    }

    @Test
    public void version_is_1_upon_construction() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        assertThat(versionTracker.getCurrentVersion(), equalTo(1));
    }

    @Test
    public void set_current_version_caps_lowest_version_to_1() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        versionTracker.setVersionRetrievedFromZooKeeper(0);
        assertThat(versionTracker.getCurrentVersion(), equalTo(1));
    }

    // TODO or should it be initially true?
    @Test
    public void new_version_from_zk_predicate_initially_false() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        assertThat(versionTracker.hasReceivedNewVersionFromZooKeeper(), is(false));
    }

    @Test
    public void new_version_from_zk_predicate_true_after_setting_zk_version() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        versionTracker.setVersionRetrievedFromZooKeeper(5);
        assertThat(versionTracker.hasReceivedNewVersionFromZooKeeper(), is(true));
    }

    @Test
    public void new_version_from_zk_predicate_false_after_applying_higher_version() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        versionTracker.setVersionRetrievedFromZooKeeper(5);
        versionTracker.applyAndVersionNewState(stateWithoutAnnotations("distributor:2 storage:2"));
        assertThat(versionTracker.hasReceivedNewVersionFromZooKeeper(), is(false));
    }

    @Test
    public void exposed_states_are_empty_upon_construction() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        assertThat(versionTracker.getVersionedClusterState().toString(), equalTo(""));
        assertThat(versionTracker.getAnnotatedClusterState().getClusterState().toString(), equalTo(""));
    }

    @Test
    public void diff_from_initial_state_implies_changed_state() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        assertTrue(versionTracker.changedEnoughFromCurrentToWarrantBroadcast(stateWithoutAnnotations("cluster:d")));
    }

    private static boolean stateChangedBetween(String fromState, String toState) {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        versionTracker.applyAndVersionNewState(stateWithoutAnnotations(fromState));
        return versionTracker.changedEnoughFromCurrentToWarrantBroadcast(stateWithoutAnnotations(toState));
    }

    @Test
    public void version_mismatch_not_counted_as_changed_state() {
        assertFalse(stateChangedBetween("distributor:2 storage:2", "distributor:2 storage:2"));
    }

    @Test
    public void different_distributor_node_count_implies_changed_state() {
        assertTrue(stateChangedBetween("distributor:2 storage:2", "distributor:3 storage:2"));
        assertTrue(stateChangedBetween("distributor:3 storage:2", "distributor:2 storage:2"));
    }

    @Test
    public void different_storage_node_count_implies_changed_state() {
        assertTrue(stateChangedBetween("distributor:2 storage:2", "distributor:2 storage:3"));
        assertTrue(stateChangedBetween("distributor:2 storage:3", "distributor:2 storage:2"));
    }

    @Test
    public void different_distributor_node_state_implies_changed_state() {
        assertTrue(stateChangedBetween("distributor:2 storage:2", "distributor:2 .0.s:d storage:2"));
        assertTrue(stateChangedBetween("distributor:2 .0.s:d storage:2", "distributor:2 storage:2"));
    }

    @Test
    public void different_storage_node_state_implies_changed_state() {
        assertTrue(stateChangedBetween("distributor:2 storage:2", "distributor:2 storage:2 .0.s:d"));
        assertTrue(stateChangedBetween("distributor:2 storage:2 .0.s:d", "distributor:2 storage:2"));
    }

    @Test
    public void lowest_observed_distribution_bit_is_initially_16() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        assertThat(versionTracker.getLowestObservedDistributionBits(), equalTo(16));
    }

    @Test
    public void lowest_observed_distribution_bit_is_tracked_across_states() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        versionTracker.applyAndVersionNewState(stateWithoutAnnotations("bits:15 distributor:2 storage:2"));
        assertThat(versionTracker.getLowestObservedDistributionBits(), equalTo(15));
        versionTracker.applyAndVersionNewState(stateWithoutAnnotations("bits:17 distributor:2 storage:2"));
        assertThat(versionTracker.getLowestObservedDistributionBits(), equalTo(15));
        versionTracker.applyAndVersionNewState(stateWithoutAnnotations("bits:14 distributor:2 storage:2"));
        assertThat(versionTracker.getLowestObservedDistributionBits(), equalTo(14));
    }

    // For similarity purposes, only the cluster-wide bits matter, not the individual node state
    // min used bits. The former is derived from the latter, but the latter is not visible in the
    // published state (but _is_ visible in the internal ClusterState structures).
    @Test
    public void per_node_min_bits_changes_are_not_considered_different() {
        final StateVersionTracker versionTracker = new StateVersionTracker();
        final AnnotatedClusterState stateWithMinBits = stateWithoutAnnotations("distributor:2 storage:2");
        stateWithMinBits.getClusterState().setNodeState(
                new Node(NodeType.STORAGE, 0),
                new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(15));
        versionTracker.applyAndVersionNewState(stateWithMinBits);
        assertFalse(versionTracker.changedEnoughFromCurrentToWarrantBroadcast(
                stateWithoutAnnotations("distributor:2 storage:2")));
    }

}
