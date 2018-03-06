// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.vdslib.state.ClusterState;
import org.junit.Test;

import java.util.*;

import static com.yahoo.vespa.clustercontroller.core.NodeStateReason.MAY_HAVE_MERGES_PENDING;
import static com.yahoo.vespa.clustercontroller.core.NodeStateReason.NODE_TOO_UNSTABLE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MaintenanceWhenPendingGlobalMergesTest {

    private static class Fixture {
        public MergePendingChecker mockPendingChecker = mock(MergePendingChecker.class);
        public MaintenanceWhenPendingGlobalMerges deriver = new MaintenanceWhenPendingGlobalMerges(mockPendingChecker);
    }

    private static String defaultSpace() {
        return FixedBucketSpaces.defaultSpace();
    }

    private static String globalSpace() {
        return FixedBucketSpaces.globalSpace();
    }

    private static AnnotatedClusterState stateFromString(String stateStr) {
        return AnnotatedClusterState.withoutAnnotations(ClusterState.stateFromString(stateStr));
    }

    private static class AnnotatedClusterStateBuilder extends AnnotatedClusterState.Builder {

        public static AnnotatedClusterStateBuilder ofState(String stateStr) {
            return (AnnotatedClusterStateBuilder) new AnnotatedClusterStateBuilder().clusterState(stateStr);
        }

        public AnnotatedClusterStateBuilder reason(NodeStateReason reason, Integer... nodeIndices) {
            Arrays.stream(nodeIndices).forEach(nodeIndex -> storageNodeReason(nodeIndex, reason));
            return this;
        }
    }

    @Test
    public void no_nodes_set_to_maintenance_in_global_bucket_space_state() {
        Fixture f = new Fixture();
        when(f.mockPendingChecker.mayHaveMergesPending(eq(globalSpace()), anyInt())).thenReturn(true); // False returned by default otherwise
        AnnotatedClusterState derived = f.deriver.derivedFrom(stateFromString("distributor:2 storage:2"), globalSpace());
        assertThat(derived, equalTo(stateFromString("distributor:2 storage:2")));
    }

    @Test
    public void content_nodes_with_global_merge_pending_set_to_maintenance_in_default_space_state() {
        Fixture f = new Fixture();
        when(f.mockPendingChecker.mayHaveMergesPending(globalSpace(), 1)).thenReturn(true);
        when(f.mockPendingChecker.mayHaveMergesPending(globalSpace(), 3)).thenReturn(true);
        AnnotatedClusterState derived = f.deriver.derivedFrom(stateFromString("distributor:5 storage:5"), defaultSpace());
        assertThat(derived, equalTo(AnnotatedClusterStateBuilder.ofState("distributor:5 storage:5 .1.s:m .3.s:m")
                .reason(MAY_HAVE_MERGES_PENDING, 1, 3).build()));
    }

    @Test
    public void no_nodes_set_to_maintenance_when_no_merges_pending() {
        Fixture f = new Fixture();
        AnnotatedClusterState derived = f.deriver.derivedFrom(stateFromString("distributor:5 storage:5"), defaultSpace());
        assertThat(derived, equalTo(stateFromString("distributor:5 storage:5")));
    }

    @Test
    public void default_space_merges_do_not_count_towards_maintenance() {
        Fixture f = new Fixture();
        when(f.mockPendingChecker.mayHaveMergesPending(eq(defaultSpace()), anyInt())).thenReturn(true);
        AnnotatedClusterState derived = f.deriver.derivedFrom(stateFromString("distributor:2 storage:2"), defaultSpace());
        assertThat(derived, equalTo(stateFromString("distributor:2 storage:2")));
    }

    @Test
    public void nodes_only_set_to_maintenance_when_marked_up_init_or_retiring() {
        Fixture f = new Fixture();
        when(f.mockPendingChecker.mayHaveMergesPending(eq(globalSpace()), anyInt())).thenReturn(true);
        AnnotatedClusterState derived = f.deriver.derivedFrom(stateFromString("distributor:5 storage:5 .1.s:m .2.s:r .3.s:i .4.s:d"), defaultSpace());
        // TODO reconsider role of retired here... It should not have merges pending towards it in the general case, but may be out of sync
        assertThat(derived, equalTo(AnnotatedClusterStateBuilder.ofState("distributor:5 storage:5 .0.s:m .1.s:m .2.s:m .3.s:m .4.s:d")
                .reason(MAY_HAVE_MERGES_PENDING, 0, 2, 3).build()));
    }

    @Test
    public void node_state_reasons_are_used_as_baseline_in_default_bucket_space_state() {
        Fixture f = new Fixture();
        when(f.mockPendingChecker.mayHaveMergesPending(globalSpace(), 1)).thenReturn(true);
        when(f.mockPendingChecker.mayHaveMergesPending(globalSpace(), 3)).thenReturn(true);
        AnnotatedClusterState derived = f.deriver.derivedFrom(AnnotatedClusterStateBuilder.ofState("distributor:5 storage:5")
                .reason(NODE_TOO_UNSTABLE, 1, 2).build(), defaultSpace());
        assertThat(derived, equalTo(AnnotatedClusterStateBuilder.ofState("distributor:5 storage:5 .1.s:m .3.s:m")
                .reason(MAY_HAVE_MERGES_PENDING, 1, 3)
                .reason(NODE_TOO_UNSTABLE, 2).build()));
    }

}
