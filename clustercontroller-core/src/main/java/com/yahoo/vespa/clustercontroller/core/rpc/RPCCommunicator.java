// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.ActivateClusterStateVersionRequest;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.Communicator;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.clustercontroller.core.GetNodeStateRequest;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.SetClusterStateRequest;
import com.yahoo.vespa.clustercontroller.core.Timer;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Responsible for doing RPC requests to storage nodes.
 * This class is not thread-safe.
 */
public class RPCCommunicator implements Communicator {

    public static final Logger log = Logger.getLogger(RPCCommunicator.class.getName());

    // Rpc method versions and Vespa versions which supports each version:
    // 0 - 4.1
    // 1 - 4.2-5.0.10
    // 2 - 5.0.11-8.48.3
    // 3 - 6.220+
    // 4 - 7.24+
    public static final int ACTIVATE_CLUSTER_STATE_VERSION_RPC_VERSION = 4;
    public static final String ACTIVATE_CLUSTER_STATE_VERSION_RPC_METHOD_NAME = "activate_cluster_state_version";

    public static final int SET_DISTRIBUTION_STATES_RPC_VERSION = 3;
    public static final String SET_DISTRIBUTION_STATES_RPC_METHOD_NAME = "setdistributionstates";

    private final Timer timer;
    private final Supervisor supervisor;
    private Duration nodeStateRequestTimeoutIntervalMax;
    private int nodeStateRequestTimeoutIntervalStartPercentage;
    private int nodeStateRequestTimeoutIntervalStopPercentage;
    private Duration nodeStateRequestRoundTripTimeMax;
    private final int fleetControllerIndex;

    public static Supervisor createRealSupervisor() {
        return new Supervisor(new Transport("rpc-communicator")).setDropEmptyBuffers(true);
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
        checkArgument(nodeStateRequestTimeoutIntervalStopPercentage <= 100);
        checkArgument(nodeStateRequestRoundTripTimeMaxSeconds >= 0);
        this.nodeStateRequestTimeoutIntervalMax = Duration.ofMillis(nodeStateRequestTimeoutIntervalMaxMs);
        this.nodeStateRequestTimeoutIntervalStartPercentage = nodeStateRequestTimeoutIntervalStartPercentage;
        this.nodeStateRequestTimeoutIntervalStopPercentage = nodeStateRequestTimeoutIntervalStopPercentage;
        this.nodeStateRequestRoundTripTimeMax = Duration.ofSeconds(nodeStateRequestRoundTripTimeMaxSeconds);
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
        checkArgument(options.nodeStateRequestTimeoutMS() > 0);
        checkArgument(options.nodeStateRequestTimeoutEarliestPercentage() >= 0);
        checkArgument(options.nodeStateRequestTimeoutEarliestPercentage() <= 100);
        checkArgument(options.nodeStateRequestTimeoutLatestPercentage() >= options.nodeStateRequestTimeoutEarliestPercentage());
        checkArgument(options.nodeStateRequestTimeoutLatestPercentage() <= 100);
        checkArgument(options.nodeStateRequestRoundTripTimeMaxSeconds() >= 0);
        this.nodeStateRequestTimeoutIntervalMax = Duration.ofMillis(options.nodeStateRequestTimeoutMS());
        this.nodeStateRequestTimeoutIntervalStartPercentage = options.nodeStateRequestTimeoutEarliestPercentage();
        this.nodeStateRequestTimeoutIntervalStopPercentage = options.nodeStateRequestTimeoutLatestPercentage();
        this.nodeStateRequestRoundTripTimeMax = Duration.ofSeconds(options.nodeStateRequestRoundTripTimeMaxSeconds());
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
        req.parameters().add(new Int32Value((int)generateNodeStateRequestTimeout().toMillis()));
        req.parameters().add(new Int32Value(fleetControllerIndex));

        RPCGetNodeStateRequest stateRequest = new RPCGetNodeStateRequest(node, req);
        RPCGetNodeStateWaiter waiter = new RPCGetNodeStateWaiter(stateRequest, externalWaiter);

        Duration requestTimeout = nodeStateRequestTimeoutIntervalMax.plus(nodeStateRequestRoundTripTimeMax);

        connection.invokeAsync(req, requestTimeout, waiter);
        node.setCurrentNodeStateRequest(stateRequest, timer.getCurrentTimeInMillis());
        node.lastRequestInfoConnection = connection;
    }

    @Override
    public void setSystemState(ClusterStateBundle stateBundle, NodeInfo node, Waiter<SetClusterStateRequest> externalWaiter) {
        RPCSetClusterStateWaiter waiter = new RPCSetClusterStateWaiter(externalWaiter);
        ClusterState baselineState = stateBundle.getBaselineClusterState();

        Target connection = getConnection(node);
        if ( ! connection.isValid()) {
            log.log(Level.FINE, () -> String.format("Connection to '%s' could not be created.", node.getRpcAddress()));
            return;
        }
        Request req = new Request(SET_DISTRIBUTION_STATES_RPC_METHOD_NAME);
        SlimeClusterStateBundleCodec codec = new SlimeClusterStateBundleCodec();
        EncodedClusterStateBundle encodedBundle = codec.encode(stateBundle);
        Values v = req.parameters();
        v.add(new Int8Value(encodedBundle.getCompression().type().getCode()));
        v.add(new Int32Value(encodedBundle.getCompression().uncompressedSize()));
        v.add(new DataValue(encodedBundle.getCompression().data()));

        log.log(Level.FINE, () -> String.format("Sending '%s' RPC to %s for state version %d",
                req.methodName(), node.getRpcAddress(), stateBundle.getVersion()));
        RPCSetClusterStateRequest stateRequest = new RPCSetClusterStateRequest(node, req, baselineState.getVersion());
        waiter.setRequest(stateRequest);

        connection.invokeAsync(req, Duration.ofSeconds(60), waiter);
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

        connection.invokeAsync(req, Duration.ofSeconds(60), waiter);
        node.setClusterStateVersionActivationSent(clusterStateVersion);
    }

    // protected for testing.
    protected Duration generateNodeStateRequestTimeout() {
        double intervalFraction = Math.random();
        long earliestTimeoutNanos =
                nodeStateRequestTimeoutIntervalMax.toNanos() * nodeStateRequestTimeoutIntervalStartPercentage / 100;
        long latestTimeoutNanos =
                nodeStateRequestTimeoutIntervalMax.toNanos() * nodeStateRequestTimeoutIntervalStopPercentage / 100;
        long interval = latestTimeoutNanos - earliestTimeoutNanos;
        long timeoutNanos = earliestTimeoutNanos + (long)(intervalFraction * interval);
        return Duration.ofNanos(timeoutNanos);
    }

}
