// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.*;
import org.junit.Test;

import java.util.function.Function;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterStateBundleTest {

    private static ClusterState stateOf(String state) {
        return ClusterState.stateFromString(state);
    }

    private static AnnotatedClusterState annotatedStateOf(String state) {
        return AnnotatedClusterState.withoutAnnotations(stateOf(state));
    }

    private static ClusterStateBundle.Builder createTestBundleBuilder(boolean modifyDefaultSpace) {
        return ClusterStateBundle
                .builder(annotatedStateOf("distributor:2 storage:2"))
                .bucketSpaces("default", "global", "narnia")
                .stateDeriver((state, space) -> {
                    AnnotatedClusterState derived = state.clone();
                    if (space.equals("default") && modifyDefaultSpace) {
                        derived.getClusterState()
                                .setNodeState(Node.ofStorage(0), new NodeState(NodeType.STORAGE, State.DOWN));
                    } else if (space.equals("narnia")) {
                        derived.getClusterState()
                                .setNodeState(Node.ofDistributor(0), new NodeState(NodeType.DISTRIBUTOR, State.DOWN));
                    }
                    return derived;
                });
    }

    private static ClusterStateBundle createTestBundle(boolean modifyDefaultSpace) {
        return createTestBundleBuilder(modifyDefaultSpace).deriveAndBuild();
    }

    private static ClusterStateBundle createTestBundle() {
        return createTestBundle(true);
    }

    @Test
    public void builder_creates_baseline_state_and_derived_state_per_space() {
        ClusterStateBundle bundle = createTestBundle();
        assertThat(bundle.getBaselineClusterState(), equalTo(stateOf("distributor:2 storage:2")));
        assertThat(bundle.getDerivedBucketSpaceStates().size(), equalTo(3));
        assertThat(bundle.getDerivedBucketSpaceStates().get("default"), equalTo(annotatedStateOf("distributor:2 storage:2 .0.s:d")));
        assertThat(bundle.getDerivedBucketSpaceStates().get("global"), equalTo(annotatedStateOf("distributor:2 storage:2")));
        assertThat(bundle.getDerivedBucketSpaceStates().get("narnia"), equalTo(annotatedStateOf("distributor:2 .0.s:d storage:2")));
    }

    @Test
    public void version_clone_sets_version_for_all_spaces() {
        ClusterStateBundle bundle = createTestBundle().clonedWithVersionSet(123);
        assertThat(bundle.getBaselineClusterState(), equalTo(stateOf("version:123 distributor:2 storage:2")));
        assertThat(bundle.getDerivedBucketSpaceStates().size(), equalTo(3));
        assertThat(bundle.getDerivedBucketSpaceStates().get("default"), equalTo(annotatedStateOf("version:123 distributor:2 storage:2 .0.s:d")));
        assertThat(bundle.getDerivedBucketSpaceStates().get("global"), equalTo(annotatedStateOf("version:123 distributor:2 storage:2")));
        assertThat(bundle.getDerivedBucketSpaceStates().get("narnia"), equalTo(annotatedStateOf("version:123 distributor:2 .0.s:d storage:2")));
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
                annotatedStateOf("distributor:2 storage:2"));
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

    @Test
    public void toString_without_derived_states_specifies_deferred_activation_iff_set() {
        var bundle = ClusterStateBundle.ofBaselineOnly(annotatedStateOf("distributor:2 storage:2"), true);
        assertThat(bundle.toString(), equalTo("ClusterStateBundle('distributor:2 storage:2' (deferred activation))"));
    }

    @Test
    public void toString_without_derived_states_does_not_specify_deferred_activation_iff_not_set() {
        var bundle = ClusterStateBundle.ofBaselineOnly(annotatedStateOf("distributor:2 storage:2"), false);
        assertThat(bundle.toString(), equalTo("ClusterStateBundle('distributor:2 storage:2')"));
    }

    @Test
    public void toString_with_derived_states_specifies_deferred_activation_iff_set() {
        var bundle = createTestBundleBuilder(true).deferredActivation(true).deriveAndBuild();
        assertThat(bundle.toString(), equalTo("ClusterStateBundle('distributor:2 storage:2', " +
                "default 'distributor:2 storage:2 .0.s:d', " +
                "global 'distributor:2 storage:2', " +
                "narnia 'distributor:2 .0.s:d storage:2' (deferred activation))"));
    }

    @Test
    public void toString_with_derived_states_does_not_specify_deferred_activation_iff_not_set() {
        var bundle = createTestBundleBuilder(true).deferredActivation(false).deriveAndBuild();
        assertThat(bundle.toString(), equalTo("ClusterStateBundle('distributor:2 storage:2', " +
                "default 'distributor:2 storage:2 .0.s:d', " +
                "global 'distributor:2 storage:2', " +
                "narnia 'distributor:2 .0.s:d storage:2')"));
    }

    @Test
    public void deferred_activation_is_disabled_by_default() {
        ClusterStateBundle bundle = createTestBundle();
        assertFalse(bundle.deferredActivation());
    }

    @Test
    public void can_build_bundle_with_deferred_activation_enabled() {
        var bundle = createTestBundleBuilder(false).deferredActivation(true).deriveAndBuild();
        assertTrue(bundle.deferredActivation());
    }

    @Test
    public void can_build_bundle_with_deferred_activation_disabled() {
        var bundle = createTestBundleBuilder(false).deferredActivation(false).deriveAndBuild();
        assertFalse(bundle.deferredActivation());
    }

    @Test
    public void simple_bundle_without_derived_states_propagates_deferred_activation_flag() {
        var bundle = ClusterStateBundle
                .builder(annotatedStateOf("distributor:2 storage:2"))
                .deferredActivation(false) // defaults to true
                .deriveAndBuild();
        assertFalse(bundle.deferredActivation());
    }

    @Test
    public void cloning_preserves_false_deferred_activation_flag() {
        var bundle = createTestBundleBuilder(true).deferredActivation(false).deriveAndBuild();
        var derived = bundle.cloneWithMapper(Function.identity());
        assertEquals(bundle, derived);
    }

    @Test
    public void cloning_preserves_true_deferred_activation_flag() {
        var bundle = createTestBundleBuilder(true).deferredActivation(true).deriveAndBuild();
        var derived = bundle.cloneWithMapper(Function.identity());
        assertEquals(bundle, derived);
    }

}
