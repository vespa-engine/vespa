// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;

import java.util.logging.Level;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

public class SystemStateBroadcaster {

    private static final Logger log = Logger.getLogger(SystemStateBroadcaster.class.getName());

    private final FleetControllerContext context;
    private final Timer timer;
    private final Object monitor;
    private ClusterStateBundle clusterStateBundle;
    private final List<SetClusterStateRequest> setClusterStateReplies = new LinkedList<>();
    private final List<ActivateClusterStateVersionRequest> activateClusterStateVersionReplies = new LinkedList<>();

    private final static long minTimeBetweenNodeErrorLogging = 10 * 60 * 1000;
    private final Map<Node, Long> lastErrorReported = new TreeMap<>();

    private int lastOfficialStateVersion = -1;
    private int lastStateVersionBundleAcked = 0;
    private int lastClusterStateVersionConverged = 0;
    private ClusterStateBundle lastClusterStateBundleConverged;

    private final SetClusterStateWaiter setClusterStateWaiter = new SetClusterStateWaiter();
    private final ActivateClusterStateVersionWaiter activateClusterStateVersionWaiter = new ActivateClusterStateVersionWaiter();

    public SystemStateBroadcaster(FleetControllerContext context, Timer timer, Object monitor) {
        this.context = context;
        this.timer = timer;
        this.monitor = monitor;
    }

    public void handleNewClusterStates(ClusterStateBundle state) {
        clusterStateBundle = state;
    }

    public ClusterState getClusterState() {
        return clusterStateBundle.getBaselineClusterState();
    }

    public boolean hasBroadcastedClusterStateBundle() {
        return clusterStateBundle != null;
    }

    public void resetBroadcastedClusterStateBundle() {
        clusterStateBundle = null;
    }

    public ClusterStateBundle getClusterStateBundle() {
        return clusterStateBundle;
    }

    public ClusterStateBundle getLastClusterStateBundleConverged() {
        return lastClusterStateBundleConverged;
    }

    private void reportNodeError(boolean nodeOk, NodeInfo info, String message) {
        long time = timer.getCurrentTimeInMillis();
        Long lastReported = lastErrorReported.get(info.getNode());
        boolean alreadySeen = (lastReported != null && time - lastReported < minTimeBetweenNodeErrorLogging);
        context.log(log, nodeOk && !alreadySeen ? Level.WARNING : Level.FINE, message);
        if (!alreadySeen) {
            lastErrorReported.put(info.getNode(), time);
        }
    }

    public boolean processResponses() {
        boolean anyResponsesFound = false;
        synchronized(monitor) {
            anyResponsesFound = !setClusterStateReplies.isEmpty() || !activateClusterStateVersionReplies.isEmpty();
            processSetClusterStateResponses();
            processActivateClusterStateVersionResponses();
        }
        return anyResponsesFound;
    }

    private void processActivateClusterStateVersionResponses() {
        for (var req : activateClusterStateVersionReplies) {
            NodeInfo info = req.getNodeInfo();
            int version = req.getClusterStateVersion();
            boolean success = true;
            var reply = req.getReply();
            if (reply.isError()) {
                // NO_SUCH_METHOD implies node is on a version that does not understand explicit activations
                // and it has already merrily started using the state version. Treat as if it had been ACKed.
                if (reply.getReturnCode() != ErrorCode.NO_SUCH_METHOD) {
                    context.log(log,
                                Level.FINE,
                                () -> String.format("Activation NACK for node %s with version %d, message %s",
                                                    info, version, reply.getReturnMessage()));
                    success = false;
                } else {
                    context.log(log,
                                Level.FINE,
                                () -> String.format("Node %s did not understand state activation RPC; " +
                                                    "implicitly treating state %d as activated on node",
                                                    info, version));
                }
            } else if (reply.getActualVersion() != version) {
                boolean nodeOk = nodeReportsSelfAsAvailable(info);
                // Avoid spamming the logs since this will happen on all resends until (presumably) the controller
                // loses election status.
                // TODO this should trigger a loss of current controller's leadership!
                reportNodeError(nodeOk, info, String.format("Activation of version %d did not take effect, node %s " +
                                "reports it has an actual pending version of %d. Racing with another controller?",
                                version, info, reply.getActualVersion()));
                success = false;
            } else {
                context.log(log,
                            Level.FINE,
                            () -> String.format("Node %s reports successful activation of state version %d",
                                                info, version));
            }
            info.setSystemStateVersionActivationAcked(version, success);
            // TODO we currently don't invoke reportNodeError here.. We assume that node errors will be reported
            // as part of processSetClusterStateResponses anyway, but can add it here as well if deemed necessary.
        }
        activateClusterStateVersionReplies.clear();
    }

    private static boolean nodeReportsSelfAsAvailable(NodeInfo info) {
        return info.getReportedState().getState().oneOf("uir");
    }

    private void processSetClusterStateResponses() {
        for (SetClusterStateRequest req : setClusterStateReplies) {
            NodeInfo info = req.getNodeInfo();
            int version = req.getClusterStateVersion();

            if (req.getReply().isError()) {
                info.setClusterStateBundleVersionAcknowledged(version, false);
                if (req.getReply().getReturnCode() != Communicator.TRANSIENT_ERROR) {
                    if (info.getNewestSystemStateVersionSent() == version) {
                        boolean nodeOk = nodeReportsSelfAsAvailable(info);
                        reportNodeError(nodeOk, info,
                                String.format("Got error response %d: %s from %s setdistributionstates request.",
                                        req.getReply().getReturnCode(), req.getReply().getReturnMessage(), info));
                    }
                }
            } else {
                info.setClusterStateBundleVersionAcknowledged(version, true);
                context.log(log, Level.FINE, () -> String.format("Node %s ACKed system state version %d.", info, version));
                lastErrorReported.remove(info.getNode());
            }
        }
        setClusterStateReplies.clear();
    }

    private static boolean nodeIsReachable(NodeInfo node) {
        if (node.getRpcAddress() == null || node.isNotInSlobrok()) {
            return false; // Can't set state on nodes we don't know where are
        }
        if (node.getReportedState().getState() == State.MAINTENANCE ||
            node.getReportedState().getState() == State.DOWN ||
            node.getReportedState().getState() == State.STOPPING)
        {
            return false; // No point in sending system state to nodes that can't receive messages or don't want them
        }
        return true;
    }

    private boolean nodeNeedsClusterStateBundle(NodeInfo node) {
        if (node.getClusterStateVersionBundleAcknowledged() == clusterStateBundle.getVersion()) {
            return false; // No point in sending if node already has updated system state
        }
        return nodeIsReachable(node);
    }

    private boolean nodeNeedsClusterStateActivation(NodeInfo node) {
        if (node.getClusterStateVersionActivationAcked() == clusterStateBundle.getVersion()) {
            return false; // No point in sending if node already has activated cluster state version
        }
        return nodeIsReachable(node);
    }

    private List<NodeInfo> resolveStateVersionSendSet(DatabaseHandler.DatabaseContext dbContext) {
        return dbContext.getCluster().getNodeInfos().stream()
                        .filter(this::nodeNeedsClusterStateBundle)
                        .filter(node -> !newestStateBundleAlreadySentToNode(node))
                        .toList();
    }

    // Precondition: no nodes in the cluster need to receive the current cluster state version bundle
    private List<NodeInfo> resolveStateActivationSendSet(DatabaseHandler.DatabaseContext dbContext) {
        return dbContext.getCluster().getNodeInfos().stream()
                        .filter(this::nodeNeedsClusterStateActivation)
                        .filter(node -> !newestStateActivationAlreadySentToNode(node))
                        .toList();
    }

    private boolean newestStateBundleAlreadySentToNode(NodeInfo node) {
        return (node.getNewestSystemStateVersionSent() == clusterStateBundle.getVersion());
    }

    private boolean newestStateActivationAlreadySentToNode(NodeInfo node) {
        return (node.getClusterStateVersionActivationSent() == clusterStateBundle.getVersion());
    }

    /**
     * Checks if all distributor nodes have ACKed (and activated) the most recent cluster state.
     * Iff this is the case, triggers handleAllDistributorsInSync() on the provided FleetController
     * object and updates the broadcaster's last known in-sync cluster state version.
     */
    void checkIfClusterStateIsAckedByAllDistributors(DatabaseHandler database,
                                                     DatabaseHandler.DatabaseContext dbContext,
                                                     FleetController fleetController) throws InterruptedException {
        if ((clusterStateBundle == null) || currentClusterStateIsConverged()) {
            return; // Nothing to do for the current state
        }
        final int currentStateVersion = clusterStateBundle.getVersion();
        boolean anyDistributorsNeedStateBundle = dbContext.getCluster().getNodeInfos().stream()
                                                          .filter(NodeInfo::isDistributor)
                                                          .anyMatch(this::nodeNeedsClusterStateBundle);

        if (!anyDistributorsNeedStateBundle && (currentStateVersion > lastStateVersionBundleAcked)) {
            markCurrentClusterStateBundleAsReceivedByAllDistributors();
            if (clusterStateBundle.deferredActivation()) {
                context.log(log,
                            Level.FINE,
                            () -> String.format("All distributors have ACKed cluster state " +
                                                "version %d, sending activation", currentStateVersion));
            } else {
                markCurrentClusterStateAsConverged(database, dbContext, fleetController);
            }
            return; // Either converged (no two-phase) or activations must be sent before we can continue.
        }

        if (anyDistributorsNeedStateBundle || !clusterStateBundle.deferredActivation()) {
            return;
        }

        boolean anyDistributorsNeedActivation = dbContext.getCluster().getNodeInfos().stream()
                                                         .filter(NodeInfo::isDistributor)
                                                         .anyMatch(this::nodeNeedsClusterStateActivation);

        if (!anyDistributorsNeedActivation && (currentStateVersion > lastClusterStateVersionConverged)) {
            markCurrentClusterStateAsConverged(database, dbContext, fleetController);
        } else {
            context.log(log,
                        Level.FINE,
                        () -> String.format("distributors still need activation in state %d (last converged: %d)",
                                            currentStateVersion, lastClusterStateVersionConverged));
        }
    }

    private void markCurrentClusterStateBundleAsReceivedByAllDistributors() {
        lastStateVersionBundleAcked = clusterStateBundle.getVersion();
    }

    private void markCurrentClusterStateAsConverged(DatabaseHandler database, DatabaseHandler.DatabaseContext dbContext, FleetController fleetController) {
        context.log(log, Level.FINE, "All distributors have newest clusterstate, updating start timestamps in zookeeper and clearing them from cluster state");
        lastClusterStateVersionConverged = clusterStateBundle.getVersion();
        lastClusterStateBundleConverged = clusterStateBundle;
        fleetController.handleAllDistributorsInSync(database, dbContext);
    }

    private boolean currentClusterStateIsConverged() {
        return lastClusterStateVersionConverged == clusterStateBundle.getVersion();
    }

    private boolean currentBundleVersionIsTaggedOfficial() {
        return clusterStateBundle.getVersion() == lastOfficialStateVersion;
    }

    private void tagCurrentBundleVersionAsOfficial() {
        lastOfficialStateVersion = clusterStateBundle.getVersion();
    }

    public boolean broadcastNewStateBundleIfRequired(DatabaseHandler.DatabaseContext dbContext, Communicator communicator,
                                                     int lastClusterStateVersionWrittenToZooKeeper) {
        if (clusterStateBundle == null || clusterStateBundle.getVersion() == 0) {
            return false;
        }
        if (clusterStateBundle.getVersion() != lastClusterStateVersionWrittenToZooKeeper) {
            return false;
        }

        ClusterState baselineState = clusterStateBundle.getBaselineClusterState();

        if (!currentBundleVersionIsTaggedOfficial()) {
            context.log(log, Level.INFO, "Publishing cluster state version " + baselineState.getVersion());
            tagCurrentBundleVersionAsOfficial();
        }

        List<NodeInfo> recipients = resolveStateVersionSendSet(dbContext);
        for (NodeInfo node : recipients) {
            if (nodeNeedsToObserveStartupTimestamps(node)) {
                // TODO this is the same for all nodes, compute only once
                ClusterStateBundle modifiedBundle = clusterStateBundle.cloneWithMapper(state -> buildModifiedClusterState(state, dbContext));
                context.log(log,
                            Level.FINE,
                            () -> "Sending modified cluster state version " + baselineState.getVersion() +
                                  " to node " + node + ": " + modifiedBundle);
                communicator.setSystemState(modifiedBundle, node, setClusterStateWaiter);
            } else {
                context.log(log,
                            Level.FINE,
                            () -> "Sending system state version " + baselineState.getVersion() +
                                  " to node " + node + ". (went down time " + node.getWentDownWithStartTime() +
                                  ", node start time " + node.getStartTimestamp() + ")");
                communicator.setSystemState(clusterStateBundle, node, setClusterStateWaiter);
            }
        }

        return !recipients.isEmpty();
    }

    public boolean broadcastStateActivationsIfRequired(DatabaseHandler.DatabaseContext dbContext, Communicator communicator) {
        if (clusterStateBundle == null || clusterStateBundle.getVersion() == 0 || !currentBundleVersionIsTaggedOfficial()) {
            return false;
        }

        if (!clusterStateBundle.deferredActivation() || !allDistributorsHaveAckedSentClusterStateBundle()) {
            return false;
        }

        var recipients = resolveStateActivationSendSet(dbContext);
        for (NodeInfo node : recipients) {
            context.log(log,
                        Level.FINE,
                        () -> "Sending cluster state activation to node " + node + " for version " +
                              clusterStateBundle.getVersion());
            communicator.activateClusterStateVersion(clusterStateBundle.getVersion(), node, activateClusterStateVersionWaiter);
        }

        return !recipients.isEmpty();
    }

    private boolean allDistributorsHaveAckedSentClusterStateBundle() {
        return (lastStateVersionBundleAcked == clusterStateBundle.getVersion());
    }

    public int lastClusterStateVersionInSync() { return lastClusterStateVersionConverged; }

    private static boolean nodeNeedsToObserveStartupTimestamps(NodeInfo node) {
        return node.getStartTimestamp() != 0 && node.getWentDownWithStartTime() == node.getStartTimestamp();
    }

    private static ClusterState buildModifiedClusterState(ClusterState sourceState, DatabaseHandler.DatabaseContext dbContext) {
        ClusterState newState = sourceState.clone();
        for (NodeInfo n : dbContext.getCluster().getNodeInfos()) {
            NodeState ns = newState.getNodeState(n.getNode());
            if (!n.isDistributor() && ns.getStartTimestamp() == 0) {
                ns.setStartTimestamp(n.getStartTimestamp());
                newState.setNodeState(n.getNode(), ns);
            }
        }
        return newState;
    }

    private class SetClusterStateWaiter implements Communicator.Waiter<SetClusterStateRequest> {
        @Override
        public void done(SetClusterStateRequest reply) {
            synchronized (monitor) {
                setClusterStateReplies.add(reply);
            }
        }
    }

    private class ActivateClusterStateVersionWaiter implements Communicator.Waiter<ActivateClusterStateVersionRequest> {
        @Override
        public void done(ActivateClusterStateVersionRequest reply) {
            synchronized (monitor) {
                activateClusterStateVersionReplies.add(reply);
            }
        }
    }

}
