// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.ActivateClusterStateVersionRequest;
import com.yahoo.vespa.clustercontroller.core.ClusterFixture;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundleUtil;
import com.yahoo.vespa.clustercontroller.core.Communicator;
import com.yahoo.vespa.clustercontroller.core.FakeTimer;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.SetClusterStateRequest;
import com.yahoo.vespa.clustercontroller.core.Timer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RPCCommunicatorTest {

    private static final int NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS = 10000;
    private static final int NODE_STATE_REQUEST_TIMEOUT_INTERVAL_START_PERCENTAGE = 80;
    private static final int NODE_STATE_REQUEST_TIMEOUT_INTERVAL_STOP_PERCENTAGE = 95;
    private static final int INDEX = 0;
    private static final int TEST_ITERATIONS = 500;
    private static final int ROUNDTRIP_LATENCY_SECONDS = 2000;

    @Test
    void testGenerateNodeStateRequestTimeoutMs() {
        final RPCCommunicator communicator = new RPCCommunicator(
                RPCCommunicator.createRealSupervisor(),
                null /* Timer */,
                INDEX,
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS,
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_START_PERCENTAGE,
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_STOP_PERCENTAGE,
                0);
        long max = -1;
        long min = 100000;
        final Set<Long> uniqueTimeoutValues = new HashSet<>();
        for (int x = 0; x < TEST_ITERATIONS; x++) {
            long timeOutMs = communicator.generateNodeStateRequestTimeout().toMillis();
            min = Math.min(min, timeOutMs);
            max = Math.max(max, timeOutMs);
            uniqueTimeoutValues.add(timeOutMs);
        }
        assertTrue(max <=  NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS *
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_STOP_PERCENTAGE / 100.);
        assertNotEquals(min, max);
        assertTrue(min >= NODE_STATE_REQUEST_TIMEOUT_INTERVAL_START_PERCENTAGE *
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS / 100);
        assertTrue(uniqueTimeoutValues.size() > TEST_ITERATIONS / 2);
    }

    @Test
    void testGenerateNodeStateRequestTimeoutMsWithUpdates() {
        final RPCCommunicator communicator = new RPCCommunicator(RPCCommunicator.createRealSupervisor(), null /* Timer */, INDEX, 1, 1, 100, 0);
        FleetControllerOptions.Builder builder = new FleetControllerOptions.Builder(null /*clustername*/, Set.of(new ConfiguredNode(0, false)))
                .setNodeStateRequestTimeoutEarliestPercentage(100)
                .setNodeStateRequestTimeoutLatestPercentage(100)
                .setNodeStateRequestTimeoutMS(NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS)
                .setZooKeeperServerAddress("localhost:2181");
        communicator.propagateOptions(builder.build());
        long timeOutMs = communicator.generateNodeStateRequestTimeout().toMillis();
        assertEquals(timeOutMs, NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS);
    }

    @Test
    void testRoundtripLatency() {
        final Timer timer = new FakeTimer();
        final RPCCommunicator communicator = new RPCCommunicator(
                RPCCommunicator.createRealSupervisor(),
                timer,
                INDEX,
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS,
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_STOP_PERCENTAGE,
                100,
                ROUNDTRIP_LATENCY_SECONDS);

        final NodeInfo nodeInfo = mock(NodeInfo.class);
        final Target target = mock(Target.class);

        when(target.isValid()).thenReturn(true);
        when(nodeInfo.getConnection()).thenReturn(target);
        when(nodeInfo.getVersion()).thenReturn(3);
        when(nodeInfo.getReportedState()).thenReturn(new NodeState(NodeType.DISTRIBUTOR, State.UP));
        communicator.getNodeState(nodeInfo, null);
        Mockito.verify(target).invokeAsync(
                any(),
                eq(Duration.ofSeconds(ROUNDTRIP_LATENCY_SECONDS).plusMillis(NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS)),
                any());
    }

    private static class Fixture<RequestType> {
        final Supervisor mockSupervisor = mock(Supervisor.class);
        final Target mockTarget = mock(Target.class);
        final Timer timer = new FakeTimer();
        final RPCCommunicator communicator;
        final AtomicReference<Request> receivedRequest = new AtomicReference<>();
        final AtomicReference<RequestWaiter> receivedWaiter = new AtomicReference<>();
        @SuppressWarnings("unchecked") // Cannot mock with "compiler-obvious" type safety for generics
        final Communicator.Waiter<RequestType> mockWaiter = mock(Communicator.Waiter.class);

        Fixture() {
            communicator = new RPCCommunicator(
                    mockSupervisor,
                    timer,
                    INDEX,
                    NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS,
                    NODE_STATE_REQUEST_TIMEOUT_INTERVAL_STOP_PERCENTAGE,
                    100,
                    ROUNDTRIP_LATENCY_SECONDS);

            when(mockSupervisor.connect(any())).thenReturn(mockTarget);
            when(mockTarget.isValid()).thenReturn(true);
            doAnswer((invocation) -> {
                receivedRequest.set((Request) invocation.getArguments()[0]);
                receivedWaiter.set((RequestWaiter) invocation.getArguments()[2]);
                return null;
            }).when(mockTarget).invokeAsync(any(), any(Duration.class), any());
        }
    }

    @Test
    void setSystemState_v3_sends_distribution_states_rpc() {
        var f = new Fixture<SetClusterStateRequest>();
        var cf = ClusterFixture.forFlatCluster(3).bringEntireClusterUp().assignDummyRpcAddresses();
        var sentBundle = ClusterStateBundleUtil.makeBundle("distributor:3 storage:3");
        f.communicator.setSystemState(sentBundle, cf.cluster().getNodeInfo(Node.ofStorage(1)), f.mockWaiter);

        Request req = f.receivedRequest.get();
        assertNotNull(req);
        assertEquals(req.methodName(), RPCCommunicator.SET_DISTRIBUTION_STATES_RPC_METHOD_NAME);
        assertTrue(req.parameters().satisfies("bix")); // <compression type>, <uncompressed size>, <payload>

        ClusterStateBundle receivedBundle = RPCUtil.decodeStateBundleFromSetDistributionStatesRequest(req);
        assertEquals(receivedBundle, sentBundle);
    }

    @Test
    void activateClusterStateVersion_sends_version_activation_rpc() {
        var f = new Fixture<ActivateClusterStateVersionRequest>();
        var cf = ClusterFixture.forFlatCluster(3).bringEntireClusterUp().assignDummyRpcAddresses();
        f.communicator.activateClusterStateVersion(12345, cf.cluster().getNodeInfo(Node.ofDistributor(1)), f.mockWaiter);

        Request req = f.receivedRequest.get();
        assertNotNull(req);
        assertEquals(req.methodName(), RPCCommunicator.ACTIVATE_CLUSTER_STATE_VERSION_RPC_METHOD_NAME);
        assertTrue(req.parameters().satisfies("i")); // <cluster state version>
        assertEquals(req.parameters().get(0).asInt32(), 12345);
    }

}
