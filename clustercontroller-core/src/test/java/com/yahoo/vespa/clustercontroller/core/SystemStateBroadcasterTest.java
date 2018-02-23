// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeAddedOrRemovedListener;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;
import org.junit.Test;

import java.util.stream.Stream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SystemStateBroadcasterTest {

    private static class Fixture {
        FakeTimer timer = new FakeTimer();
        final Object monitor = new Object();
        SystemStateBroadcaster broadcaster = new SystemStateBroadcaster(timer, monitor);
        Communicator mockCommunicator = mock(Communicator.class);

        void simulateNodePartitionedAwaySilently(ClusterFixture cf) {
            cf.cluster().getNodeInfo(Node.ofStorage(0)).setStartTimestamp(600);
            cf.cluster().getNodeInfo(Node.ofStorage(1)).setStartTimestamp(700);
            // Simulate a distributor being partitioned away from the controller without actually going down. It will
            // need to observe all startup timestamps to infer if it should fetch bucket info from nodes.
            cf.cluster().getNodeInfo(Node.ofDistributor(0)).setStartTimestamp(500); // FIXME multiple sources of timestamps are... rather confusing
            cf.cluster().getNodeInfo(Node.ofDistributor(0)).setReportedState(new NodeState(NodeType.DISTRIBUTOR, State.UP).setStartTimestamp(500), 1000);
            cf.cluster().getNodeInfo(Node.ofDistributor(0)).setReportedState(new NodeState(NodeType.DISTRIBUTOR, State.DOWN).setStartTimestamp(500), 2000);
            cf.cluster().getNodeInfo(Node.ofDistributor(0)).setReportedState(new NodeState(NodeType.DISTRIBUTOR, State.UP).setStartTimestamp(500), 3000);
        }
    }

    private static DatabaseHandler.Context dbContextFrom(ContentCluster cluster) {
        return new DatabaseHandler.Context() {
            @Override
            public ContentCluster getCluster() {
                return cluster;
            }

            @Override
            public FleetController getFleetController() {
                return null; // We assume the broadcaster doesn't use this for our test purposes
            }

            @Override
            public NodeAddedOrRemovedListener getNodeAddedOrRemovedListener() {
                return null;
            }

            @Override
            public NodeStateOrHostInfoChangeHandler getNodeStateUpdateListener() {
                return null;
            }
        };
    }

    private static Stream<NodeInfo> clusterNodeInfos(ContentCluster c, Node... nodes) {
        return Stream.of(nodes).map(c::getNodeInfo);
    }

    @Test
    public void always_publish_baseline_cluster_state() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2");
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewState(dbContextFrom(cf.cluster()), f.mockCommunicator);
        cf.cluster().getNodeInfo().forEach(nodeInfo -> verify(f.mockCommunicator).setSystemState(eq(stateBundle), eq(nodeInfo), any()));
    }

    @Test
    public void non_observed_startup_timestamps_are_published_per_node_for_baseline_state() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2");
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.simulateNodePartitionedAwaySilently(cf);
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewState(dbContextFrom(cf.cluster()), f.mockCommunicator);

        clusterNodeInfos(cf.cluster(), Node.ofDistributor(1), Node.ofStorage(0), Node.ofStorage(1)).forEach(nodeInfo -> {
            // Only distributor 0 should observe startup timestamps
            verify(f.mockCommunicator).setSystemState(eq(stateBundle), eq(nodeInfo), any());
        });
        ClusterStateBundle expectedDistr0Bundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2 .0.t:600 .1.t:700");
        verify(f.mockCommunicator).setSystemState(eq(expectedDistr0Bundle), eq(cf.cluster().getNodeInfo(Node.ofDistributor(0))), any());
    }

    @Test
    public void bucket_space_states_are_published_verbatim_when_no_additional_timestamps_needed() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2",
                StateMapping.of("default", "distributor:2 storage:2 .0.s:d"),
                StateMapping.of("upsidedown", "distributor:2 .0.s:d storage:2"));
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewState(dbContextFrom(cf.cluster()), f.mockCommunicator);

        cf.cluster().getNodeInfo().forEach(nodeInfo -> verify(f.mockCommunicator).setSystemState(eq(stateBundle), eq(nodeInfo), any()));
    }

    @Test
    public void non_observed_startup_timestamps_are_published_per_bucket_space_state() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2",
                StateMapping.of("default", "distributor:2 storage:2 .0.s:d"),
                StateMapping.of("upsidedown", "distributor:2 .0.s:d storage:2"));
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.simulateNodePartitionedAwaySilently(cf);
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewState(dbContextFrom(cf.cluster()), f.mockCommunicator);

        clusterNodeInfos(cf.cluster(), Node.ofDistributor(1), Node.ofStorage(0), Node.ofStorage(1)).forEach(nodeInfo -> {
            // Only distributor 0 should observe startup timestamps
            verify(f.mockCommunicator).setSystemState(eq(stateBundle), eq(nodeInfo), any());
        });
        ClusterStateBundle expectedDistr0Bundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2 .0.t:600 .1.t:700",
                StateMapping.of("default", "distributor:2 storage:2 .0.s:d .0.t:600 .1.t:700"),
                StateMapping.of("upsidedown", "distributor:2 .0.s:d storage:2 .0.t:600 .1.t:700"));
        verify(f.mockCommunicator).setSystemState(eq(expectedDistr0Bundle), eq(cf.cluster().getNodeInfo(Node.ofDistributor(0))), any());
    }
}
