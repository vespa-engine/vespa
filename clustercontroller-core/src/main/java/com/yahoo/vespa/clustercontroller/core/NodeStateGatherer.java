// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Target;
import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Collects the state of all nodes by making remote requests and handling the replies.
 */
public class NodeStateGatherer {

    public static final Logger log = Logger.getLogger(NodeStateGatherer.class.getName());

    private final Object monitor;
    private final Timer timer;
    private final List<GetNodeStateRequest> replies = new LinkedList<>();

    private class NodeStateWaiter implements Communicator.Waiter<GetNodeStateRequest> {
        @Override
        public void done(GetNodeStateRequest reply) {
            synchronized (monitor) {
                replies.add(reply);
                monitor.notifyAll();
            }
        }
    }

    private final NodeStateWaiter waiter = new NodeStateWaiter();

    private final EventLog eventLog;
    private int maxSlobrokDisconnectGracePeriod = 1000;
    private long nodeStateRequestTimeoutMS = 10 * 1000;

    public NodeStateGatherer(Object monitor, Timer timer, EventLog log) {
        this.monitor = monitor;
        this.timer = timer;
        this.eventLog = log;
    }

    public void setMaxSlobrokDisconnectGracePeriod(int millisecs) { maxSlobrokDisconnectGracePeriod = millisecs; }

    public void setNodeStateRequestTimeout(long millisecs) { nodeStateRequestTimeoutMS = millisecs; }

    /**
     * Sends state requests to nodes that does not have one pending and is due
     * for another attempt.
     */
    public boolean sendMessages(ContentCluster cluster, Communicator communicator, NodeStateOrHostInfoChangeHandler listener) {
        boolean sentAnyMessages = false;
        long currentTime = timer.getCurrentTimeInMillis();
        for (NodeInfo info : cluster.getNodeInfo()) {
            Long requestTime = info.getLatestNodeStateRequestTime();

            if (requestTime != null && (currentTime - requestTime < nodeStateRequestTimeoutMS)) continue; // pending request
            if (info.getTimeForNextStateRequestAttempt() > currentTime) continue; // too early

            if (info.getRpcAddress() == null || info.isRpcAddressOutdated()) { // Cannot query state of node without RPC address
                log.log(LogLevel.DEBUG, "Not sending getNodeState request to node " + info.getNode() + ": Not in slobrok");
                NodeState reportedState = info.getReportedState().clone();
                if (( ! reportedState.getState().equals(State.DOWN) && currentTime - info.getRpcAddressOutdatedTimestamp() > maxSlobrokDisconnectGracePeriod)
                    || reportedState.getState().equals(State.STOPPING)) // Don't wait for grace period if we expect node to be stopping
                {
                    log.log(LogLevel.DEBUG, "Setting reported state to DOWN "
                            + (reportedState.getState().equals(State.STOPPING)
                                ? "as node completed stopping."
                                : "as node has been out of slobrok longer than " + maxSlobrokDisconnectGracePeriod + "."));
                    if (reportedState.getState().oneOf("iur") || ! reportedState.hasDescription()) {
                        StringBuilder sb = new StringBuilder().append("Set node down as it has been out of slobrok for ")
                                .append(currentTime - info.getRpcAddressOutdatedTimestamp()).append(" ms which is more than the max limit of ")
                                .append(maxSlobrokDisconnectGracePeriod).append(" ms.");
                        reportedState.setDescription(sb.toString());
                    }
                    reportedState.setState(State.DOWN);
                    listener.handleNewNodeState(info, reportedState.clone());
                }
                info.setReportedState(reportedState, currentTime); // Must reset it to null to get connection attempts counted
                continue;
            }

            communicator.getNodeState(info, waiter);
            sentAnyMessages = true;
        }
        return sentAnyMessages;
    }

    /** Reads replies to get node state requests and create events. */
    public boolean processResponses(NodeStateOrHostInfoChangeHandler listener) {
        boolean processedAnyResponses = false;
        long currentTime = timer.getCurrentTimeInMillis();
        synchronized(monitor) {
            for(GetNodeStateRequest req : replies) {
                processedAnyResponses = true;
                NodeInfo info = req.getNodeInfo();

                if (!info.isPendingGetNodeStateRequest(req)) {
                    log.log(LogLevel.DEBUG, "Ignoring getnodestate response from " + info.getNode()
                            + " as request replied to is not the most recent pending request.");
                    continue;
                }

                info.removePendingGetNodeStateRequest(req);

                GetNodeStateRequest.Reply reply = req.getReply();

                if (reply.isError()) {
                    if (reply.getReturnCode() != ErrorCode.ABORT) {
                        NodeState newState = handleError(req, info, currentTime);
                        if (newState != null) {
                            listener.handleNewNodeState(info, newState.clone());
                            info.setReportedState(newState, currentTime);
                        } else {
                            log.log(LogLevel.DEBUG, "Ignoring get node state error. Need to resend");
                        }
                    } else {
                        log.log(LogLevel.DEBUG, "Ignoring getnodestate response from " + info.getNode() + " as it was aborted by client");
                    }

                    continue;
                }

                try {
                    NodeState state = NodeState.deserialize(info.getNode().getType(), reply.getStateString());

                    // For version 0 responses, we poll, so we likely have not altered the state
                    if ( ! state.equals(info.getReportedState()))
                        listener.handleNewNodeState(info, state.clone());
                    info.setReportedState(state, currentTime);
                } catch (Exception e) {
                    log.log(LogLevel.WARNING, "Failed to process get node state response", e);
                    info.setReportedState(new NodeState(info.getNode().getType(), State.DOWN), currentTime);
                }

                // Important: The old host info should be accessible in info.getHostInfo(), see interface.
                // Therefore, setHostInfo() must be called AFTER handleUpdatedHostInfo().
                HostInfo hostInfo = HostInfo.createHostInfo(reply.getHostInfo());
                listener.handleUpdatedHostInfo(info, hostInfo);
                info.setHostInfo(hostInfo);

            }
            replies.clear();
        }
        return processedAnyResponses;
    }

    private NodeState handleError(GetNodeStateRequest req, NodeInfo info, long currentTime) {
        String prefix = "Failed get node state request: ";
        NodeState newState = new NodeState(info.getNode().getType(), State.DOWN);
        if (req.getReply().getReturnCode() == ErrorCode.TIMEOUT) {
            String msg = "RPC timeout";
            if (info.getReportedState().getState().oneOf("ui")) {
                eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(info, prefix + "RPC timeout talking to node.", NodeEvent.Type.REPORTED, currentTime), LogLevel.INFO);
            } else if (!info.getReportedState().hasDescription() || !info.getReportedState().getDescription().equals(msg)) {
                log.log(LogLevel.DEBUG, "Failed to talk to node " + info + ": " + req.getReply().getReturnCode() + " " + req.getReply().getReturnMessage() + ": " + msg);
            }
            newState.setDescription(msg);
        } else if (req.getReply().getReturnCode() == ErrorCode.CONNECTION) {
            Target target = info.lastRequestInfoConnection;
            Exception reason = (target == null ? null : target.getConnectionLostReason());
            if (reason != null) {
                String msg = reason.getMessage();
                if (msg == null) msg = "(null)";
                newState.setDescription(msg);
                if (msg.equals("Connection refused")) {
                    msg = "Connection error: Connection refused";
                    if (info.getReportedState().getState().oneOf("ui")) {
                        eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(info, prefix + msg, NodeEvent.Type.REPORTED, currentTime), LogLevel.INFO);
                    } else if (!info.getReportedState().hasDescription() || !info.getReportedState().getDescription().equals(msg)) {
                        log.log(LogLevel.DEBUG, "Failed to talk to node " + info + ": " + req.getReply().getReturnCode()
                                + " " + req.getReply().getReturnMessage() + ": " + msg);
                    }
                    newState.setState(State.DOWN);
                } else if (msg.equals("jrt: Connection closed by peer") || msg.equals("Connection reset by peer")) {
                    msg = "Connection error: Closed at other end. (Node or switch likely shut down)";
                    if (info.isRpcAddressOutdated()) {
                        msg += " Node is no longer in slobrok.";
                    }
                    if (info.getReportedState().getState().oneOf("ui")) {
                        eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(info, prefix + msg, NodeEvent.Type.REPORTED, currentTime), LogLevel.INFO);
                    } else if (!info.getReportedState().hasDescription() || !info.getReportedState().getDescription().equals(msg)) {
                        log.log(LogLevel.DEBUG, "Failed to talk to node " + info + ": " + req.getReply().getReturnCode() + " " + req.getReply().getReturnMessage() + ": " + msg);
                    }
                    newState.setState(State.DOWN).setDescription(msg);
                } else if (msg.equals("Connection timed out")) {
                    if (info.getReportedState().getState().oneOf("ui")) {
                        msg = "Connection error: Timeout";
                        eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(info, prefix + msg, NodeEvent.Type.REPORTED, currentTime), LogLevel.INFO);
                    } else {
                        log.log(LogLevel.DEBUG, "Failed to talk to node " + info + ": " + req.getReply().getReturnCode() + " " + req.getReply().getReturnMessage() + ": " + msg);
                    }
                } else {
                    msg = "Connection error: " + reason;
                    if (info.getReportedState().getState().oneOf("ui")) {
                        eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(info, prefix + msg, NodeEvent.Type.REPORTED, currentTime), LogLevel.WARNING);
                    } else if (!info.getReportedState().hasDescription() || !info.getReportedState().getDescription().equals(msg)) {
                        log.log(LogLevel.DEBUG, "Failed to talk to node " + info + ": " + req.getReply().getReturnCode() + " " + req.getReply().getReturnMessage() + ": " + msg);
                    }
                    newState.setDescription(msg);
                }
            } else {
                String msg = "Connection error: Unexpected error with no reason set. Assuming it is a network issue: " +
                        req.getReply().getReturnCode() + ": " + req.getReply().getReturnMessage();

                if (info.getReportedState().getState().oneOf("ui")) {
                    eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(info, prefix + msg, NodeEvent.Type.REPORTED, currentTime), LogLevel.WARNING);
                } else if (!info.getReportedState().hasDescription() || !info.getReportedState().getDescription().equals(msg)) {
                    log.log(LogLevel.DEBUG, "Failed to talk to node " + info + ": " + req.getReply().getReturnCode() + " " + req.getReply().getReturnMessage() + ": " + msg);
                }
                newState.setDescription(msg);
            }
        } else if (req.getReply().getReturnCode() == Communicator.TRANSIENT_ERROR) {
            return null;
        } else if (req.getReply().getReturnCode() == ErrorCode.NO_SUCH_METHOD) {
            String msg = "no such RPC method error";
            if (info.getReportedState().getState().oneOf("ui")) {
                eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(info, prefix + msg, NodeEvent.Type.REPORTED, currentTime), LogLevel.WARNING);
            } else if (!info.getReportedState().hasDescription() || !info.getReportedState().getDescription().equals(msg)) {
                log.log(LogLevel.DEBUG, "Failed to talk to node " + info + ": " + req.getReply().getReturnCode() + " " + req.getReply().getReturnMessage() + ": " + msg);
            }
            newState.setState(State.DOWN).setDescription(msg + ": get node state");
        } else if (req.getReply().getReturnCode() == 75004) {
            String msg = "Node refused to answer RPC request and is likely stopping: " + req.getReply().getReturnMessage();
                // The node is shutting down and is not accepting requests from anyone
            if (info.getReportedState().getState().equals(State.STOPPING)) {
                log.log(LogLevel.DEBUG, "Failed to get node state from " + info + " because it is still shutting down.");
            } else {
                if (info.getReportedState().getState().oneOf("ui")) {
                    eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(info, prefix + msg, NodeEvent.Type.REPORTED, currentTime), LogLevel.INFO);
                } else if (!info.getReportedState().hasDescription() || !info.getReportedState().getDescription().equals(msg)) {
                    log.log(LogLevel.DEBUG, "Failed to talk to node " + info + ": " + req.getReply().getReturnCode() + " " + req.getReply().getReturnMessage() + ": " + msg);
                }
            }
            newState.setState(State.STOPPING).setDescription(msg);
        } else {
            String msg = "Got unexpected error, assumed to be node issue " + req.getReply().getReturnCode() + ": " + req.getReply().getReturnMessage();
            if (info.getReportedState().getState().oneOf("ui")) {
                eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(info, prefix + msg, NodeEvent.Type.REPORTED, currentTime), LogLevel.WARNING);
            } else if (!info.getReportedState().hasDescription() || !info.getReportedState().getDescription().equals(msg)) {
                log.log(LogLevel.DEBUG, "Failed to talk to node " + info + ": " + req.getReply().getReturnCode() + " " + req.getReply().getReturnMessage() + ": " + msg);
            }
            newState.setState(State.DOWN).setDescription(msg);
        }
        return newState;
    }

}
