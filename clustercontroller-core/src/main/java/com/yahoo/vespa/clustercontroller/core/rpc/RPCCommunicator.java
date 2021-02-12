// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int8Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.Values;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.State;
import java.util.logging.Level;
import com.yahoo.vespa.clustercontroller.core.ActivateClusterStateVersionRequest;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.Communicator;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.clustercontroller.core.GetNodeStateRequest;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.SetClusterStateRequest;
import com.yahoo.vespa.clustercontroller.core.Timer;

import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Responsible for doing RPC requests to VDS nodes.
 This class is not thread-safe.
 */
public class RPCCommunicator implements Communicator {

    public static final Logger log = Logger.getLogger(RPCCommunicator.class.getName());

    public static final int ACTIVATE_CLUSTER_STATE_VERSION_RPC_VERSION = 4;
    public static final String ACTIVATE_CLUSTER_STATE_VERSION_RPC_METHOD_NAME = "activate_cluster_state_version";

    public static final int SET_DISTRIBUTION_STATES_RPC_VERSION = 3;
    public static final String SET_DISTRIBUTION_STATES_RPC_METHOD_NAME = "setdistributionstates";

    public static final int LEGACY_SET_SYSTEM_STATE2_RPC_VERSION = 2;
    public static final String LEGACY_SET_SYSTEM_STATE2_RPC_METHOD_NAME = "setsystemstate2";

    private final Timer timer;
    private final Supervisor supervisor;
    private double nodeStateRequestTimeoutIntervalMaxSeconds;
    private int nodeStateRequestTimeoutIntervalStartPercentage;
    private int nodeStateRequestTimeoutIntervalStopPercentage;
    private int nodeStateRequestRoundTripTimeMaxSeconds;
    private final int fleetControllerIndex;

    public static Supervisor createRealSupervisor() {
        return new Supervisor(new Transport("rpc-communicator")).useSmallBuffers();

    }

    public RPCCommunicator(Supervisor supervisor,
                           Timer t,
                           int index,
                           int nodeStateRequestTimeoutIntervalMaxMs,
                           int nodeStateRequestTimeoutIntervalStartPercentage,
                           int nodeStateRequestTimeoutIntervalStopPercentage,
                           int nodeStateRequestRoundTripTimeMaxSeconds) {
        this.timer = t;
        this.fleetControllerIndex = index;
        checkArgument(nodeStateRequestTimeoutIntervalMaxMs > 0);
        checkArgument(nodeStateRequestTimeoutIntervalStartPercentage >= 0);
        checkArgument(nodeStateRequestTimeoutIntervalStartPercentage <= 100);
        checkArgument(nodeStateRequestTimeoutIntervalStopPercentage >= nodeStateRequestTimeoutIntervalStartPercentage);
        checkArgument(nodeStateRequestTimeoutIntervalStartPercentage <= 100);
        checkArgument(nodeStateRequestRoundTripTimeMaxSeconds >= 0);
        this.nodeStateRequestTimeoutIntervalMaxSeconds = nodeStateRequestTimeoutIntervalMaxMs / 1000D;
        this.nodeStateRequestTimeoutIntervalStartPercentage = nodeStateRequestTimeoutIntervalStartPercentage;
        this.nodeStateRequestTimeoutIntervalStopPercentage = nodeStateRequestTimeoutIntervalStopPercentage;
        this.nodeStateRequestRoundTripTimeMaxSeconds = nodeStateRequestRoundTripTimeMaxSeconds;
        this.supervisor = supervisor;
    }

    public void shutdown() {
        supervisor.transport().shutdown().join();
    }

    public Target getConnection(final NodeInfo node) {
        Target t = node.getConnection();
        if (t == null || !t.isValid()) {
            t = node.setConnection(supervisor.connect(new Spec(node.getRpcAddress())));
        }
        return t;
    }

    @Override
    public void propagateOptions(FleetControllerOptions options) {
        checkArgument(options.nodeStateRequestTimeoutMS > 0);
        checkArgument(options.nodeStateRequestTimeoutEarliestPercentage >= 0);
        checkArgument(options.nodeStateRequestTimeoutEarliestPercentage <= 100);
        checkArgument(options.nodeStateRequestTimeoutLatestPercentage
                      >= options.nodeStateRequestTimeoutEarliestPercentage);
        checkArgument(options.nodeStateRequestTimeoutLatestPercentage <= 100);
        checkArgument(options.nodeStateRequestRoundTripTimeMaxSeconds >= 0);
        this.nodeStateRequestTimeoutIntervalMaxSeconds = options.nodeStateRequestTimeoutMS / 1000.0;
        this.nodeStateRequestTimeoutIntervalStartPercentage = options.nodeStateRequestTimeoutEarliestPercentage;
        this.nodeStateRequestTimeoutIntervalStopPercentage = options.nodeStateRequestTimeoutLatestPercentage;
        this.nodeStateRequestRoundTripTimeMaxSeconds = options.nodeStateRequestRoundTripTimeMaxSeconds;
    }

    @Override
    public void getNodeState(NodeInfo node, Waiter<GetNodeStateRequest> externalWaiter) {
        Target connection = getConnection(node);
        if ( ! connection.isValid()) {
            log.log(Level.FINE, () -> String.format("Connection to '%s' could not be created.", node.getRpcAddress()));
        }
        NodeState currentState = node.getReportedState();
        Request req = new Request("getnodestate3");
        req.parameters().add(new StringValue(
                currentState.getState().equals(State.DOWN) || node.getConnectionAttemptCount() > 0
                   ? "unknown" : currentState.serialize()));
        req.parameters().add(new Int32Value(generateNodeStateRequestTimeoutMs()));
        req.parameters().add(new Int32Value(fleetControllerIndex));

        RPCGetNodeStateRequest stateRequest = new RPCGetNodeStateRequest(node, req);
        RPCGetNodeStateWaiter waiter = new RPCGetNodeStateWaiter(stateRequest, externalWaiter, timer);

        double requestTimeoutSeconds =
            nodeStateRequestTimeoutIntervalMaxSeconds + nodeStateRequestRoundTripTimeMaxSeconds;

        connection.invokeAsync(req, requestTimeoutSeconds, waiter);
        node.setCurrentNodeStateRequest(stateRequest, timer.getCurrentTimeInMillis());
        node.lastRequestInfoConnection = connection;
    }

    @Override
    public void setSystemState(ClusterStateBundle stateBundle, NodeInfo node, Waiter<SetClusterStateRequest> externalWaiter) {
        RPCSetClusterStateWaiter waiter = new RPCSetClusterStateWaiter(externalWaiter, timer);
        ClusterState baselineState = stateBundle.getBaselineClusterState();

        Target connection = getConnection(node);
        if ( ! connection.isValid()) {
            log.log(Level.FINE, () -> String.format("Connection to '%s' could not be created.", node.getRpcAddress()));
            return;
        }
        int nodeVersion = node.getVersion();
        Request req;
        if (nodeVersion <= 2) {
            req = new Request(LEGACY_SET_SYSTEM_STATE2_RPC_METHOD_NAME);
            req.parameters().add(new StringValue(baselineState.toString(false)));
        } else {
            req = new Request(SET_DISTRIBUTION_STATES_RPC_METHOD_NAME);
            SlimeClusterStateBundleCodec codec = new SlimeClusterStateBundleCodec();
            EncodedClusterStateBundle encodedBundle = codec.encode(stateBundle);
            Values v = req.parameters();
            v.add(new Int8Value(encodedBundle.getCompression().type().getCode()));
            v.add(new Int32Value(encodedBundle.getCompression().uncompressedSize()));
            v.add(new DataValue(encodedBundle.getCompression().data()));
        }

        log.log(Level.FINE, () -> String.format("Sending '%s' RPC to %s for state version %d",
                req.methodName(), node.getRpcAddress(), stateBundle.getVersion()));
        RPCSetClusterStateRequest stateRequest = new RPCSetClusterStateRequest(node, req, baselineState.getVersion());
        waiter.setRequest(stateRequest);

        connection.invokeAsync(req, 60, waiter);
        node.setClusterStateVersionBundleSent(stateBundle);
    }

    @Override
    public void activateClusterStateVersion(int clusterStateVersion, NodeInfo node, Waiter<ActivateClusterStateVersionRequest> externalWaiter) {
        var waiter = new RPCActivateClusterStateVersionWaiter(externalWaiter);

        Target connection = getConnection(node);
        if ( ! connection.isValid()) {
            log.log(Level.FINE, () -> String.format("Connection to '%s' could not be created.", node.getRpcAddress()));
            return;
        }

        var req = new Request(ACTIVATE_CLUSTER_STATE_VERSION_RPC_METHOD_NAME);
        req.parameters().add(new Int32Value(clusterStateVersion));

        log.log(Level.FINE, () -> String.format("Sending '%s' RPC to %s for state version %d",
                req.methodName(), node.getRpcAddress(), clusterStateVersion));
        var activationRequest = new RPCActivateClusterStateVersionRequest(node, req, clusterStateVersion);
        waiter.setRequest(activationRequest);

        connection.invokeAsync(req, 60, waiter);
        node.setClusterStateVersionActivationSent(clusterStateVersion);
    }

    // protected for testing.
    protected int generateNodeStateRequestTimeoutMs() {
        double intervalFraction = Math.random();
        double earliestTimeoutSeconds =
                nodeStateRequestTimeoutIntervalMaxSeconds * nodeStateRequestTimeoutIntervalStartPercentage / 100.0;
        double latestTimeoutSeconds =
                nodeStateRequestTimeoutIntervalMaxSeconds * nodeStateRequestTimeoutIntervalStopPercentage / 100.0;
        double interval = latestTimeoutSeconds - earliestTimeoutSeconds;
        double timeoutSeconds = earliestTimeoutSeconds + intervalFraction * interval;
        return (int) (timeoutSeconds * 1000);
    }

}
