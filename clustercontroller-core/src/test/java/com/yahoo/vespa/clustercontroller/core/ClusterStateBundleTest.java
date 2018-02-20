// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.*;
import org.junit.Test;

import java.text.ParseException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterStateBundleTest {

    private static ClusterState stateOf(String state) {
        try {
            return new ClusterState(state);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static ClusterStateBundle createTestBundle(boolean modifyDefaultSpace) {
        return ClusterStateBundle
                .builder(AnnotatedClusterState.withoutAnnotations(stateOf("distributor:2 storage:2")))
                .bucketSpaces("default", "global", "narnia")
                .stateDeriver((state, space) -> {
                    ClusterState derived = state.clone();
                    if (space.equals("default") && modifyDefaultSpace) {
                        derived.setNodeState(Node.ofStorage(0), new NodeState(NodeType.STORAGE, State.DOWN));
                    } else if (space.equals("narnia")) {
                        derived.setNodeState(Node.ofDistributor(0), new NodeState(NodeType.DISTRIBUTOR, State.DOWN));
                    }
                    return derived;
                })
                .deriveAndBuild();
    }

    private static ClusterStateBundle createTestBundle() {
        return createTestBundle(true);
    }

    @Test
    public void builder_creates_baseline_state_and_derived_state_per_space() {
        ClusterStateBundle bundle = createTestBundle();
        assertThat(bundle.getBaselineClusterState(), equalTo(stateOf("distributor:2 storage:2")));
        assertThat(bundle.getDerivedBucketSpaceStates().size(), equalTo(3));
        assertThat(bundle.getDerivedBucketSpaceStates().get("default"), equalTo(stateOf("distributor:2 storage:2 .0.s:d")));
        assertThat(bundle.getDerivedBucketSpaceStates().get("global"), equalTo(stateOf("distributor:2 storage:2")));
        assertThat(bundle.getDerivedBucketSpaceStates().get("narnia"), equalTo(stateOf("distributor:2 .0.s:d storage:2")));
    }

    @Test
    public void version_clone_sets_version_for_all_spaces() {
        ClusterStateBundle bundle = createTestBundle().clonedWithVersionSet(123);
        assertThat(bundle.getBaselineClusterState(), equalTo(stateOf("version:123 distributor:2 storage:2")));
        assertThat(bundle.getDerivedBucketSpaceStates().size(), equalTo(3));
        assertThat(bundle.getDerivedBucketSpaceStates().get("default"), equalTo(stateOf("version:123 distributor:2 storage:2 .0.s:d")));
        assertThat(bundle.getDerivedBucketSpaceStates().get("global"), equalTo(stateOf("version:123 distributor:2 storage:2")));
        assertThat(bundle.getDerivedBucketSpaceStates().get("narnia"), equalTo(stateOf("version:123 distributor:2 .0.s:d storage:2")));
    }

    @Test
    public void same_bundle_instance_considered_similar() {
        ClusterStateBundle bundle = createTestBundle();
        assertTrue(bundle.similarTo(bundle));
    }

    @Test
    public void similarity_test_considers_all_bucket_spaces() {
        ClusterStateBundle bundle = createTestBundle(false);
        ClusterStateBundle unchangedBundle = createTestBundle(false);

        assertTrue(bundle.similarTo(unchangedBundle));
        assertTrue(unchangedBundle.similarTo(bundle));

        ClusterStateBundle changedBundle = createTestBundle(true);
        assertFalse(bundle.similarTo(changedBundle));
        assertFalse(changedBundle.similarTo(bundle));
    }

    @Test
    public void toString_without_bucket_space_states_prints_only_baseline_state() {
        ClusterStateBundle bundle = ClusterStateBundle.ofBaselineOnly(
                AnnotatedClusterState.withoutAnnotations(stateOf("distributor:2 storage:2")));
        assertThat(bundle.toString(), equalTo("ClusterStateBundle('distributor:2 storage:2')"));
    }

    @Test
    public void toString_includes_all_bucket_space_states() {
        ClusterStateBundle bundle = createTestBundle();
        assertThat(bundle.toString(), equalTo("ClusterStateBundle('distributor:2 storage:2', " +
                "default 'distributor:2 storage:2 .0.s:d', " +
                "global 'distributor:2 storage:2', " +
                "narnia 'distributor:2 .0.s:d storage:2')"));
    }

}
