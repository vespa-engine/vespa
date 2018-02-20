// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNodeStatsBridge;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author hakonhall
 * @since 5.34
 */
public class ClusterStateViewTest {
    final Map<Integer, String> hostnames = new HashMap<>();
    final NodeInfo nodeInfo = mock(NodeInfo.class);
    final Node node = mock(Node.class);
    final ClusterStatsAggregator statsAggregator = mock(ClusterStatsAggregator.class);
    final ClusterState clusterState = mock(ClusterState.class);
    final ClusterStateView clusterStateView = new ClusterStateView(clusterState, statsAggregator);

    HostInfo createHostInfo(String version) {
        return HostInfo.createHostInfo("{ \"cluster-state-version\": " + version + " }");
    }

    @Test
    public void testWrongNodeType() {
        when(nodeInfo.isDistributor()).thenReturn(false);

        clusterStateView.handleUpdatedHostInfo(hostnames, nodeInfo, createHostInfo("101"));

        verify(statsAggregator, never()).updateForDistributor(anyInt(), any());
    }



    @Test
    public void testStateVersionMismatch() {
        when(nodeInfo.isDistributor()).thenReturn(true);
        when(clusterState.getVersion()).thenReturn(101);

        clusterStateView.handleUpdatedHostInfo(hostnames, nodeInfo, createHostInfo("22"));

        verify(statsAggregator, never()).updateForDistributor(anyInt(), any());
    }

    @Test
    public void testFailToGetStats() {
        when(nodeInfo.isDistributor()).thenReturn(true);
        when(clusterState.getVersion()).thenReturn(101);

        clusterStateView.handleUpdatedHostInfo(hostnames, nodeInfo, createHostInfo("22"));

        verify(statsAggregator, never()).updateForDistributor(anyInt(), any());
    }

    @Test
    public void testSuccessCase() {
        when(nodeInfo.isDistributor()).thenReturn(true);
        HostInfo hostInfo = HostInfo.createHostInfo(
                "{" +
                " \"cluster-state-version\": 101," +
                " \"distributor\": {\n" +
                "        \"storage-nodes\": [\n" +
                "            {\n" +
                "                \"node-index\": 3\n" +
                "            }\n" +
                "           ]}}");


        when(nodeInfo.getNodeIndex()).thenReturn(3);
        when(clusterState.getVersion()).thenReturn(101);

        clusterStateView.handleUpdatedHostInfo(hostnames, nodeInfo, hostInfo);

        verify(statsAggregator).updateForDistributor(3, StorageNodeStatsBridge.generate(hostInfo.getDistributor()));
    }

    @Test
    public void testIndicesOfUpNodes() {
        when(clusterState.getNodeCount(NodeType.DISTRIBUTOR)).thenReturn(7);

        NodeState nodeState = mock(NodeState.class);
        when(nodeState.getState()).
                thenReturn(State.MAINTENANCE).  // 0
                thenReturn(State.RETIRED).  // 1
                thenReturn(State.INITIALIZING).  // 2
                thenReturn(State.DOWN).
                thenReturn(State.STOPPING).
                thenReturn(State.UNKNOWN).
                thenReturn(State.UP);  // 6

        when(clusterState.getNodeState(any())).thenReturn(nodeState);

        Set<Integer> indices = ClusterStateView.getIndicesOfUpNodes(clusterState, NodeType.DISTRIBUTOR);
        assertEquals(4, indices.size());
        assert(indices.contains(0));
        assert(indices.contains(1));
        assert(indices.contains(2));
        assert(indices.contains(6));
    }
}
