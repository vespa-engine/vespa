// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.*;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.*;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RPCCommunicatorTest {

    public static final int NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS = 10000;
    public static final int NODE_STATE_REQUEST_TIMEOUT_INTERVAL_START_PERCENTAGE = 80;
    public static final int NODE_STATE_REQUEST_TIMEOUT_INTERVAL_STOP_PERCENTAGE = 95;
    public static final int INDEX = 0;
    public static final int TEST_ITERATIONS = 500;
    public static final int ROUNDTRIP_LATENCY_SECONDS = 2000;

    @Test
    public void testGenerateNodeStateRequestTimeoutMs() throws Exception {
        final RPCCommunicator communicator = new RPCCommunicator(
                RPCCommunicator.createRealSupervisor(),
                null /* Timer */,
                INDEX,
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS,
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_START_PERCENTAGE,
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_STOP_PERCENTAGE,
                0);
        int max = -1;
        int min = 100000;
        final Set<Integer> uniqueTimeoutValues = new HashSet<>();
        for (int x = 0; x < TEST_ITERATIONS; x++) {
            int timeOutMs = communicator.generateNodeStateRequestTimeoutMs();
            min = Math.min(min, timeOutMs);
            max = Math.max(max, timeOutMs);
            uniqueTimeoutValues.add(timeOutMs);
        }
        assertTrue(max <=  NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS *
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_STOP_PERCENTAGE / 100.);
        assertThat(min, is(not(max)));
        assertTrue(min >= NODE_STATE_REQUEST_TIMEOUT_INTERVAL_START_PERCENTAGE *
                NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS / 100);
        assertTrue(uniqueTimeoutValues.size()> TEST_ITERATIONS/2);
    }

    @Test
    public void testGenerateNodeStateRequestTimeoutMsWithUpdates() throws Exception {
        final RPCCommunicator communicator = new RPCCommunicator(RPCCommunicator.createRealSupervisor(), null /* Timer */, INDEX, 1, 1, 100, 0);
        FleetControllerOptions fleetControllerOptions = new FleetControllerOptions(null /*clustername*/);
        fleetControllerOptions.nodeStateRequestTimeoutEarliestPercentage = 100;
        fleetControllerOptions.nodeStateRequestTimeoutLatestPercentage = 100;
        fleetControllerOptions.nodeStateRequestTimeoutMS = NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS;
        communicator.propagateOptions(fleetControllerOptions);
        int timeOutMs = communicator.generateNodeStateRequestTimeoutMs();
        assertThat(timeOutMs, is(NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS));
    }

    @Test
    public void testRoundtripLatency() throws Exception {
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
        communicator.getNodeState(nodeInfo, null);
        Mockito.verify(target).invokeAsync(
                (Request)any(),
                eq(ROUNDTRIP_LATENCY_SECONDS + NODE_STATE_REQUEST_TIMEOUT_INTERVAL_MAX_MS/1000.0),
                (RequestWaiter)any());
    }

    private static class Fixture {
        final Supervisor mockSupervisor = mock(Supervisor.class);
        final Target mockTarget = mock(Target.class);
        final Timer timer = new FakeTimer();
        final RPCCommunicator communicator;
        final AtomicReference<Request> receivedRequest = new AtomicReference<>();
        final AtomicReference<RequestWaiter> receivedWaiter = new AtomicReference<>();
        @SuppressWarnings("unchecked") // Cannot mock with "compiler-obvious" type safety for generics
        final Communicator.Waiter<SetClusterStateRequest> mockWaiter = mock(Communicator.Waiter.class);

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
            }).when(mockTarget).invokeAsync(any(), anyDouble(), any());
        }
    }

    @Test
    public void setSystemState_v3_sends_distribution_states_rpc() {
        Fixture f = new Fixture();
        ClusterFixture cf = ClusterFixture.forFlatCluster(3).bringEntireClusterUp().assignDummyRpcAddresses();
        ClusterStateBundle sentBundle = ClusterStateBundleUtil.makeBundle("distributor:3 storage:3");
        f.communicator.setSystemState(sentBundle, cf.cluster().getNodeInfo(Node.ofStorage(1)), f.mockWaiter);

        Request req = f.receivedRequest.get();
        assertThat(req, notNullValue());
        assertThat(req.methodName(), equalTo(RPCCommunicator.SET_DISTRIBUTION_STATES_RPC_METHOD_NAME));
        assertTrue(req.parameters().satisfies("bix")); // <compression type>, <uncompressed size>, <payload>

        ClusterStateBundle receivedBundle = RPCUtil.decodeStateBundleFromSetDistributionStatesRequest(req);
        assertThat(receivedBundle, equalTo(sentBundle));
    }

    @Test
    public void set_distribution_states_v3_rpc_auto_downgrades_to_v2_on_unknown_method_error() {
        Fixture f = new Fixture();
        ClusterFixture cf = ClusterFixture.forFlatCluster(3).bringEntireClusterUp().assignDummyRpcAddresses();
        ClusterStateBundle sentBundle = ClusterStateBundleUtil.makeBundle("version:123 distributor:3 storage:3");
        f.communicator.setSystemState(sentBundle, cf.cluster().getNodeInfo(Node.ofStorage(1)), f.mockWaiter);

        RequestWaiter waiter = f.receivedWaiter.get();
        assertThat(waiter, notNullValue());
        Request req = f.receivedRequest.get();
        assertThat(req, notNullValue());

        req.setError(ErrorCode.NO_SUCH_METHOD, "que?");
        waiter.handleRequestDone(req);

        // This would normally be done in processResponses(), but that code path is not invoked in this test.
        cf.cluster().getNodeInfo(Node.ofStorage(1)).setSystemStateVersionAcknowledged(123, false);

        f.receivedRequest.set(null);
        // Now when we try again, we should have been downgraded to the legacy setsystemstate2 RPC
        f.communicator.setSystemState(sentBundle, cf.cluster().getNodeInfo(Node.ofStorage(1)), f.mockWaiter);
        req = f.receivedRequest.get();
        assertThat(req, notNullValue());
        assertThat(req.methodName(), equalTo(RPCCommunicator.LEGACY_SET_SYSTEM_STATE2_RPC_METHOD_NAME));
    }

}
