// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.Spec;
import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;

import java.util.*;
import java.util.logging.Logger;

/**
 * This class gets node state updates and timer events and uses these to decide
 * whether a new cluster state should be generated.
 *
 * TODO refactor logic out into smaller, separate components. Still state duplication
 * between ClusterStateGenerator and StateChangeHandler, especially for temporal
 * state transition configuration parameters.
 */
public class StateChangeHandler {

    private static Logger log = Logger.getLogger(StateChangeHandler.class.getName());

    private final Timer timer;
    private final EventLogInterface eventLog;
    private boolean stateMayHaveChanged = false;
    private boolean isMaster = false;

    private Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
    private int maxInitProgressTime = 5000;
    private int maxPrematureCrashes = 4;
    private long stableStateTimePeriod = 60 * 60 * 1000;
    private Map<Integer, String> hostnames = new HashMap<>();
    private int maxSlobrokDisconnectGracePeriod = 1000;
    private static final boolean disableUnstableNodes = true;

    /**
     * @param metricUpdater may be null, in which case no metrics will be recorded.
     */
    public StateChangeHandler(Timer timer, EventLogInterface eventLog, MetricUpdater metricUpdater) {
        this.timer = timer;
        this.eventLog = eventLog;
        maxTransitionTime.put(NodeType.DISTRIBUTOR, 5000);
        maxTransitionTime.put(NodeType.STORAGE, 5000);
    }

    public void handleAllDistributorsInSync(final ClusterState currentState,
                                            final Set<ConfiguredNode> nodes,
                                            final DatabaseHandler database,
                                            final DatabaseHandler.Context dbContext) throws InterruptedException {
        int startTimestampsReset = 0;
        log.log(LogLevel.DEBUG, String.format("handleAllDistributorsInSync invoked for state version %d", currentState.getVersion()));
        for (NodeType nodeType : NodeType.getTypes()) {
            for (ConfiguredNode configuredNode : nodes) {
                final Node node = new Node(nodeType, configuredNode.index());
                final NodeInfo nodeInfo = dbContext.getCluster().getNodeInfo(node);
                final NodeState nodeState = currentState.getNodeState(node);
                if (nodeInfo != null && nodeState != null) {
                    if (nodeState.getStartTimestamp() > nodeInfo.getStartTimestamp()) {
                        log.log(LogLevel.DEBUG, String.format("Storing away new start timestamp for node %s (%d)",
                                node, nodeState.getStartTimestamp()));
                        nodeInfo.setStartTimestamp(nodeState.getStartTimestamp());
                    }
                    if (nodeState.getStartTimestamp() > 0) {
                        log.log(LogLevel.DEBUG, String.format("Resetting timestamp in cluster state for node %s", node));
                        ++startTimestampsReset;
                    }
                } else {
                    log.log(LogLevel.DEBUG, node + ": " +
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
            log.log(LogLevel.DEBUG, "Found no start timestamps to reset in cluster state.");
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

    // TODO nodeListener is only used via decideNodeStateGivenReportedState -> handlePrematureCrash
    // TODO this will recursively invoke proposeNewNodeState, which will presumably (i.e. hopefully) be a no-op...
    public void handleNewReportedNodeState(final ClusterState currentClusterState,
                                           final NodeInfo node,
                                           final NodeState reportedState,
                                           final NodeStateOrHostInfoChangeHandler nodeListener)
    {
        final NodeState currentState = currentClusterState.getNodeState(node.getNode());
        log.log(currentState.equals(reportedState) && node.getVersion() == 0 ? LogLevel.SPAM : LogLevel.DEBUG,
                "Got nodestate reply from " + node + ": "
                + node.getReportedState().getTextualDifference(reportedState) + " (Current state is " + currentState.toString(true) + ")");
        final long currentTime = timer.getCurrentTimeInMillis();

        if (reportedState.getState().equals(State.DOWN)) {
            node.setTimeOfFirstFailingConnectionAttempt(currentTime);
        }
        // FIXME only set if reported state has actually changed...
        stateMayHaveChanged = true;

        // *** LOGGING ONLY
        if ( ! reportedState.similarTo(node.getReportedState())) {
            if (reportedState.getState().equals(State.DOWN)) {
                eventLog.addNodeOnlyEvent(new NodeEvent(node, "Failed to get node state: " + reportedState.toString(true), NodeEvent.Type.REPORTED, currentTime), LogLevel.INFO);
            } else {
                eventLog.addNodeOnlyEvent(new NodeEvent(node, "Now reporting state " + reportedState.toString(true), NodeEvent.Type.REPORTED, currentTime), LogLevel.DEBUG);
            }
        }

        if (reportedState.equals(node.getReportedState()) &&  ! reportedState.getState().equals(State.INITIALIZING)) {
            return;
        }

        final NodeState alteredState = decideNodeStateGivenReportedState(node, currentState, reportedState, nodeListener);
        if (alteredState != null) {
            // TODO figure out what to do with init progress vs. new states.
            // TODO .. simply don't include init progress in generated states? are they used anywhere? it's already in reported state
            if (alteredState.getMinUsedBits() != currentState.getMinUsedBits()) {
                log.log(LogLevel.DEBUG, "Altering node state to reflect that min distribution bit count has changed from "
                                        + currentState.getMinUsedBits() + " to " + alteredState.getMinUsedBits());
                int oldCount = currentState.getMinUsedBits();
                eventLog.add(new NodeEvent(node, "Altered min distribution bit count from " + oldCount
                                        + " to " + alteredState.getMinUsedBits(), NodeEvent.Type.CURRENT, currentTime), isMaster);
                stateMayHaveChanged = true;
            } else {
                log.log(LogLevel.DEBUG, "Not altering state of " + node + " in cluster state because new state is too similar: "
                        + currentState.getTextualDifference(alteredState));
            }
        }
    }

    public void handleNewNode(NodeInfo node) {
        setHostName(node);
        String message = "Found new node " + node + " in slobrok at " + node.getRpcAddress();
        eventLog.add(new NodeEvent(node, message, NodeEvent.Type.REPORTED, timer.getCurrentTimeInMillis()), isMaster);
    }


    // TODO move to node state change handler
    public void handleMissingNode(final ClusterState currentClusterState,
                                  final NodeInfo node,
                                  final NodeStateOrHostInfoChangeHandler nodeListener)
    {
        removeHostName(node);

        final long timeNow = timer.getCurrentTimeInMillis();

        if (node.getLatestNodeStateRequestTime() != null) {
            eventLog.add(new NodeEvent(node, "Node is no longer in slobrok, but we still have a pending state request.", NodeEvent.Type.REPORTED, timeNow), isMaster);
        } else {
            eventLog.add(new NodeEvent(node, "Node is no longer in slobrok. No pending state request to node.", NodeEvent.Type.REPORTED, timeNow), isMaster);
        }

        if (node.getReportedState().getState().equals(State.STOPPING)) {
            log.log(LogLevel.DEBUG, "Node " + node.getNode() + " is no longer in slobrok. Was in stopping state, so assuming it has shut down normally. Setting node down");
            NodeState ns = node.getReportedState().clone();
            ns.setState(State.DOWN);
            handleNewReportedNodeState(currentClusterState, node, ns.clone(), nodeListener);
        } else {
            log.log(LogLevel.DEBUG, "Node " + node.getNode() + " no longer in slobrok was in state " + node.getReportedState() + ". Waiting to see if it reappears in slobrok");
        }

        stateMayHaveChanged = true;
    }

    /**
     * Propose a new state for a node. This may happen due to an administrator action, orchestration, or
     * a configuration change.
     *
     * TODO this is currently really just to trigger (event) logging and state re-gen
     */
    public void proposeNewNodeState(final ClusterState currentClusterState, final NodeInfo node, final NodeState proposedState) {
        final NodeState currentState = currentClusterState.getNodeState(node.getNode());
        final NodeState currentReported = node.getReportedState(); // TODO: Is there a reason to have both of this and the above?

        final NodeState newCurrentState = currentReported.clone();

        newCurrentState.setState(proposedState.getState()).setDescription(proposedState.getDescription());

        if (currentState.getState().equals(newCurrentState.getState())) {
            return;
        }
        stateMayHaveChanged = true;

        log.log(LogLevel.DEBUG, "Got new wanted nodestate for " + node + ": " + currentState.getTextualDifference(proposedState));
        // Should be checked earlier before state was set in cluster
        assert(newCurrentState.getState().validWantedNodeState(node.getNode().getType()));
        long timeNow = timer.getCurrentTimeInMillis();
        if (newCurrentState.above(currentReported)) {
            eventLog.add(new NodeEvent(node, "Wanted state " + newCurrentState + ", but we cannot force node into that state yet as it is currently in " + currentReported, NodeEvent.Type.REPORTED, timeNow), isMaster);
            return;
        }
        if ( ! newCurrentState.similarTo(currentState)) {
            eventLog.add(new NodeEvent(node, "Node state set to " + newCurrentState + ".", NodeEvent.Type.WANTED, timeNow), isMaster);
        }
    }

    public void handleNewRpcAddress(NodeInfo node) {
        setHostName(node);
        String message = "Node " + node + " has a new address in slobrok: " + node.getRpcAddress();
        eventLog.add(new NodeEvent(node, message, NodeEvent.Type.REPORTED, timer.getCurrentTimeInMillis()), isMaster);
    }

    public void handleReturnedRpcAddress(NodeInfo node) {
        setHostName(node);
        String message = "Node got back into slobrok with same address as before: " + node.getRpcAddress();
        eventLog.add(new NodeEvent(node, message, NodeEvent.Type.REPORTED, timer.getCurrentTimeInMillis()), isMaster);
    }

    private void setHostName(NodeInfo node) {
        String rpcAddress = node.getRpcAddress();
        if (rpcAddress == null) {
            // This may happen if we haven't seen the node in Slobrok yet.
            return;
        }

        Spec address = new Spec(rpcAddress);
        if (address.malformed()) {
            return;
        }

        hostnames.put(node.getNodeIndex(), address.host());
    }

    void reconfigureFromOptions(FleetControllerOptions options) {
        setMaxPrematureCrashes(options.maxPrematureCrashes);
        setStableStateTimePeriod(options.stableStateTimePeriod);
        setMaxInitProgressTime(options.maxInitProgressTime);
        setMaxSlobrokDisconnectGracePeriod(options.maxSlobrokDisconnectGracePeriod);
        setMaxTransitionTime(options.maxTransitionTime);
    }

    private void removeHostName(NodeInfo node) {
        hostnames.remove(node.getNodeIndex());
    }

    Map<Integer, String> getHostnames() {
        return Collections.unmodifiableMap(hostnames);
    }

    // TODO refactor like a boss
    public boolean watchTimers(final ContentCluster cluster,
                               final ClusterState currentClusterState,
                               final NodeStateOrHostInfoChangeHandler nodeListener)
    {
        boolean triggeredAnyTimers = false;
        final long currentTime = timer.getCurrentTimeInMillis();
        for(NodeInfo node : cluster.getNodeInfo()) {
            final NodeState currentStateInSystem = currentClusterState.getNodeState(node.getNode());
            final NodeState lastReportedState = node.getReportedState();

            triggeredAnyTimers = reportDownIfOutdatedSlobrokNode(
                    currentClusterState, nodeListener, currentTime, node, lastReportedState);

            if (nodeStillUnavailableAfterTransitionTimeExceeded(
                    currentTime, node, currentStateInSystem, lastReportedState))
            {
                eventLog.add(new NodeEvent(node, (currentTime - node.getTransitionTime())
                        + " milliseconds without contact. Marking node down.",
                        NodeEvent.Type.CURRENT, currentTime), isMaster);
                triggeredAnyTimers = true;
            }

            // TODO should we handle in baseline? handlePrematureCrash sets wanted state, so might not be needed
            // TODO ---> yes, always want to set it down/maintenance even if #max crash has not been reached
            // If node hasn't increased its initializing progress within initprogresstime, mark it down.
            if (!currentStateInSystem.getState().equals(State.DOWN)
                && node.getWantedState().above(new NodeState(node.getNode().getType(), State.DOWN))
                && lastReportedState.getState().equals(State.INITIALIZING)
                && maxInitProgressTime != 0
                && node.getInitProgressTime() + maxInitProgressTime <= currentTime
                && node.getNode().getType().equals(NodeType.STORAGE))
            {
                eventLog.add(new NodeEvent(node, (currentTime - node.getInitProgressTime()) + " milliseconds "
                        + "without initialize progress. Marking node down."
                        + " Premature crash count is now " + (node.getPrematureCrashCount() + 1) + ".", NodeEvent.Type.CURRENT, currentTime), isMaster);
                handlePrematureCrash(node, nodeListener);
                triggeredAnyTimers = true;
            }

            if (mayResetCrashCounterOnStableUpNode(currentTime, node, lastReportedState)) {
                node.setPrematureCrashCount(0);
                log.log(LogLevel.DEBUG, "Resetting premature crash count on node " + node + " as it has been up for a long time.");
                triggeredAnyTimers = true;
            } else if (mayResetCrashCounterOnStableDownNode(currentTime, node, lastReportedState)) {
                node.setPrematureCrashCount(0);
                log.log(LogLevel.DEBUG, "Resetting premature crash count on node " + node + " as it has been down for a long time.");
                triggeredAnyTimers = true;
            }
        }

        if (triggeredAnyTimers) {
            stateMayHaveChanged = true;
        }
        return triggeredAnyTimers;
    }

    private boolean mayResetCrashCounterOnStableDownNode(long currentTime, NodeInfo node, NodeState lastReportedState) {
        return node.getDownStableStateTime() + stableStateTimePeriod <= currentTime
            && lastReportedState.getState().equals(State.DOWN)
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
            && node.getWantedState().above(new NodeState(node.getNode().getType(), State.DOWN))
            && (lastReportedState.getState().equals(State.DOWN) || node.isRpcAddressOutdated())
            && node.getTransitionTime() + maxTransitionTime.get(node.getNode().getType()) < currentTime;
    }

    private boolean reportDownIfOutdatedSlobrokNode(ClusterState currentClusterState,
                                                    NodeStateOrHostInfoChangeHandler nodeListener,
                                                    long currentTime,
                                                    NodeInfo node,
                                                    NodeState lastReportedState)
    {
        if (node.isRpcAddressOutdated()
            && !lastReportedState.getState().equals(State.DOWN)
            && node.getRpcAddressOutdatedTimestamp() + maxSlobrokDisconnectGracePeriod <= currentTime)
        {
            StringBuilder sb = new StringBuilder().append("Set node down as it has been out of slobrok for ")
                    .append(currentTime - node.getRpcAddressOutdatedTimestamp()).append(" ms which is more than the max limit of ")
                    .append(maxSlobrokDisconnectGracePeriod).append(" ms.");
            node.abortCurrentNodeStateRequests();
            NodeState state = lastReportedState.clone();
            state.setState(State.DOWN);
            if (!state.hasDescription()) state.setDescription(sb.toString());
            eventLog.add(new NodeEvent(node, sb.toString(), NodeEvent.Type.CURRENT, currentTime), isMaster);
            handleNewReportedNodeState(currentClusterState, node, state.clone(), nodeListener);
            node.setReportedState(state, currentTime);
            return true;
        }
        return false;
    }

    private boolean isControlledShutdown(NodeState state) {
        return (state.getState() == State.STOPPING
                && (state.getDescription().contains("Received signal 15 (SIGTERM - Termination signal)")
                    || state.getDescription().contains("controlled shutdown")));
    }


    // TODO refactor this into a function that only alters NodeInfo
    // FIXME urrrgh this function mixes and matches internal state mutations and pure return values
    /**
     * Decide the state assigned to a new node given the state it reported
     *
     * @param  node the node we are computing the state of
     * @param  currentState the current state of the node
     * @param  reportedState the new state reported by (or, in the case of down - inferred from) the node
     * @param  nodeListener this listener is notified for some of the system state changes that this will return
     * @return the node node state, or null to keep the nodes current state
     */
    private NodeState decideNodeStateGivenReportedState(NodeInfo node, NodeState currentState, NodeState reportedState,
                                                        NodeStateOrHostInfoChangeHandler nodeListener) {
        final long timeNow = timer.getCurrentTimeInMillis();

        log.log(LogLevel.DEBUG, "Finding new cluster state entry for " + node + " switching state " + currentState.getTextualDifference(reportedState));

        // Set nodes in maintenance if 1) down, or 2) initializing but set retired, to avoid migrating data
        // to the retired node while it is initializing
        // FIXME case 2 now handled in baseline
        // FIXME x 2; why is maxTransitionTime used here...?
        if (currentState.getState().oneOf("ur") && reportedState.getState().oneOf("dis")
            && (node.getWantedState().getState().equals(State.RETIRED) || !reportedState.getState().equals(State.INITIALIZING)))
        {
            node.setTransitionTime(timeNow);
            if (node.getUpStableStateTime() + stableStateTimePeriod > timeNow && !isControlledShutdown(reportedState)) {
                log.log(LogLevel.INFO, "Stable state: " + node.getUpStableStateTime() + " + " + stableStateTimePeriod + " > " + timeNow);
                eventLog.add(new NodeEvent(node,
                        "Stopped or possibly crashed after " + (timeNow - node.getUpStableStateTime())
                        + " ms, which is before stable state time period."
                        + " Premature crash count is now " + (node.getPrematureCrashCount() + 1) + ".",
                        NodeEvent.Type.CURRENT,
                        timeNow), isMaster);
                if (handlePrematureCrash(node, nodeListener)) return null;
            }
        }



        // TODO move this to new reported state handling
        // FIXME need to offload tracking of last reported state somewhere else (or take in last generated state?)
        // If we got increasing initialization progress, reset initialize timer
        if (reportedState.getState().equals(State.INITIALIZING) &&
            (!currentState.getState().equals(State.INITIALIZING) ||
             reportedState.getInitProgress() > currentState.getInitProgress()))
        {
            node.setInitProgressTime(timer.getCurrentTimeInMillis());
            log.log(LogLevel.DEBUG, "Reset initialize timer on " + node + " to " + node.getInitProgressTime());
        }



        // TODO do we need this when we have startup timestamps? at least it's unit tested.
        // TODO this seems fairly contrived...
        // If we get reverse initialize progress, mark node unstable, such that we don't mark it initializing again before it is up.
        if (currentState.getState().equals(State.INITIALIZING) &&
            (reportedState.getState().equals(State.INITIALIZING) && reportedState.getInitProgress() < currentState.getInitProgress()))
        {
            eventLog.add(new NodeEvent(node, "Stop or crash during initialization detected from reverse initializing progress."
                    + " Progress was " + currentState.getInitProgress() + " but is now " + reportedState.getInitProgress() + "."
                    + " Premature crash count is now " + (node.getPrematureCrashCount() + 1) + ".",
                    NodeEvent.Type.CURRENT, timeNow), isMaster);
            node.setRecentlyObservedUnstableDuringInit(true);
            return (handlePrematureCrash(node, nodeListener) ? null : new NodeState(node.getNode().getType(), State.DOWN).setDescription(
                    "Got reverse initialize progress. Assuming node has prematurely crashed"));
        }


        // If we go down while initializing, mark node unstable, such that we don't mark it initializing again before it is up.
        if (currentState.getState().equals(State.INITIALIZING) && reportedState.getState().oneOf("ds") && !isControlledShutdown(reportedState))
        {
            eventLog.add(new NodeEvent(node, "Stop or crash during initialization."
                    + " Premature crash count is now " + (node.getPrematureCrashCount() + 1) + ".",
                    NodeEvent.Type.CURRENT, timeNow), isMaster);
            return (handlePrematureCrash(node, nodeListener) ? null : new NodeState(node.getNode().getType(), State.DOWN).setDescription(reportedState.getDescription()));
        }


        // TODO what is really the point looking at init progress in this branch...?
        // TODO baseline state generation should make this a no-op if we don't bother to publish init progress in generated state
        // Ignore further unavailable states when node is set in maintenance
        if (currentState.getState().equals(State.MAINTENANCE) && reportedState.getState().oneOf("dis"))
        {
            if (node.getWantedState().getState().equals(State.RETIRED)  || !reportedState.getState().equals(State.INITIALIZING)
                    || reportedState.getInitProgress() <= NodeState.getListingBucketsInitProgressLimit() + 0.00001) {
                log.log(LogLevel.DEBUG, "Ignoring down and initializing reports while in maintenance mode on " + node + ".");
                return null;
            }
        }



        // FIXME comment is outdated; distributors are never in init state!
        // TODO handle premature crash count in baseline
        // Hide initializing state if node has been unstable. (Not for distributors as these own buckets while initializing)
        if ((currentState.getState().equals(State.DOWN) || currentState.getState().equals(State.UP)) &&
            reportedState.getState().equals(State.INITIALIZING) && node.getPrematureCrashCount() > 0 &&
            !node.isDistributor())
        {
            log.log(LogLevel.DEBUG, "Not setting " + node + " initializing again as it crashed prematurely earlier.");
            return new NodeState(node.getNode().getType(), State.DOWN).setDescription("Not setting node back up as it failed prematurely at last attempt");
        }



        // TODO handle implicit down on list buckets in baseline
        // Hide initializing state in cluster state if initialize progress is so low that we haven't listed buckets yet
        if (!node.isDistributor() && reportedState.getState().equals(State.INITIALIZING) &&
            reportedState.getInitProgress() <= NodeState.getListingBucketsInitProgressLimit() + 0.00001)
        {
            log.log(LogLevel.DEBUG, "Not setting " + node + " initializing in cluster state quite yet, as initializing progress still indicate it is listing buckets.");
            return new NodeState(node.getNode().getType(), State.DOWN).setDescription("Listing buckets. Progress " + (100 * reportedState.getInitProgress()) + " %.");
        }
        return reportedState.clone();
    }

    // TODO move somewhere appropriate
    public boolean handlePrematureCrash(NodeInfo node, NodeStateOrHostInfoChangeHandler changeListener) {
        node.setPrematureCrashCount(node.getPrematureCrashCount() + 1);
        if (disableUnstableNodes && node.getPrematureCrashCount() > maxPrematureCrashes) {
            NodeState wantedState = new NodeState(node.getNode().getType(), State.DOWN)
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
