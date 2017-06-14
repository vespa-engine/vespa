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
    private ClusterState systemState;
    private final List<SetClusterStateRequest> replies = new LinkedList<>();

    private final static long minTimeBetweenNodeErrorLogging = 10 * 60 * 1000;
    private final Map<Node, Long> lastErrorReported = new TreeMap<>();
    private int lastClusterStateInSync = 0;

    private final ClusterStateWaiter waiter = new ClusterStateWaiter();

    public SystemStateBroadcaster(Timer timer, Object monitor) {
        this.timer = timer;
        this.monitor = monitor;
    }

    public void handleNewSystemState(ClusterState state) {
        systemState = state;
    }

    public ClusterState getClusterState() {
        return systemState;
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
                    if (req.getReply().getReturnCode() != Communicator.TRANSIENT_ERROR) {
                        info.setSystemStateVersionAcknowledged(version, false);
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
        if (node.getSystemStateVersionAcknowledged() == systemState.getVersion()) {
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
        if (node.getNewestSystemStateVersionSent() == systemState.getVersion()) {
            return false; // No point in sending if we already have done so
        }
        return true;
    }

    private List<NodeInfo> resolveStateVersionSendSet(DatabaseHandler.Context dbContext) {
        return dbContext.getCluster().getNodeInfo().stream()
                .filter(this::nodeNeedsClusterState)
                .collect(Collectors.toList());
    }

    public boolean broadcastNewState(DatabaseHandler database,
                                     DatabaseHandler.Context dbContext,
                                     Communicator communicator,
                                     FleetController fleetController) throws InterruptedException {
        if (systemState == null) return false;

        List<NodeInfo> recipients = resolveStateVersionSendSet(dbContext);
        if (!systemState.isOfficial()) {
            systemState.setOfficial(true);
        }

        boolean anyOutdatedDistributorNodes = false;
        for (NodeInfo node : recipients) {
            if (node.isDistributor()) {
                anyOutdatedDistributorNodes = true;
            }
            if (nodeNeedsToObserveStartupTimestamps(node)) {
                ClusterState newState = buildModifiedClusterState(dbContext);
                log.log(LogLevel.DEBUG, "Sending modified system state version " + systemState.getVersion()
                        + " to node " + node + ": " + newState);
                communicator.setSystemState(newState, node, waiter);
            } else {
                log.log(LogLevel.DEBUG, "Sending system state version " + systemState.getVersion() + " to node " + node
                        + ". (went down time " + node.getWentDownWithStartTime() + ", node start time " + node.getStartTimestamp() + ")");
                communicator.setSystemState(systemState, node, waiter);
            }
        }

        if (!anyOutdatedDistributorNodes && systemState.getVersion() > lastClusterStateInSync) {
            log.log(LogLevel.DEBUG, "All distributors have newest clusterstate, updating start timestamps in zookeeper and clearing them from cluster state");
            lastClusterStateInSync = systemState.getVersion();
            fleetController.handleAllDistributorsInSync(database, dbContext);
        }
        return !recipients.isEmpty();
    }

    private boolean nodeNeedsToObserveStartupTimestamps(NodeInfo node) {
        return node.getStartTimestamp() != 0 && node.getWentDownWithStartTime() == node.getStartTimestamp();
    }

    private ClusterState buildModifiedClusterState(DatabaseHandler.Context dbContext) {
        ClusterState newState = systemState.clone();
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
