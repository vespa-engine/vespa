// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import org.junit.Test;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StateVersionTrackerTest {

    private static AnnotatedClusterState stateWithoutAnnotations(String stateStr) {
        final ClusterState state = ClusterState.stateFromString(stateStr);
        return new AnnotatedClusterState(state, Optional.empty(), AnnotatedClusterState.emptyNodeStateReasons());
    }

    private static ClusterStateBundle stateBundleWithoutAnnotations(String stateStr) {
        return ClusterStateBundle.ofBaselineOnly(stateWithoutAnnotations(stateStr));
    }

    private static StateVersionTracker createWithMockedMetrics() {
        return new StateVersionTracker();
    }

    private static void updateAndPromote(final StateVersionTracker versionTracker,
                                         final AnnotatedClusterState state,
                                         final long timeMs)
    {
        versionTracker.updateLatestCandidateStateBundle(ClusterStateBundle.ofBaselineOnly(state));
        versionTracker.promoteCandidateToVersionedState(timeMs);
    }

    @Test
    public void version_is_incremented_when_new_state_is_applied() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        versionTracker.setVersionRetrievedFromZooKeeper(100);
        updateAndPromote(versionTracker, stateWithoutAnnotations("distributor:2 storage:2"), 123);
        assertThat(versionTracker.getCurrentVersion(), equalTo(101));
        assertThat(versionTracker.getVersionedClusterState().toString(), equalTo("version:101 distributor:2 storage:2"));
    }

    @Test
    public void version_is_1_upon_construction() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        assertThat(versionTracker.getCurrentVersion(), equalTo(1));
    }

    @Test
    public void set_current_version_caps_lowest_version_to_1() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        versionTracker.setVersionRetrievedFromZooKeeper(0);
        assertThat(versionTracker.getCurrentVersion(), equalTo(1));
    }

    @Test
    public void new_version_from_zk_predicate_initially_false() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        assertThat(versionTracker.hasReceivedNewVersionFromZooKeeper(), is(false));
    }

    @Test
    public void new_version_from_zk_predicate_true_after_setting_zk_version() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        versionTracker.setVersionRetrievedFromZooKeeper(5);
        assertThat(versionTracker.hasReceivedNewVersionFromZooKeeper(), is(true));
    }

    @Test
    public void new_version_from_zk_predicate_false_after_applying_higher_version() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        versionTracker.setVersionRetrievedFromZooKeeper(5);
        updateAndPromote(versionTracker, stateWithoutAnnotations("distributor:2 storage:2"), 123);
        assertThat(versionTracker.hasReceivedNewVersionFromZooKeeper(), is(false));
    }

    @Test
    public void exposed_states_are_empty_upon_construction() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        assertThat(versionTracker.getVersionedClusterState().toString(), equalTo(""));
        assertThat(versionTracker.getAnnotatedVersionedClusterState().getClusterState().toString(), equalTo(""));
    }

    @Test
    public void diff_from_initial_state_implies_changed_state() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        versionTracker.updateLatestCandidateStateBundle(stateBundleWithoutAnnotations("cluster:d"));
        assertTrue(versionTracker.candidateChangedEnoughFromCurrentToWarrantPublish());
    }

    private static boolean stateChangedBetween(String fromState, String toState) {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        updateAndPromote(versionTracker, stateWithoutAnnotations(fromState), 123);
        versionTracker.updateLatestCandidateStateBundle(stateBundleWithoutAnnotations(toState));
        return versionTracker.candidateChangedEnoughFromCurrentToWarrantPublish();
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
    public void init_progress_change_not_counted_as_changed_state() {
        assertFalse(stateChangedBetween("distributor:2 storage:2 .0.s:i .0.i:0.5",
                                        "distributor:2 storage:2 .0.s:i .0.i:0.6"));
    }

    @Test
    public void lowest_observed_distribution_bit_is_initially_16() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        assertThat(versionTracker.getLowestObservedDistributionBits(), equalTo(16));
    }

    @Test
    public void lowest_observed_distribution_bit_is_tracked_across_states() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        updateAndPromote(versionTracker, stateWithoutAnnotations("bits:15 distributor:2 storage:2"), 100);
        assertThat(versionTracker.getLowestObservedDistributionBits(), equalTo(15));

        updateAndPromote(versionTracker, stateWithoutAnnotations("bits:17 distributor:2 storage:2"), 200);
        assertThat(versionTracker.getLowestObservedDistributionBits(), equalTo(15));

        updateAndPromote(versionTracker, stateWithoutAnnotations("bits:14 distributor:2 storage:2"), 300);
        assertThat(versionTracker.getLowestObservedDistributionBits(), equalTo(14));
    }

    // For similarity purposes, only the cluster-wide bits matter, not the individual node state
    // min used bits. The former is derived from the latter, but the latter is not visible in the
    // published state (but _is_ visible in the internal ClusterState structures).
    @Test
    public void per_node_min_bits_changes_are_not_considered_different() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        final AnnotatedClusterState stateWithMinBits = stateWithoutAnnotations("distributor:2 storage:2");
        stateWithMinBits.getClusterState().setNodeState(
                new Node(NodeType.STORAGE, 0),
                new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(15));
        updateAndPromote(versionTracker, stateWithMinBits, 123);
        versionTracker.updateLatestCandidateStateBundle(stateBundleWithoutAnnotations("distributor:2 storage:2"));
        assertFalse(versionTracker.candidateChangedEnoughFromCurrentToWarrantPublish());
    }

    @Test
    public void state_history_is_initially_empty() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        assertTrue(versionTracker.getClusterStateHistory().isEmpty());
    }

    private static ClusterStateHistoryEntry historyEntry(final String state, final long time) {
        return new ClusterStateHistoryEntry(ClusterState.stateFromString(state), time);
    }

    @Test
    public void applying_state_adds_to_cluster_state_history() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        updateAndPromote(versionTracker, stateWithoutAnnotations("distributor:2 storage:2") ,100);
        updateAndPromote(versionTracker, stateWithoutAnnotations("distributor:3 storage:3"), 200);
        updateAndPromote(versionTracker, stateWithoutAnnotations("distributor:4 storage:4"), 300);

        // Note: newest entry first
        assertThat(versionTracker.getClusterStateHistory(),
                equalTo(Arrays.asList(
                         historyEntry("version:4 distributor:4 storage:4", 300),
                         historyEntry("version:3 distributor:3 storage:3", 200),
                         historyEntry("version:2 distributor:2 storage:2", 100))));
    }

    @Test
    public void old_states_pruned_when_state_history_limit_reached() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        versionTracker.setMaxHistoryEntryCount(2);

        updateAndPromote(versionTracker, stateWithoutAnnotations("distributor:2 storage:2") ,100);
        updateAndPromote(versionTracker, stateWithoutAnnotations("distributor:3 storage:3"), 200);
        updateAndPromote(versionTracker, stateWithoutAnnotations("distributor:4 storage:4"), 300);

        assertThat(versionTracker.getClusterStateHistory(),
                equalTo(Arrays.asList(
                        historyEntry("version:4 distributor:4 storage:4", 300),
                        historyEntry("version:3 distributor:3 storage:3", 200))));

        updateAndPromote(versionTracker, stateWithoutAnnotations("distributor:5 storage:5"), 400);

        assertThat(versionTracker.getClusterStateHistory(),
                equalTo(Arrays.asList(
                        historyEntry("version:5 distributor:5 storage:5", 400),
                        historyEntry("version:4 distributor:4 storage:4", 300))));
    }

    @Test
    public void can_get_latest_non_published_candidate_state() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();

        AnnotatedClusterState candidate = stateWithoutAnnotations("distributor:2 storage:2");
        versionTracker.updateLatestCandidateStateBundle(ClusterStateBundle.ofBaselineOnly(candidate));
        assertThat(versionTracker.getLatestCandidateState(), equalTo(candidate));

        candidate = stateWithoutAnnotations("distributor:3 storage:3");
        versionTracker.updateLatestCandidateStateBundle(ClusterStateBundle.ofBaselineOnly(candidate));
        assertThat(versionTracker.getLatestCandidateState(), equalTo(candidate));
    }

    private static ClusterState stateOf(String state) {
        return ClusterState.stateFromString(state);
    }

    private static ClusterStateBundle baselineBundle(boolean alteredDefaultState) {
        return ClusterStateBundle
                .builder(AnnotatedClusterState.withoutAnnotations(stateOf("distributor:1 storage:1")))
                .bucketSpaces("default")
                .stateDeriver((state, space) -> {
                    ClusterState derived = state.clone();
                    if (alteredDefaultState) {
                        derived.setNodeState(Node.ofStorage(0), new NodeState(NodeType.STORAGE, State.DOWN));
                    }
                    return derived;
                })
                .deriveAndBuild();
    }

    @Test
    public void version_change_check_takes_derived_states_into_account() {
        final StateVersionTracker versionTracker = createWithMockedMetrics();
        versionTracker.updateLatestCandidateStateBundle(baselineBundle(false));
        versionTracker.promoteCandidateToVersionedState(1234);

        // Not marked changed with no changes across bucket spaces
        versionTracker.updateLatestCandidateStateBundle(baselineBundle(false));
        assertFalse(versionTracker.candidateChangedEnoughFromCurrentToWarrantPublish());

        // Changing state in default space marks as sufficiently changed
        versionTracker.updateLatestCandidateStateBundle(baselineBundle(true));
        assertTrue(versionTracker.candidateChangedEnoughFromCurrentToWarrantPublish());
    }

    @Test
    public void buckets_pending_state_is_tracked_between_cluster_states() {
        final StateVersionTracker tracker = createWithMockedMetrics();
        final NodeInfo distributorNode = mock(DistributorNodeInfo.class);
        when(distributorNode.isDistributor()).thenReturn(true);
        assertFalse(tracker.bucketSpaceMergeCompletionStateHasChanged());

        tracker.updateLatestCandidateStateBundle(ClusterStateBundle
                .ofBaselineOnly(stateWithoutAnnotations("distributor:1 storage:1")));
        tracker.promoteCandidateToVersionedState(1234);
        assertFalse(tracker.bucketSpaceMergeCompletionStateHasChanged());

        // Give 'global' bucket space no buckets pending, which is the same as previous stats
        tracker.handleUpdatedHostInfo(distributorNode, createHostInfo(0));
        assertFalse(tracker.bucketSpaceMergeCompletionStateHasChanged());

        // Give 'global' bucket space buckets pending, which is different from previous stats
        tracker.handleUpdatedHostInfo(distributorNode, createHostInfo(1));
        assertTrue(tracker.bucketSpaceMergeCompletionStateHasChanged());

        tracker.updateLatestCandidateStateBundle(ClusterStateBundle
                .ofBaselineOnly(stateWithoutAnnotations("distributor:1 storage:1")));
        assertFalse(tracker.bucketSpaceMergeCompletionStateHasChanged());
    }

    private HostInfo createHostInfo(long bucketsPending) {
        return HostInfo.createHostInfo(
                "{\n" +
                "\"cluster-state-version\": 2,\n" +
                "\"distributor\": {\n" +
                "  \"storage-nodes\": [\n" +
                "    {\n" +
                "      \"node-index\": 0,\n" +
                "      \"bucket-spaces\": [\n" +
                "        {\n" +
                "          \"name\": \"global\"\n," +
                "          \"buckets\": {\n" +
                "            \"total\": 5,\n" +
                "            \"pending\": " + bucketsPending + "\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "}");
    }

}
