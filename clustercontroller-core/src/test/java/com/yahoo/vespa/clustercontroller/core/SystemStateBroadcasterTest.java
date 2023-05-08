// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SystemStateBroadcasterTest {

    private static class Fixture {
        FakeTimer timer = new FakeTimer();
        final Object monitor = new Object();
        FleetControllerContext context = mock(FleetControllerContext.class);
        SystemStateBroadcaster broadcaster = new SystemStateBroadcaster(context, timer, monitor);
        Communicator mockCommunicator = mock(Communicator.class);
        DatabaseHandler mockDatabaseHandler = mock(DatabaseHandler.class);
        FleetController mockFleetController = mock(FleetController.class);

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

        void simulateBroadcastTick(ClusterFixture cf, int stateVersion) {
            broadcaster.processResponses();
            broadcaster.broadcastNewStateBundleIfRequired(dbContextFrom(cf.cluster()), mockCommunicator, stateVersion);
            try {
                broadcaster.checkIfClusterStateIsAckedByAllDistributors(
                        mockDatabaseHandler, dbContextFrom(cf.cluster()), mockFleetController);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            broadcaster.broadcastStateActivationsIfRequired(dbContextFrom(cf.cluster()), mockCommunicator);
        }
    }

    private static DatabaseHandler.DatabaseContext dbContextFrom(ContentCluster cluster) {
        return new DatabaseHandler.DatabaseContext() {
            @Override
            public ContentCluster getCluster() {
                return cluster;
            }

            @Override
            public FleetController getFleetController() {
                return null; // We assume the broadcaster doesn't use this for our test purposes
            }

            @Override
            public NodeListener getNodeStateUpdateListener() {
                return null;
            }
        };
    }

    private static Stream<NodeInfo> clusterNodeInfos(ContentCluster c, Node... nodes) {
        return Stream.of(nodes).map(c::getNodeInfo);
    }

    @Test
    void always_publish_baseline_cluster_state() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("version:3 distributor:2 storage:2");
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewStateBundleIfRequired(dbContextFrom(cf.cluster()), f.mockCommunicator, 3);
        cf.cluster().getNodeInfos().forEach(nodeInfo -> verify(f.mockCommunicator).setSystemState(eq(stateBundle), eq(nodeInfo), any()));
    }

    @Test
    void non_observed_startup_timestamps_are_published_per_node_for_baseline_state() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("version:3 distributor:2 storage:2");
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.simulateNodePartitionedAwaySilently(cf);
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewStateBundleIfRequired(dbContextFrom(cf.cluster()), f.mockCommunicator, 3);

        clusterNodeInfos(cf.cluster(), Node.ofDistributor(1), Node.ofStorage(0), Node.ofStorage(1)).forEach(nodeInfo -> {
            // Only distributor 0 should observe startup timestamps
            verify(f.mockCommunicator).setSystemState(eq(stateBundle), eq(nodeInfo), any());
        });
        ClusterStateBundle expectedDistr0Bundle = ClusterStateBundleUtil.makeBundle("version:3 distributor:2 storage:2 .0.t:600 .1.t:700");
        verify(f.mockCommunicator).setSystemState(eq(expectedDistr0Bundle), eq(cf.cluster().getNodeInfo(Node.ofDistributor(0))), any());
    }

    @Test
    void bucket_space_states_are_published_verbatim_when_no_additional_timestamps_needed() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("version:3 distributor:2 storage:2",
                StateMapping.of("default", "version:3 distributor:2 storage:2 .0.s:d"),
                StateMapping.of("upsidedown", "version:3 distributor:2 .0.s:d storage:2"));
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewStateBundleIfRequired(dbContextFrom(cf.cluster()), f.mockCommunicator, 3);

        cf.cluster().getNodeInfos().forEach(nodeInfo -> verify(f.mockCommunicator).setSystemState(eq(stateBundle), eq(nodeInfo), any()));
    }

    @Test
    void non_observed_startup_timestamps_are_published_per_bucket_space_state() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("version:3 distributor:2 storage:2",
                StateMapping.of("default", "version:3 distributor:2 storage:2 .0.s:d"),
                StateMapping.of("upsidedown", "version:3 distributor:2 .0.s:d storage:2"));
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.simulateNodePartitionedAwaySilently(cf);
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewStateBundleIfRequired(dbContextFrom(cf.cluster()), f.mockCommunicator, 3);

        clusterNodeInfos(cf.cluster(), Node.ofDistributor(1), Node.ofStorage(0), Node.ofStorage(1)).forEach(nodeInfo -> {
            // Only distributor 0 should observe startup timestamps
            verify(f.mockCommunicator).setSystemState(eq(stateBundle), eq(nodeInfo), any());
        });
        ClusterStateBundle expectedDistr0Bundle = ClusterStateBundleUtil.makeBundle("version:3 distributor:2 storage:2 .0.t:600 .1.t:700",
                StateMapping.of("default", "version:3 distributor:2 storage:2 .0.s:d .0.t:600 .1.t:700"),
                StateMapping.of("upsidedown", "version:3 distributor:2 .0.s:d storage:2 .0.t:600 .1.t:700"));
        verify(f.mockCommunicator).setSystemState(eq(expectedDistr0Bundle), eq(cf.cluster().getNodeInfo(Node.ofDistributor(0))), any());
    }

    @Test
    void state_not_broadcast_if_version_not_tagged_as_written_to_zookeeper() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("version:100 distributor:2 storage:2");
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewStateBundleIfRequired(dbContextFrom(cf.cluster()), f.mockCommunicator, 99);

        cf.cluster().getNodeInfos().forEach(nodeInfo -> {
            verify(f.mockCommunicator, times(0)).setSystemState(any(), eq(nodeInfo), any());
        });
    }

    @Test
    void state_is_broadcast_if_version_is_tagged_as_written_to_zookeeper() {
        Fixture f = new Fixture();
        ClusterStateBundle stateBundle = ClusterStateBundleUtil.makeBundle("version:100 distributor:2 storage:2");
        ClusterFixture cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
        f.broadcaster.handleNewClusterStates(stateBundle);
        f.broadcaster.broadcastNewStateBundleIfRequired(dbContextFrom(cf.cluster()), f.mockCommunicator, 100);

        cf.cluster().getNodeInfos().forEach(nodeInfo -> {
            verify(f.mockCommunicator, times(1)).setSystemState(any(), eq(nodeInfo), any());
        });
    }

    private static class MockSetClusterStateRequest extends SetClusterStateRequest {
        MockSetClusterStateRequest(NodeInfo nodeInfo, int clusterStateVersion) {
            super(nodeInfo, clusterStateVersion);
        }
    }

    private static class MockActivateClusterStateVersionRequest extends ActivateClusterStateVersionRequest {
        public MockActivateClusterStateVersionRequest(NodeInfo nodeInfo, int systemStateVersion) {
            super(nodeInfo, systemStateVersion);
        }
    }

    private static void respondToSetClusterStateBundle(NodeInfo nodeInfo,
                                                       ClusterStateBundle stateBundle,
                                                       Communicator.Waiter<SetClusterStateRequest> waiter) {
        // Have to patch in that we've actually sent the bundle in the first place...
        nodeInfo.setClusterStateVersionBundleSent(stateBundle);

        var req =  new MockSetClusterStateRequest(nodeInfo, stateBundle.getVersion());
        req.setReply(new ClusterStateVersionSpecificRequest.Reply());
        waiter.done(req);
    }

    private static void respondToActivateClusterStateVersion(NodeInfo nodeInfo,
                                                             ClusterStateBundle stateBundle,
                                                             int actualVersion,
                                                             Communicator.Waiter<ActivateClusterStateVersionRequest> waiter) {
        // Have to patch in that we've actually sent the bundle in the first place...
        nodeInfo.setClusterStateVersionActivationSent(stateBundle.getVersion());

        var req =  new MockActivateClusterStateVersionRequest(nodeInfo, stateBundle.getVersion());
        req.setReply(ClusterStateVersionSpecificRequest.Reply.withActualVersion(actualVersion));
        waiter.done(req);
    }

    private static void respondToActivateClusterStateVersion(NodeInfo nodeInfo,
                                                             ClusterStateBundle stateBundle,
                                                             Communicator.Waiter<ActivateClusterStateVersionRequest> waiter) {
        respondToActivateClusterStateVersion(nodeInfo, stateBundle, stateBundle.getVersion(), waiter);
    }

    private static class StateActivationFixture extends Fixture {
        ClusterStateBundle stateBundle;
        ClusterFixture cf;

        @SuppressWarnings("rawtypes") // Java generics <3
        final ArgumentCaptor<Communicator.Waiter> d0Waiter;
        @SuppressWarnings("rawtypes")
        final ArgumentCaptor<Communicator.Waiter> d1Waiter;

        private StateActivationFixture(boolean enableDeferred) {
            super();
            stateBundle = ClusterStateBundleUtil
                    .makeBundleBuilder("version:123 distributor:2 storage:2")
                    .deferredActivation(enableDeferred)
                    .deriveAndBuild();
            cf = ClusterFixture.forFlatCluster(2).bringEntireClusterUp().assignDummyRpcAddresses();
            broadcaster.handleNewClusterStates(stateBundle);
            broadcaster.broadcastNewStateBundleIfRequired(dbContextFrom(cf.cluster()), mockCommunicator, stateBundle.getVersion());

            d0Waiter = ArgumentCaptor.forClass(Communicator.Waiter.class);
            d1Waiter = ArgumentCaptor.forClass(Communicator.Waiter.class);
        }

        @SuppressWarnings("unchecked") // Type erasure of Waiter in mocked argument capture
        void expectSetSystemStateInvocationsToBothDistributors() {
            clusterNodeInfos(cf.cluster(), Node.ofDistributor(0), Node.ofDistributor(1)).forEach(nodeInfo -> {
                verify(mockCommunicator).setSystemState(eq(stateBundle), eq(nodeInfo),
                        (nodeInfo.getNodeIndex() == 0 ? d0Waiter : d1Waiter).capture());
            });
        }

        @SuppressWarnings("unchecked") // Type erasure of Waiter in mocked argument capture
        void ackStateBundleFromBothDistributors() {
            expectSetSystemStateInvocationsToBothDistributors();
            simulateBroadcastTick(cf, 123);

            respondToSetClusterStateBundle(cf.cluster.getNodeInfo(Node.ofDistributor(0)), stateBundle, d0Waiter.getValue());
            respondToSetClusterStateBundle(cf.cluster.getNodeInfo(Node.ofDistributor(1)), stateBundle, d1Waiter.getValue());
            simulateBroadcastTick(cf, 123);
        }

        static StateActivationFixture withTwoPhaseEnabled() {
            return new StateActivationFixture(true);
        }

        static StateActivationFixture withTwoPhaseDisabled() {
            return new StateActivationFixture(false);
        }
    }

    // Type erasure of Waiter in mocked argument capture
    @Test
    @SuppressWarnings("unchecked")
    void activation_not_sent_before_all_distributors_have_acked_state_bundle() {
        var f = StateActivationFixture.withTwoPhaseEnabled();
        var cf = f.cf;

        f.expectSetSystemStateInvocationsToBothDistributors();
        f.simulateBroadcastTick(cf, 123);

        // Respond from distributor 0, but not yet from distributor 1
        respondToSetClusterStateBundle(cf.cluster.getNodeInfo(Node.ofDistributor(0)), f.stateBundle, f.d0Waiter.getValue());
        f.simulateBroadcastTick(cf, 123);

        // No activations should be sent yet
        cf.cluster().getNodeInfos().forEach(nodeInfo -> {
            verify(f.mockCommunicator, times(0)).activateClusterStateVersion(eq(123), eq(nodeInfo), any());
        });
        assertNull(f.broadcaster.getLastClusterStateBundleConverged());

        respondToSetClusterStateBundle(cf.cluster.getNodeInfo(Node.ofDistributor(1)), f.stateBundle, f.d1Waiter.getValue());
        f.simulateBroadcastTick(cf, 123);

        // Activation should now be sent to _all_ nodes (distributor and storage)
        cf.cluster().getNodeInfos().forEach(nodeInfo -> {
            verify(f.mockCommunicator).activateClusterStateVersion(eq(123), eq(nodeInfo), any());
        });
        // But not converged yet, as activations have not been ACKed
        assertNull(f.broadcaster.getLastClusterStateBundleConverged());
    }

    // Type erasure of Waiter in mocked argument capture
    @Test
    @SuppressWarnings("unchecked")
    void state_bundle_not_considered_converged_until_activation_acked_by_all_distributors() {
        var f = StateActivationFixture.withTwoPhaseEnabled();
        var cf = f.cf;

        f.ackStateBundleFromBothDistributors();

        final var d0ActivateWaiter = ArgumentCaptor.forClass(Communicator.Waiter.class);
        final var d1ActivateWaiter = ArgumentCaptor.forClass(Communicator.Waiter.class);

        clusterNodeInfos(cf.cluster(), Node.ofDistributor(0), Node.ofDistributor(1)).forEach(nodeInfo -> {
            verify(f.mockCommunicator).activateClusterStateVersion(eq(123), eq(nodeInfo),
                    (nodeInfo.getNodeIndex() == 0 ? d0ActivateWaiter : d1ActivateWaiter).capture());
        });

        respondToActivateClusterStateVersion(cf.cluster.getNodeInfo(Node.ofDistributor(0)),
                f.stateBundle, d0ActivateWaiter.getValue());
        f.simulateBroadcastTick(cf, 123);

        assertNull(f.broadcaster.getLastClusterStateBundleConverged()); // Not yet converged

        respondToActivateClusterStateVersion(cf.cluster.getNodeInfo(Node.ofDistributor(1)),
                f.stateBundle, d1ActivateWaiter.getValue());
        f.simulateBroadcastTick(cf, 123);

        // Finally, all distributors have ACKed the version! State is marked as converged.
        assertEquals(f.stateBundle, f.broadcaster.getLastClusterStateBundleConverged());
    }

    // Type erasure of Waiter in mocked argument capture
    @Test
    void activation_not_sent_if_deferred_activation_is_disabled_in_state_bundle() {
        var f = StateActivationFixture.withTwoPhaseDisabled();
        var cf = f.cf;

        f.ackStateBundleFromBothDistributors();

        // At this point the cluster state shall be considered converged.
        assertEquals(f.stateBundle, f.broadcaster.getLastClusterStateBundleConverged());

        // No activations shall have been sent.
        clusterNodeInfos(cf.cluster(), Node.ofDistributor(0), Node.ofDistributor(1)).forEach(nodeInfo -> {
            verify(f.mockCommunicator, times(0)).activateClusterStateVersion(eq(123), eq(nodeInfo), any());
        });
    }

    // Type erasure of Waiter in mocked argument capture
    @Test
    @SuppressWarnings("unchecked")
    void activation_convergence_considers_actual_version_returned_from_node() {
        var f = StateActivationFixture.withTwoPhaseEnabled();
        var cf = f.cf;

        f.ackStateBundleFromBothDistributors();

        final var d0ActivateWaiter = ArgumentCaptor.forClass(Communicator.Waiter.class);
        final var d1ActivateWaiter = ArgumentCaptor.forClass(Communicator.Waiter.class);

        clusterNodeInfos(cf.cluster(), Node.ofDistributor(0), Node.ofDistributor(1)).forEach(nodeInfo -> {
            verify(f.mockCommunicator).activateClusterStateVersion(eq(123), eq(nodeInfo),
                    (nodeInfo.getNodeIndex() == 0 ? d0ActivateWaiter : d1ActivateWaiter).capture());
        });

        respondToActivateClusterStateVersion(cf.cluster.getNodeInfo(Node.ofDistributor(0)),
                f.stateBundle, d0ActivateWaiter.getValue());
        // Distributor 1 reports higher actual version, should not cause this version to be
        // considered converged since it's not an exact version match.
        respondToActivateClusterStateVersion(cf.cluster.getNodeInfo(Node.ofDistributor(1)),
                f.stateBundle, 124, d1ActivateWaiter.getValue());
        f.simulateBroadcastTick(cf, 123);

        assertNull(f.broadcaster.getLastClusterStateBundleConverged());
    }

}
