// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SystemStateBroadcaster {

    public static Logger log = Logger.getLogger(SystemStateBroadcaster.class.getName());

    private final Timer timer;
    private final Object monitor;
    private ClusterStateBundle clusterStateBundle;
    private final List<SetClusterStateRequest> replies = new LinkedList<>();

    private final static long minTimeBetweenNodeErrorLogging = 10 * 60 * 1000;
    private final Map<Node, Long> lastErrorReported = new TreeMap<>();
    private int lastClusterStateInSync = 0;

    private final ClusterStateWaiter waiter = new ClusterStateWaiter();

    public SystemStateBroadcaster(Timer timer, Object monitor) {
        this.timer = timer;
        this.monitor = monitor;
    }

    public void handleNewClusterStates(ClusterStateBundle state) {
        clusterStateBundle = state;
    }

    public ClusterState getClusterState() {
        return clusterStateBundle.getBaselineClusterState();
    }

    private void reportNodeError(boolean nodeOk, NodeInfo info, String message) {
        long time = timer.getCurrentTimeInMillis();
        Long lastReported = lastErrorReported.get(info.getNode());
        boolean alreadySeen = (lastReported != null && time - lastReported < minTimeBetweenNodeErrorLogging);
        log.log(nodeOk && !alreadySeen ? LogLevel.WARNING : LogLevel.DEBUG, message);
        if (!alreadySeen) lastErrorReported.put(info.getNode(), time);
    }

    public boolean processResponses() {
        boolean anyResponsesFound = false;
        synchronized(monitor) {
            for(SetClusterStateRequest req : replies) {
                anyResponsesFound = true;

                NodeInfo info = req.getNodeInfo();
                boolean nodeOk = info.getReportedState().getState().oneOf("uir");
                int version = req.getSystemStateVersion();

                if (req.getReply().isError()) {
                    info.setSystemStateVersionAcknowledged(version, false);
                    if (req.getReply().getReturnCode() != Communicator.TRANSIENT_ERROR) {
                        if (info.getNewestSystemStateVersionSent() == version) {
                            reportNodeError(nodeOk, info,
                                    "Got error response " + req.getReply().getReturnCode() + ": " + req.getReply().getReturnMessage()
                                            + " from " + info + " setsystemstate request.");
                        }
                    }
                } else {
                    info.setSystemStateVersionAcknowledged(version, true);
                    log.log(LogLevel.DEBUG, "Node " + info + " acked system state version " + version + ".");
                    lastErrorReported.remove(info.getNode());
                }
            }
            replies.clear();
        }
        return anyResponsesFound;
    }

    private boolean nodeNeedsClusterState(NodeInfo node) {
        if (node.getSystemStateVersionAcknowledged() == clusterStateBundle.getVersion()) {
            return false; // No point in sending if node already has updated system state
        }
        if (node.getRpcAddress() == null || node.isRpcAddressOutdated()) {
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

    private List<NodeInfo> resolveStateVersionSendSet(DatabaseHandler.Context dbContext) {
        return dbContext.getCluster().getNodeInfo().stream()
                .filter(this::nodeNeedsClusterState)
                .filter(node -> !newestStateAlreadySentToNode(node))
                .collect(Collectors.toList());
    }

    private boolean newestStateAlreadySentToNode(NodeInfo node) {
        return (node.getNewestSystemStateVersionSent() == clusterStateBundle.getVersion());
    }

    /**
     * Checks if all distributor nodes have ACKed the most recent cluster state. Iff this
     * is the case, triggers handleAllDistributorsInSync() on the provided FleetController
     * object and updates the broadcaster's last known in-sync cluster state version.
     */
    void checkIfClusterStateIsAckedByAllDistributors(DatabaseHandler database,
                                                        DatabaseHandler.Context dbContext,
                                                        FleetController fleetController) throws InterruptedException {
        final int currentStateVersion = clusterStateBundle.getVersion();
        if ((clusterStateBundle == null) || (lastClusterStateInSync == currentStateVersion)) {
            return; // Nothing to do for the current state
        }
        boolean anyOutdatedDistributorNodes = dbContext.getCluster().getNodeInfo().stream()
                .filter(NodeInfo::isDistributor)
                .anyMatch(this::nodeNeedsClusterState);

        if (!anyOutdatedDistributorNodes && (currentStateVersion > lastClusterStateInSync)) {
            log.log(LogLevel.DEBUG, "All distributors have newest clusterstate, updating start timestamps in zookeeper and clearing them from cluster state");
            lastClusterStateInSync = currentStateVersion;
            fleetController.handleAllDistributorsInSync(database, dbContext);
        }
    }

    public boolean broadcastNewState(DatabaseHandler.Context dbContext, Communicator communicator) {
        if (clusterStateBundle == null) {
            return false;
        }

        ClusterState baselineState = clusterStateBundle.getBaselineClusterState();

        if (!baselineState.isOfficial()) {
            log.log(LogLevel.INFO, String.format("Publishing cluster state version %d", baselineState.getVersion()));
            baselineState.setOfficial(true);
        }

        List<NodeInfo> recipients = resolveStateVersionSendSet(dbContext);
        for (NodeInfo node : recipients) {
            if (nodeNeedsToObserveStartupTimestamps(node)) {
                // TODO this is the same for all nodes, compute only once
                ClusterStateBundle modifiedBundle = clusterStateBundle.cloneWithMapper(state -> buildModifiedClusterState(state, dbContext));
                log.log(LogLevel.DEBUG, "Sending modified cluster state version " + baselineState.getVersion()
                        + " to node " + node + ": " + modifiedBundle);
                communicator.setSystemState(modifiedBundle, node, waiter);
            } else {
                log.log(LogLevel.DEBUG, "Sending system state version " + baselineState.getVersion() + " to node " + node
                        + ". (went down time " + node.getWentDownWithStartTime() + ", node start time " + node.getStartTimestamp() + ")");
                communicator.setSystemState(clusterStateBundle, node, waiter);
            }
        }

        return !recipients.isEmpty();
    }

    public int lastClusterStateVersionInSync() { return lastClusterStateInSync; }

    private static boolean nodeNeedsToObserveStartupTimestamps(NodeInfo node) {
        return node.getStartTimestamp() != 0 && node.getWentDownWithStartTime() == node.getStartTimestamp();
    }

    private static ClusterState buildModifiedClusterState(ClusterState sourceState, DatabaseHandler.Context dbContext) {
        ClusterState newState = sourceState.clone();
        for (NodeInfo n : dbContext.getCluster().getNodeInfo()) {
            NodeState ns = newState.getNodeState(n.getNode());
            if (!n.isDistributor() && ns.getStartTimestamp() == 0) {
                ns.setStartTimestamp(n.getStartTimestamp());
                newState.setNodeState(n.getNode(), ns);
            }
        }
        return newState;
    }

    private class ClusterStateWaiter implements Communicator.Waiter<SetClusterStateRequest> {
        @Override
        public void done(SetClusterStateRequest reply) {
            synchronized (monitor) {
                replies.add(reply);
            }
        }
    }

}
