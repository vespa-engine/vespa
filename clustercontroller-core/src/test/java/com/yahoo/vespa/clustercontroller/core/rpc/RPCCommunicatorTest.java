// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Target;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.*;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
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
        final RPCCommunicator communicator = new RPCCommunicator(null /* Timer */, INDEX, 1, 1, 100, 0);
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
}
