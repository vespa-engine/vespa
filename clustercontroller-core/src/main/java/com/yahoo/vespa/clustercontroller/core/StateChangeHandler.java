// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vdslib.state.State.DOWN;
import static com.yahoo.vdslib.state.State.INITIALIZING;
import static com.yahoo.vdslib.state.State.STOPPING;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;

/**
 * This class gets node state updates and timer events and uses these to decide
 * whether a new cluster state should be generated.
 *
 * TODO refactor logic out into smaller, separate components. Still state duplication
 * between ClusterStateGenerator and StateChangeHandler, especially for temporal
 * state transition configuration parameters.
 */
public class StateChangeHandler {

    private static final Logger log = Logger.getLogger(StateChangeHandler.class.getName());

    private final FleetControllerContext context;
    private final Timer timer;
    private final EventLogInterface eventLog;
    private boolean stateMayHaveChanged = false;
    private boolean isMaster = false;

    private Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
    private int maxInitProgressTime = 5000;
    private int maxPrematureCrashes = 4;
    private long stableStateTimePeriod = 60 * 60 * 1000;
    private int maxSlobrokDisconnectGracePeriod = 1000;
    private static final boolean disableUnstableNodes = true;

    public StateChangeHandler(FleetControllerContext context, Timer timer, EventLogInterface eventLog) {
        this.context = context;
        this.timer = timer;
        this.eventLog = eventLog;
        maxTransitionTime.put(NodeType.DISTRIBUTOR, 5000);
        maxTransitionTime.put(NodeType.STORAGE, 5000);
    }

    public void handleAllDistributorsInSync(final ClusterState currentState,
                                            final Set<ConfiguredNode> nodes,
                                            final DatabaseHandler database,
                                            final DatabaseHandler.DatabaseContext dbContext) {
        int startTimestampsReset = 0;
        context.log(log, FINE, "handleAllDistributorsInSync invoked for state version %d", currentState.getVersion());
        for (NodeType nodeType : NodeType.getTypes()) {
            for (ConfiguredNode configuredNode : nodes) {
                final Node node = new Node(nodeType, configuredNode.index());
                final NodeInfo nodeInfo = dbContext.getCluster().getNodeInfo(node);
                final NodeState nodeState = currentState.getNodeState(node);
                if (nodeInfo != null && nodeState != null) {
                    if (nodeState.getStartTimestamp() > nodeInfo.getStartTimestamp()) {
                        log.log(FINE, () -> String.format("Storing away new start timestamp for node %s (%d)", node, nodeState.getStartTimestamp()));
                        nodeInfo.setStartTimestamp(nodeState.getStartTimestamp());
                    }
                    if (nodeState.getStartTimestamp() > 0) {
                        log.log(FINE, "Resetting timestamp in cluster state for node %s", node);
                        ++startTimestampsReset;
                    }
                } else if (log.isLoggable(FINE)) {
                    log.log(FINE, node + ": " +
                                            (nodeInfo == null ? "null" : nodeInfo.getStartTimestamp()) + ", " +
                                            (nodeState == null ? "null" : nodeState.getStartTimestamp()));
                }
            }
        }
        if (startTimestampsReset > 0) {
            eventLog.add(new ClusterEvent(ClusterEvent.Type.SYSTEMSTATE, "Reset " + startTimestampsReset +
                    " start timestamps as all available distributors have seen newest cluster state.",
                    timer.getCurrentTimeInMillis()));
            stateMayHaveChanged = true;
            database.saveStartTimestamps(dbContext);
        } else {
            log.log(FINE, "Found no start timestamps to reset in cluster state.");
        }
    }

    public boolean stateMayHaveChanged() {
        return stateMayHaveChanged;
    }

    public void setStateChangedFlag() { stateMayHaveChanged = true; }
    public void unsetStateChangedFlag() {
        stateMayHaveChanged = false;
    }

    public void setMaster(boolean isMaster) {
        this.isMaster = isMaster;
    }

    public void setMaxTransitionTime(Map<NodeType, Integer> map) { maxTransitionTime = map; }
    public void setMaxInitProgressTime(int millisecs) { maxInitProgressTime = millisecs; }
    public void setMaxSlobrokDisconnectGracePeriod(int millisecs) {
        maxSlobrokDisconnectGracePeriod = millisecs;
    }
    public void setStableStateTimePeriod(long millisecs) { stableStateTimePeriod = millisecs; }
    public void setMaxPrematureCrashes(int count) { maxPrematureCrashes = count; }

    // TODO nodeListener is only used via updateNodeInfoFromReportedState -> handlePrematureCrash
    // TODO this will recursively invoke proposeNewNodeState, which will presumably (i.e. hopefully) be a no-op...
    public void handleNewReportedNodeState(ClusterState currentClusterState,
                                           NodeInfo node,
                                           NodeState reportedState,
                                           NodeListener nodeListener) {
        NodeState currentState = currentClusterState.getNodeState(node.getNode());
        Level level = (currentState.equals(reportedState) && node.getVersion() == 0) ? FINEST : FINE;
        log.log(level, () -> String.format("Got nodestate reply from %s: %s (Current state is %s)",
                                           node, node.getReportedState().getTextualDifference(reportedState), currentState.toString(true)));
        long currentTime = timer.getCurrentTimeInMillis();

        if (reportedState.getState().equals(DOWN)) {
            node.setTimeOfFirstFailingConnectionAttempt(currentTime);
        }

        // *** LOGGING ONLY
        if ( ! reportedState.similarTo(node.getReportedState())) {
            if (reportedState.getState().equals(DOWN)) {
                eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(node, "Failed to get node state: " + reportedState.toString(true), NodeEvent.Type.REPORTED, currentTime), Level.INFO);
            } else {
                eventLog.addNodeOnlyEvent(NodeEvent.forBaseline(node, "Now reporting state " + reportedState.toString(true), NodeEvent.Type.REPORTED, currentTime), FINE);
            }
        }

        if (reportedState.equals(node.getReportedState()) &&  ! reportedState.getState().equals(INITIALIZING)) {
            return;
        }

        updateNodeInfoFromReportedState(node, currentState, reportedState, nodeListener);

        if (reportedState.getMinUsedBits() != currentState.getMinUsedBits()) {
            int oldCount = currentState.getMinUsedBits();
            int newCount = reportedState.getMinUsedBits();
            log.log(FINE,
                    () -> String.format("Altering node state to reflect that min distribution bit count has changed from %d to %d", oldCount, newCount));
            eventLog.add(NodeEvent.forBaseline(node, String.format("Altered min distribution bit count from %d to %d", oldCount, newCount),
                         NodeEvent.Type.CURRENT, currentTime), isMaster);
        } else {
            log.log(FINE, () -> String.format("Not altering state of %s in cluster state because new state is too similar: %s",
                                              node, currentState.getTextualDifference(reportedState)));
        }

        stateMayHaveChanged = true;
    }

    public void handleNewNode(NodeInfo node) {
        String message = "Found new node " + node + " in slobrok at " + node.getRpcAddress();
        eventLog.add(NodeEvent.forBaseline(node, message, NodeEvent.Type.REPORTED, timer.getCurrentTimeInMillis()), isMaster);
    }

    public void handleMissingNode(ClusterState currentClusterState, NodeInfo node, NodeListener nodeListener) {
        long timeNow = timer.getCurrentTimeInMillis();

        if (node.getLatestNodeStateRequestTime() != null) {
            eventLog.add(NodeEvent.forBaseline(node, "Node is no longer in slobrok, but we still have a pending state request.", NodeEvent.Type.REPORTED, timeNow), isMaster);
        } else {
            eventLog.add(NodeEvent.forBaseline(node, "Node is no longer in slobrok. No pending state request to node.", NodeEvent.Type.REPORTED, timeNow), isMaster);
        }

        if (node.getReportedState().getState().equals(STOPPING)) {
            log.log(FINE, () -> "Node " + node.getNode() + " is no longer in slobrok. Was in stopping state, so assuming it has shut down normally. Setting node down");
            NodeState ns = node.getReportedState().clone();
            ns.setState(DOWN);
            handleNewReportedNodeState(currentClusterState, node, ns.clone(), nodeListener);
        } else {
            log.log(FINE, () -> "Node " + node.getNode() + " no longer in slobrok was in state " + node.getReportedState() + ". Waiting to see if it reappears in slobrok");
        }

        stateMayHaveChanged = true;
    }

    /**
     * Propose a new state for a node. This may happen due to an administrator action, orchestration, or
     * a configuration change.
     *
     * If the newly proposed state differs from the state the node currently has in the system,
     * a cluster state regeneration will be triggered.
     */
    public void proposeNewNodeState(ClusterState currentClusterState, NodeInfo node, NodeState proposedState) {
        NodeState currentState = currentClusterState.getNodeState(node.getNode());

        if (currentState.getState().equals(proposedState.getState()))
            return;

        stateMayHaveChanged = true;

        log.log(FINE, () -> String.format("Got new wanted nodestate for %s: %s", node, currentState.getTextualDifference(proposedState)));
        // Should be checked earlier before state was set in cluster
        assert(proposedState.getState().validWantedNodeState(node.getNode().getType()));
        long timeNow = timer.getCurrentTimeInMillis();
        NodeState currentReported = node.getReportedState();
        if (proposedState.above(currentReported)) {
            eventLog.add(NodeEvent.forBaseline(node, String.format("Wanted state %s, but we cannot force node into that " +
                    "state yet as it is currently in %s", proposedState, currentReported),
                    NodeEvent.Type.REPORTED, timeNow), isMaster);
            return;
        }
        if ( ! proposedState.similarTo(currentState)) {
            eventLog.add(NodeEvent.forBaseline(node, String.format("Node state set to %s.", proposedState),
                    NodeEvent.Type.WANTED, timeNow), isMaster);
        }
    }

    public void handleNewRpcAddress(NodeInfo node) {
        String message = "Node " + node + " has a new address in slobrok: " + node.getRpcAddress();
        eventLog.add(NodeEvent.forBaseline(node, message, NodeEvent.Type.REPORTED, timer.getCurrentTimeInMillis()), isMaster);
    }

    public void handleReturnedRpcAddress(NodeInfo node) {
        String message = "Node got back into slobrok with same address as before: " + node.getRpcAddress();
        eventLog.add(NodeEvent.forBaseline(node, message, NodeEvent.Type.REPORTED, timer.getCurrentTimeInMillis()), isMaster);
    }

    void reconfigureFromOptions(FleetControllerOptions options) {
        setMaxPrematureCrashes(options.maxPrematureCrashes());
        setStableStateTimePeriod(options.stableStateTimePeriod());
        setMaxInitProgressTime(options.maxInitProgressTime());
        setMaxSlobrokDisconnectGracePeriod(options.maxSlobrokDisconnectGracePeriod());
        setMaxTransitionTime(options.maxTransitionTime());
    }

    // TODO too many hidden behavior dependencies between this and the actually
    // generated cluster state. Still a bit of a mine field...
    // TODO remove all node state mutation from this function entirely in favor of ClusterStateGenerator!
    //  `--> this will require adding more event edges and premature crash handling to it. Which is fine.
    public boolean watchTimers(ContentCluster cluster, ClusterState currentClusterState, NodeListener nodeListener) {
        boolean triggeredAnyTimers = false;
        long currentTime = timer.getCurrentTimeInMillis();

        for(NodeInfo node : cluster.getNodeInfos()) {
            triggeredAnyTimers |= handleTimeDependentOpsForNode(currentClusterState, nodeListener, currentTime, node);
        }

        if (triggeredAnyTimers) {
            stateMayHaveChanged = true;
        }
        return triggeredAnyTimers;
    }

    private boolean handleTimeDependentOpsForNode(ClusterState currentClusterState,
                                                  NodeListener nodeListener,
                                                  long currentTime,
                                                  NodeInfo node) {
        NodeState currentStateInSystem = currentClusterState.getNodeState(node.getNode());
        NodeState lastReportedState = node.getReportedState();
        boolean triggeredAnyTimers =
                reportDownIfOutdatedSlobrokNode(currentClusterState, nodeListener, currentTime, node, lastReportedState);

        if (nodeStillUnavailableAfterTransitionTimeExceeded(currentTime, node, currentStateInSystem, lastReportedState))
            triggeredAnyTimers = true;

        if (nodeInitProgressHasTimedOut(currentTime, node, currentStateInSystem, lastReportedState)) {
            eventLog.add(NodeEvent.forBaseline(node, String.format(
                        "%d milliseconds without initialize progress. Marking node down. " +
                        "Premature crash count is now %d.",
                        currentTime - node.getInitProgressTime(),
                        node.getPrematureCrashCount() + 1),
                    NodeEvent.Type.CURRENT, currentTime), isMaster);
            handlePrematureCrash(node, nodeListener);
            triggeredAnyTimers = true;
        }

        if (mayResetCrashCounterOnStableUpNode(currentTime, node, lastReportedState)) {
            node.setPrematureCrashCount(0);
            log.log(FINE, () -> "Resetting premature crash count on node " + node + " as it has been up for a long time.");
            triggeredAnyTimers = true;
        } else if (mayResetCrashCounterOnStableDownNode(currentTime, node, lastReportedState)) {
            node.setPrematureCrashCount(0);
            log.log(FINE, () -> "Resetting premature crash count on node " + node + " as it has been down for a long time.");
            triggeredAnyTimers = true;
        }

        return triggeredAnyTimers;
    }

    private boolean nodeInitProgressHasTimedOut(long currentTime, NodeInfo node, NodeState currentStateInSystem, NodeState lastReportedState) {
        return !currentStateInSystem.getState().equals(DOWN)
            && node.getWantedState().above(new NodeState(node.getNode().getType(), DOWN))
            && lastReportedState.getState().equals(INITIALIZING)
            && maxInitProgressTime != 0
            && node.getInitProgressTime() + maxInitProgressTime <= currentTime
            && node.getNode().getType().equals(NodeType.STORAGE);
    }

    // TODO: Merge this and the below method
    private boolean mayResetCrashCounterOnStableDownNode(long currentTime, NodeInfo node, NodeState lastReportedState) {
        return node.getDownStableStateTime() + stableStateTimePeriod <= currentTime
            && lastReportedState.getState().equals(DOWN)
            && node.getPrematureCrashCount() <= maxPrematureCrashes
            && node.getPrematureCrashCount() != 0;
    }

    private boolean mayResetCrashCounterOnStableUpNode(long currentTime, NodeInfo node, NodeState lastReportedState) {
        return node.getUpStableStateTime() + stableStateTimePeriod <= currentTime
            && lastReportedState.getState().equals(State.UP)
            && node.getPrematureCrashCount() <= maxPrematureCrashes
            && node.getPrematureCrashCount() != 0;
    }

    private boolean nodeStillUnavailableAfterTransitionTimeExceeded(
            long currentTime,
            NodeInfo node,
            NodeState currentStateInSystem,
            NodeState lastReportedState)
    {
        return currentStateInSystem.getState().equals(State.MAINTENANCE)
            && node.getWantedState().above(new NodeState(node.getNode().getType(), DOWN))
            && (lastReportedState.getState().equals(DOWN) || node.isNotInSlobrok())
            && node.getTransitionTime() + maxTransitionTime.get(node.getNode().getType()) < currentTime;
    }

    private boolean reportDownIfOutdatedSlobrokNode(ClusterState currentClusterState,
                                                    NodeListener nodeListener,
                                                    long currentTime,
                                                    NodeInfo node,
                                                    NodeState lastReportedState)
    {
        if (node.isNotInSlobrok()
            && !lastReportedState.getState().equals(DOWN)
            && node.lastSeenInSlobrok() + maxSlobrokDisconnectGracePeriod <= currentTime)
        {
            final String desc = String.format(
                    "Set node down as it has been out of slobrok for %d ms which " +
                    "is more than the max limit of %d ms.",
                    currentTime - node.lastSeenInSlobrok(),
                    maxSlobrokDisconnectGracePeriod);
            node.abortCurrentNodeStateRequests();
            NodeState state = lastReportedState.clone();
            state.setState(DOWN);
            if (!state.hasDescription()) {
                state.setDescription(desc);
            }
            eventLog.add(NodeEvent.forBaseline(node, desc, NodeEvent.Type.CURRENT, currentTime), isMaster);
            handleNewReportedNodeState(currentClusterState, node, state.clone(), nodeListener);
            node.setReportedState(state, currentTime);
            return true;
        }
        return false;
    }

    private boolean isNotControlledShutdown(NodeState state) { return ! isControlledShutdown(state); }

    private boolean isControlledShutdown(NodeState state) {
        return state.getState() == State.STOPPING
                && List.of("Received signal 15 (SIGTERM - Termination signal)", "controlled shutdown")
                       .contains(state.getDescription());
    }

    /**
     * Modify a node's cross-state information in the cluster based on a newly arrived reported state.
     *
     * @param  node the node we are computing the state of
     * @param  currentState the current state of the node
     * @param  reportedState the new state reported by (or, in the case of down - inferred from) the node
     * @param  nodeListener this listener is notified for some of the system state changes that this will return
     */
    private void updateNodeInfoFromReportedState(final NodeInfo node,
                                                 final NodeState currentState,
                                                 final NodeState reportedState,
                                                 final NodeListener nodeListener) {
        final long timeNow = timer.getCurrentTimeInMillis();
        log.log(FINE, () -> String.format("Finding new cluster state entry for %s switching state %s", node, currentState.getTextualDifference(reportedState)));

        if (handleReportedNodeCrashEdge(node, currentState, reportedState, nodeListener, timeNow)) {
            return;
        }
        if (initializationProgressHasIncreased(currentState, reportedState)) {
            node.setInitProgressTime(timeNow);
            log.log(FINEST, () -> "Reset initialize timer on " + node + " to " + node.getInitProgressTime());
        }
        if (handleImplicitCrashEdgeFromReverseInitProgress(node, currentState, reportedState, nodeListener, timeNow)) {
            return;
        }
        markNodeUnstableIfDownEdgeDuringInit(node, currentState, reportedState, nodeListener, timeNow);
    }

    // If we go down while initializing, mark node unstable, such that we don't mark it initializing again before it is up.
    private void markNodeUnstableIfDownEdgeDuringInit(final NodeInfo node,
                                                      final NodeState currentState,
                                                      final NodeState reportedState,
                                                      final NodeListener nodeListener,
                                                      final long timeNow) {
        if (currentState.getState().equals(INITIALIZING)
                && reportedState.getState().oneOf("ds")
                && isNotControlledShutdown(reportedState))
        {
            eventLog.add(NodeEvent.forBaseline(node, String.format("Stop or crash during initialization. " +
                    "Premature crash count is now %d.", node.getPrematureCrashCount() + 1),
                    NodeEvent.Type.CURRENT, timeNow), isMaster);
            handlePrematureCrash(node, nodeListener);
        }
    }

    // TODO do we need this when we have startup timestamps? at least it's unit tested.
    // TODO this seems fairly contrived...
    // If we get reverse initialize progress, mark node unstable, such that we don't mark it initializing again before it is up.
    private boolean handleImplicitCrashEdgeFromReverseInitProgress(final NodeInfo node,
                                                                   final NodeState currentState,
                                                                   final NodeState reportedState,
                                                                   final NodeListener nodeListener,
                                                                   final long timeNow) {
        if (currentState.getState().equals(INITIALIZING) &&
            (reportedState.getState().equals(INITIALIZING) && reportedState.getInitProgress() < currentState.getInitProgress()))
        {
            eventLog.add(NodeEvent.forBaseline(node, String.format(
                        "Stop or crash during initialization detected from reverse initializing progress." +
                        " Progress was %g but is now %g. Premature crash count is now %d.",
                        currentState.getInitProgress(), reportedState.getInitProgress(),
                        node.getPrematureCrashCount() + 1),
                    NodeEvent.Type.CURRENT, timeNow), isMaster);
            node.setRecentlyObservedUnstableDuringInit(true);
            handlePrematureCrash(node, nodeListener);
            return true;
        }
        return false;
    }

    private boolean handleReportedNodeCrashEdge(NodeInfo node, NodeState currentState,
                                                NodeState reportedState, NodeListener nodeListener,
                                                long timeNow) {
        if (nodeUpToDownEdge(node, currentState, reportedState)) {
            node.setTransitionTime(timeNow);
            if (node.getUpStableStateTime() + stableStateTimePeriod > timeNow && isNotControlledShutdown(reportedState)) {
                log.log(FINE, () -> "Stable state: " + node.getUpStableStateTime() + " + " + stableStateTimePeriod + " > " + timeNow);
                eventLog.add(NodeEvent.forBaseline(node,
                        String.format("Stopped or possibly crashed after %d ms, which is before " +
                                      "stable state time period. Premature crash count is now %d.",
                                timeNow - node.getUpStableStateTime(), node.getPrematureCrashCount() + 1),
                        NodeEvent.Type.CURRENT,
                        timeNow), isMaster);
                return handlePrematureCrash(node, nodeListener);
            }
        }
        return false;
    }

    private boolean initializationProgressHasIncreased(NodeState currentState, NodeState reportedState) {
        return reportedState.getState().equals(INITIALIZING) &&
            (!currentState.getState().equals(INITIALIZING) ||
             reportedState.getInitProgress() > currentState.getInitProgress());
    }

    private boolean nodeUpToDownEdge(NodeInfo node, NodeState currentState, NodeState reportedState) {
        return currentState.getState().oneOf("ur") && reportedState.getState().oneOf("dis")
            && (node.getWantedState().getState().equals(State.RETIRED) || !reportedState.getState().equals(INITIALIZING));
    }

    private boolean handlePrematureCrash(NodeInfo node, NodeListener changeListener) {
        node.setPrematureCrashCount(node.getPrematureCrashCount() + 1);
        if (disableUnstableNodes && node.getPrematureCrashCount() > maxPrematureCrashes) {
            NodeState wantedState = new NodeState(node.getNode().getType(), DOWN)
                    .setDescription("Disabled by fleet controller as it prematurely shut down " + node.getPrematureCrashCount() + " times in a row");
            NodeState oldState = node.getWantedState();
            node.setWantedState(wantedState);
            if ( ! oldState.equals(wantedState)) {
                changeListener.handleNewWantedNodeState(node, wantedState);
            }
            return true;
        }
        return false;
    }

}
