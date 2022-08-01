// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NodeInfoTest {

    @Test
    void unstable_init_flag_is_initially_clear() {
        ClusterFixture fixture = ClusterFixture.forFlatCluster(3);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        assertFalse(nodeInfo.recentlyObservedUnstableDuringInit());
    }

    private static ClusterFixture fixtureWithNodeMarkedAsUnstableInit(int nodeIndex) {
        return ClusterFixture.forFlatCluster(3)
                .reportStorageNodeState(nodeIndex, State.INITIALIZING)
                .reportStorageNodeState(nodeIndex, State.DOWN);
    }

    @Test
    void down_edge_during_init_state_marks_as_unstable_init() {
        ClusterFixture fixture = fixtureWithNodeMarkedAsUnstableInit(1);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        assertTrue(nodeInfo.recentlyObservedUnstableDuringInit());
    }

    @Test
    void stopping_edge_during_init_does_not_mark_as_unstable_init() {
        ClusterFixture fixture = ClusterFixture.forFlatCluster(3).reportStorageNodeState(0, State.INITIALIZING);
        fixture.reportStorageNodeState(0, State.STOPPING);
        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 0));

        assertFalse(nodeInfo.recentlyObservedUnstableDuringInit());
    }

    /**
     * The cluster controller will, after a time of observed stable state, reset the crash
     * counter for a given node. This should also reset the unstable init flag to keep it
     * from haunting a now stable node.
     */
    @Test
    void zeroing_crash_count_resets_unstable_init_flag() {
        ClusterFixture fixture = fixtureWithNodeMarkedAsUnstableInit(1);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        nodeInfo.setPrematureCrashCount(0);
        assertFalse(nodeInfo.recentlyObservedUnstableDuringInit());
    }

    /**
     * A non-zero crash count update, on the other hand, implies the node is suffering
     * further instabilities and should not clear the unstable init flag.
     */
    @Test
    void non_zero_crash_count_update_does_not_reset_unstable_init_flag() {
        ClusterFixture fixture = fixtureWithNodeMarkedAsUnstableInit(1);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        nodeInfo.setPrematureCrashCount(3);
        assertTrue(nodeInfo.recentlyObservedUnstableDuringInit());
    }

    @Test
    void non_zero_crash_count_does_not_implicitly_set_unstable_init_flag() {
        ClusterFixture fixture = ClusterFixture.forFlatCluster(3);

        final NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        nodeInfo.setPrematureCrashCount(1);
        assertFalse(nodeInfo.recentlyObservedUnstableDuringInit());
    }

    @Test
    void down_wanted_state_overrides_config_retired_state() {
        ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .markNodeAsConfigRetired(1)
                .proposeStorageNodeWantedState(1, State.DOWN);

        NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        assertEquals(State.DOWN, nodeInfo.getWantedState().getState());
    }

    @Test
    void maintenance_wanted_state_overrides_config_retired_state() {
        ClusterFixture fixture = ClusterFixture.forFlatCluster(3)
                .markNodeAsConfigRetired(1)
                .proposeStorageNodeWantedState(1, State.MAINTENANCE);

        NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        assertEquals(State.MAINTENANCE, nodeInfo.getWantedState().getState());
    }

    @Test
    void retired_state_overrides_default_up_wanted_state() {
        final ClusterFixture fixture = ClusterFixture.forFlatCluster(3).markNodeAsConfigRetired(1);

        NodeInfo nodeInfo = fixture.cluster.getNodeInfo(new Node(NodeType.STORAGE, 1));
        assertEquals(State.RETIRED, nodeInfo.getWantedState().getState());
    }

}
