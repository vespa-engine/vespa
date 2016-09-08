// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.Spec;
import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.SystemStateListener;

import java.util.*;
import java.util.logging.Logger;
import java.text.ParseException;
import java.util.stream.Collectors;

/**
 * This class get node state updates and uses them to decide the cluster state.
 */
// TODO: Remove all current state from this and make it rely on state from ClusterInfo instead
// TODO: Do this ASAP! SystemStateGenerator should ideally behave as a pure function!
public class SystemStateGenerator {

    private static Logger log = Logger.getLogger(SystemStateGenerator.class.getName());

    private final Timer timer;
    private final EventLogInterface eventLog;
    private ClusterStateView currentClusterStateView;
    private ClusterStateView nextClusterStateView;
    private Distribution distribution;
    private boolean nextStateViewChanged = false;
    private boolean isMaster = false;

    private Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
    private int maxInitProgressTime = 5000;
    private int maxPrematureCrashes = 4;
    private long stableStateTimePeriod = 60 * 60 * 1000;
    private static final int maxHistorySize = 50;
    private Set<ConfiguredNode> nodes;
    private Map<Integer, String> hostnames = new HashMap<>();
    private int minDistributorNodesUp = 1;
    private int minStorageNodesUp = 1;
    private double minRatioOfDistributorNodesUp = 0.50;
    private double minRatioOfStorageNodesUp = 0.50;
    private double minNodeRatioPerGroup = 0.0;
    private int maxSlobrokDisconnectGracePeriod = 1000;
    private int idealDistributionBits = 16;
    private static final boolean disableUnstableNodes = true;

    private final LinkedList<SystemStateHistoryEntry> systemStateHistory = new LinkedList<>();

    /**
     * @param metricUpdater may be null, in which case no metrics will be recorded.
     */
    public SystemStateGenerator(Timer timer, EventLogInterface eventLog, MetricUpdater metricUpdater) {
        try {
            currentClusterStateView = ClusterStateView.create("", metricUpdater);
            nextClusterStateView = ClusterStateView.create("", metricUpdater);
        } catch (ParseException e) {
            throw new RuntimeException("Parsing empty string should always work");
        }
        this.timer = timer;
        this.eventLog = eventLog;
        maxTransitionTime.put(NodeType.DISTRIBUTOR, 5000);
        maxTransitionTime.put(NodeType.STORAGE, 5000);
    }

    public void handleAllDistributorsInSync(DatabaseHandler database,
                                            DatabaseHandler.Context dbContext) throws InterruptedException {
        int startTimestampsReset = 0;
        for (NodeType nodeType : NodeType.getTypes()) {
            for (ConfiguredNode configuredNode : nodes) {
                Node node = new Node(nodeType, configuredNode.index());
                NodeInfo nodeInfo = dbContext.getCluster().getNodeInfo(node);
                NodeState nodeState = nextClusterStateView.getClusterState().getNodeState(node);
                if (nodeInfo != null && nodeState != null) {
                    if (nodeState.getStartTimestamp() > nodeInfo.getStartTimestamp()) {
                        log.log(LogLevel.INFO, String.format("Storing away new start timestamp for node %s (%d)",
                                node, nodeState.getStartTimestamp()));
                        nodeInfo.setStartTimestamp(nodeState.getStartTimestamp());
                    }
                    if (nodeState.getStartTimestamp() > 0) {
                        log.log(LogLevel.INFO, "Resetting timestamp in cluster state for node " + node);
                        nodeState.setStartTimestamp(0);
                        nextClusterStateView.getClusterState().setNodeState(node, nodeState);
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
                    " start timestamps as all available distributors have seen newest cluster state.", timer.getCurrentTimeInMillis()));
            nextStateViewChanged = true;
            database.saveStartTimestamps(dbContext);
        } else {
            log.log(LogLevel.DEBUG, "Found no start timestamps to reset in cluster state.");
        }
    }

    public void setMaxTransitionTime(Map<NodeType, Integer> map) { maxTransitionTime = map; }
    public void setMaxInitProgressTime(int millisecs) { maxInitProgressTime = millisecs; }
    public void setMaxPrematureCrashes(int count) { maxPrematureCrashes = count; }
    public void setStableStateTimePeriod(long millisecs) { stableStateTimePeriod = millisecs; }

    public ClusterStateView currentClusterStateView() { return currentClusterStateView; }

    /** Returns an immutable list of the historical states this has generated */
    public List<SystemStateHistoryEntry> systemStateHistory() {
        return Collections.unmodifiableList(systemStateHistory);
    }

    public boolean stateMayHaveChanged() {
        return nextStateViewChanged;
    }

    public void unsetStateChangedFlag() {
        nextStateViewChanged = false;
    }

    public void setMinNodesUp(int minDistNodes, int minStorNodes, double minDistRatio, double minStorRatio) {
        minDistributorNodesUp = minDistNodes;
        minStorageNodesUp = minStorNodes;
        minRatioOfDistributorNodesUp = minDistRatio;
        minRatioOfStorageNodesUp = minStorRatio;
        nextStateViewChanged = true;
    }

    public void setMinNodeRatioPerGroup(double upRatio) {
        this.minNodeRatioPerGroup = upRatio;
        nextStateViewChanged = true;
    }

    // TODO deprecate
    /** Sets the nodes of this and attempts to keep the node state in sync */
    public void setNodes(ClusterInfo newClusterInfo) {
        this.nodes = new HashSet<>(newClusterInfo.getConfiguredNodes().values());

        for (ConfiguredNode node : this.nodes) {
            NodeInfo newNodeInfo = newClusterInfo.getStorageNodeInfo(node.index());
            NodeState currentState = currentClusterStateView.getClusterState().getNodeState(new Node(NodeType.STORAGE, node.index()));
            if (currentState.getState() == State.RETIRED || currentState.getState() == State.UP) { // then correct to configured state
                proposeNewNodeState(newNodeInfo, new NodeState(NodeType.STORAGE, node.retired() ? State.RETIRED : State.UP));
            }
        }

        // Ensure that any nodes that have been removed from the config are also
        // promptly removed from the next (and subsequent) generated cluster states.
        pruneAllNodesNotContainedInConfig();

        nextStateViewChanged = true;
    }

    private void pruneAllNodesNotContainedInConfig() {
        Set<Integer> configuredIndices = this.nodes.stream().map(ConfiguredNode::index).collect(Collectors.toSet());
        final ClusterState candidateNextState = nextClusterStateView.getClusterState();
        pruneNodesNotContainedInConfig(candidateNextState, configuredIndices, NodeType.DISTRIBUTOR);
        pruneNodesNotContainedInConfig(candidateNextState, configuredIndices, NodeType.STORAGE);
    }

    public void setDistribution(Distribution distribution) {
        this.distribution = distribution;
        nextStateViewChanged = true;
    }

    public void setMaster(boolean isMaster) {
        this.isMaster = isMaster;
    }
    public void setMaxSlobrokDisconnectGracePeriod(int millisecs) { maxSlobrokDisconnectGracePeriod = millisecs; }

    public void setDistributionBits(int bits) {
        if (bits == idealDistributionBits) return;
        idealDistributionBits = bits;
        int currentDistributionBits = calculateMinDistributionBitCount();
        if (currentDistributionBits != nextClusterStateView.getClusterState().getDistributionBitCount()) {
            nextClusterStateView.getClusterState().setDistributionBits(currentDistributionBits);
            nextStateViewChanged = true;
        }
    }

    public int getDistributionBits() { return idealDistributionBits; }

    public int calculateMinDistributionBitCount() {
        int currentDistributionBits = idealDistributionBits;
        int minNode = -1;
        for (ConfiguredNode node : nodes) {
            NodeState ns = nextClusterStateView.getClusterState().getNodeState(new Node(NodeType.STORAGE, node.index()));
            if (ns.getState().oneOf("iur")) {
                if (ns.getMinUsedBits() < currentDistributionBits) {
                    currentDistributionBits = ns.getMinUsedBits();
                    minNode = node.index();
                }
            }
        }
        if (minNode == -1) {
            log.log(LogLevel.DEBUG, "Distribution bit count should still be default as all available nodes have at least split to " + idealDistributionBits + " bits");
        } else {
            log.log(LogLevel.DEBUG, "Distribution bit count is limited to " + currentDistributionBits + " due to storage node " + minNode);
        }
        return currentDistributionBits;
    }

    public ClusterState getClusterState() { return currentClusterStateView.getClusterState(); }

    /**
     * Return the current cluster state, but if the cluster is down, modify the node states with the
     * actual node states from the temporary next state.
     */
    public ClusterState getConsolidatedClusterState() {
        ClusterState currentState = currentClusterStateView.getClusterState();
        if (currentState.getClusterState().equals(State.UP)) {
            return currentState;
        }

        ClusterState nextState = nextClusterStateView.getClusterState();
        if (!currentState.getClusterState().equals(nextState.getClusterState())) {
            log.warning("Expected current cluster state object to have same global state as the under creation instance.");
        }
        ClusterState state = nextState.clone();
        state.setVersion(currentState.getVersion());
        state.setOfficial(false);
        return state;
    }

    private Optional<Event> getDownDueToTooFewNodesEvent(ClusterState nextClusterState) {
        int upStorageCount = 0, upDistributorCount = 0;
        int dcount = nodes.size();
        int scount = nodes.size();
        for (NodeType type : NodeType.getTypes()) {
            for (ConfiguredNode node : nodes) {
                NodeState ns = nextClusterState.getNodeState(new Node(type, node.index()));
                if (ns.getState() == State.UP || ns.getState() == State.RETIRED || ns.getState() == State.INITIALIZING) {
                    if (type.equals(NodeType.STORAGE))
                        ++upStorageCount;
                    else
                        ++upDistributorCount;
                }
            }
        }

        long timeNow = timer.getCurrentTimeInMillis();
        if (upStorageCount < minStorageNodesUp) {
            return Optional.of(new ClusterEvent(ClusterEvent.Type.SYSTEMSTATE,
                    "Less than " + minStorageNodesUp + " storage nodes available (" + upStorageCount + "). Setting cluster state down.",
                    timeNow));
        }
        if (upDistributorCount < minDistributorNodesUp) {
            return Optional.of(new ClusterEvent(ClusterEvent.Type.SYSTEMSTATE,
                    "Less than " + minDistributorNodesUp + " distributor nodes available (" + upDistributorCount + "). Setting cluster state down.",
                    timeNow));
        }
        if (minRatioOfStorageNodesUp * scount > upStorageCount) {
            return Optional.of(new ClusterEvent(ClusterEvent.Type.SYSTEMSTATE,
                    "Less than " + (100 * minRatioOfStorageNodesUp) + " % of storage nodes are available ("
                            + upStorageCount + "/" + scount + "). Setting cluster state down.",
                    timeNow));
        }
        if (minRatioOfDistributorNodesUp * dcount > upDistributorCount) {
            return Optional.of(new ClusterEvent(ClusterEvent.Type.SYSTEMSTATE,
                    "Less than " + (100 * minRatioOfDistributorNodesUp) + " % of distributor nodes are available ("
                            + upDistributorCount + "/" + dcount + "). Setting cluster state down.",
                    timeNow));
        }
        return Optional.empty();
    }

    private static Node storageNode(int index) {
        return new Node(NodeType.STORAGE, index);
    }

    private void performImplicitStorageNodeStateTransitions(ClusterState candidateState, ContentCluster cluster) {
        if (distribution == null) {
            return; // FIXME due to tests that don't bother setting distr config! Never happens in prod.
        }
        // First clear the states of any nodes that according to reported/wanted state alone
        // should have their states cleared. We might still take these down again based on the
        // decisions of the group availability calculator, but this way we ensure that groups
        // that no longer should be down will have their nodes implicitly made available again.
        // TODO this will be void once SystemStateGenerator has been rewritten to be stateless.
        final Set<Integer> clearedNodes = clearDownStateForStorageNodesThatCanBeUp(candidateState, cluster);

        final GroupAvailabilityCalculator calc = new GroupAvailabilityCalculator.Builder()
                .withMinNodeRatioPerGroup(minNodeRatioPerGroup)
                .withDistribution(distribution)
                .build();
        final Set<Integer> nodesToTakeDown = calc.nodesThatShouldBeDown(candidateState);
        markNodesAsDownDueToGroupUnavailability(cluster, candidateState, nodesToTakeDown, clearedNodes);

        clearedNodes.removeAll(nodesToTakeDown);
        logEventsForNodesThatWereTakenUp(clearedNodes, cluster);
    }

    private void logEventsForNodesThatWereTakenUp(Set<Integer> newlyUpNodes, ContentCluster cluster) {
        newlyUpNodes.forEach(i -> {
            final NodeInfo info = cluster.getNodeInfo(storageNode(i)); // Should always be non-null here.
            // TODO the fact that this only happens for group up events is implementation specific
            // should generalize this if we get other such events.
            eventLog.addNodeOnlyEvent(new NodeEvent(info,
                    "Group availability restored; taking node back up",
                    NodeEvent.Type.CURRENT, timer.getCurrentTimeInMillis()), LogLevel.INFO);
        });
    }

    private void markNodesAsDownDueToGroupUnavailability(ContentCluster cluster,
                                                         ClusterState candidateState,
                                                         Set<Integer> nodesToTakeDown,
                                                         Set<Integer> clearedNodes)
    {
        for (Integer idx : nodesToTakeDown) {
            final Node node = storageNode(idx);
            NodeState newState = new NodeState(NodeType.STORAGE, State.DOWN);
            newState.setDescription("group node availability below configured threshold");
            candidateState.setNodeState(node, newState);

            logNodeGroupDownEdgeEventOnce(clearedNodes, node, cluster);
        }
    }

    private void logNodeGroupDownEdgeEventOnce(Set<Integer> clearedNodes, Node node, ContentCluster cluster) {
        final NodeInfo nodeInfo = cluster.getNodeInfo(node);
        // If clearedNodes contains the index it means we're just re-downing a node
        // that was previously down. If this is the case, we'd cause a duplicate
        // event if we logged it now as well.
        if (nodeInfo != null && !clearedNodes.contains(node.getIndex())) {
            eventLog.addNodeOnlyEvent(new NodeEvent(nodeInfo,
                    "Setting node down as the total availability of its group is " +
                    "below the configured threshold",
                    NodeEvent.Type.CURRENT, timer.getCurrentTimeInMillis()), LogLevel.INFO);
        }
    }

    private NodeState baselineNodeState(NodeInfo info) {
        NodeState reported = info.getReportedState();
        NodeState wanted = info.getWantedState();

        final NodeState baseline = reported.clone();
        if (wanted.getState() != State.UP) {
            baseline.setDescription(wanted.getDescription());
            if (reported.above(wanted)) {
                baseline.setState(wanted.getState());
            }
        }
        // Don't reintroduce start timestamp to the node's state if it has already been
        // observed by all distributors. This matches how handleNewReportedNodeState() sets timestamps.
        // TODO make timestamp semantics clearer. Non-obvious what the two different timestamp stores imply.
        // For posterity: reported.getStartTimestamp() is the start timestamp the node itself has stated.
        // info.getStartTimestamp() is the timestamp written as having been observed by all distributors
        // (which is done in handleAllDistributorsInSync()).
        if (reported.getStartTimestamp() <= info.getStartTimestamp()) {
            baseline.setStartTimestamp(0);
        }

        return baseline;
    }

    // Returns set of nodes whose state was cleared
    private Set<Integer> clearDownStateForStorageNodesThatCanBeUp(
            ClusterState candidateState, ContentCluster cluster)
    {
        final int nodeCount = candidateState.getNodeCount(NodeType.STORAGE);
        final Set<Integer> clearedNodes = new HashSet<>();
        for (int i = 0; i < nodeCount; ++i) {
            final Node node = storageNode(i);
            final NodeInfo info = cluster.getNodeInfo(node);
            final NodeState currentState = candidateState.getNodeState(node);
            if (mayClearCurrentNodeState(currentState, info)) {
                candidateState.setNodeState(node, baselineNodeState(info));
                clearedNodes.add(i);
            }
        }
        return clearedNodes;
    }

    private boolean mayClearCurrentNodeState(NodeState currentState, NodeInfo info) {
        if (currentState.getState() != State.DOWN) {
            return false;
        }
        if (info == null) {
            // Nothing known about node in cluster info; we definitely don't want it
            // to be taken up at this point.
            return false;
        }
        // There exists an edge in watchTimers where a node in Maintenance is implicitly
        // transitioned into Down without being Down in either reported or wanted states
        // iff isRpcAddressOutdated() is true. To avoid getting into an edge where we
        // inadvertently clear this state because its reported/wanted states seem fine,
        // we must also check if that particular edge could have happened. I.e. whether
        // the node's RPC address is marked as outdated.
        // It also makes sense in general to not allow taking a node back up automatically
        // if its RPC connectivity appears to be bad.
        if (info.isRpcAddressOutdated()) {
            return false;
        }
        // Rationale: we can only enter this statement if the _current_ (generated) state
        // of the node is Down. Aside from the group take-down logic, there should not exist
        // any other edges in the cluster controller state transition logic where a node
        // may be set Down while both its reported state and wanted state imply that a better
        // state should already have been chosen. Consequently we allow the node to have its
        // Down-state cleared.
        return (info.getReportedState().getState() != State.DOWN
                && !info.getWantedState().getState().oneOf("d"));
    }

    private ClusterStateView createNextVersionOfClusterStateView(ContentCluster cluster) {
        // If you change this method, see *) in notifyIfNewSystemState
        ClusterStateView candidateClusterStateView = nextClusterStateView.cloneForNewState();
        ClusterState candidateClusterState = candidateClusterStateView.getClusterState();

        int currentDistributionBits = calculateMinDistributionBitCount();
        if (currentDistributionBits != nextClusterStateView.getClusterState().getDistributionBitCount()) {
            candidateClusterState.setDistributionBits(currentDistributionBits);
        }
        performImplicitStorageNodeStateTransitions(candidateClusterState, cluster);

        return candidateClusterStateView;
    }

    private void pruneNodesNotContainedInConfig(ClusterState candidateClusterState,
                                                Set<Integer> configuredIndices,
                                                NodeType nodeType)
    {
        final int nodeCount = candidateClusterState.getNodeCount(nodeType);
        for (int i = 0; i < nodeCount; ++i) {
            final Node node = new Node(nodeType, i);
            final NodeState currentState = candidateClusterState.getNodeState(node);
            if (!configuredIndices.contains(i) && !currentState.getState().equals(State.DOWN)) {
                log.log(LogLevel.INFO, "Removing node " + node + " from state as it is no longer present in config");
                candidateClusterState.setNodeState(node, new NodeState(nodeType, State.DOWN));
            }
        }
    }

    private void recordNewClusterStateHasBeenChosen(
            ClusterState currentClusterState, ClusterState newClusterState, Event clusterEvent) {
        long timeNow = timer.getCurrentTimeInMillis();

        if (!currentClusterState.getClusterState().equals(State.UP) &&
                newClusterState.getClusterState().equals(State.UP)) {
            eventLog.add(new ClusterEvent(ClusterEvent.Type.SYSTEMSTATE,
                                          "Enough nodes available for system to become up.", timeNow), isMaster);
        } else if (currentClusterState.getClusterState().equals(State.UP) &&
                   ! newClusterState.getClusterState().equals(State.UP)) {
            assert(clusterEvent != null);
            eventLog.add(clusterEvent, isMaster);
        }

        if (newClusterState.getDistributionBitCount() != currentClusterState.getDistributionBitCount()) {
            eventLog.add(new ClusterEvent(
                    ClusterEvent.Type.SYSTEMSTATE,
                    "Altering distribution bits in system from "
                            + currentClusterState.getDistributionBitCount() + " to " +
                            currentClusterState.getDistributionBitCount(),
                    timeNow), isMaster);
        }

        eventLog.add(new ClusterEvent(
                ClusterEvent.Type.SYSTEMSTATE,
                "New cluster state version " + newClusterState.getVersion() + ". Change from last: " +
                        currentClusterState.getTextualDifference(newClusterState),
                timeNow), isMaster);

        log.log(LogLevel.DEBUG, "Created new cluster state version: " + newClusterState.toString(true));
        systemStateHistory.addFirst(new SystemStateHistoryEntry(newClusterState, timeNow));
        if (systemStateHistory.size() > maxHistorySize) {
            systemStateHistory.removeLast();
        }
    }

    private void mergeIntoNextClusterState(ClusterState sourceState) {
        final ClusterState nextState = nextClusterStateView.getClusterState();
        final int nodeCount = sourceState.getNodeCount(NodeType.STORAGE);
        for (int i = 0; i < nodeCount; ++i) {
            final Node node = storageNode(i);
            final NodeState stateInSource = sourceState.getNodeState(node);
            final NodeState stateInTarget = nextState.getNodeState(node);
            if (stateInSource.getState() != stateInTarget.getState()) {
                nextState.setNodeState(node, stateInSource);
            }
        }
    }

    public boolean notifyIfNewSystemState(ContentCluster cluster, SystemStateListener stateListener) {
        if ( ! nextStateViewChanged) return false;

        ClusterStateView newClusterStateView = createNextVersionOfClusterStateView(cluster);

        ClusterState newClusterState = newClusterStateView.getClusterState();
        // Creating the next version of the state may implicitly take down nodes, so our checks
        // for taking the entire cluster down must happen _after_ this
        Optional<Event> clusterDown = getDownDueToTooFewNodesEvent(newClusterState);
        newClusterState.setClusterState(clusterDown.isPresent() ? State.DOWN : State.UP);

        if (newClusterState.similarTo(currentClusterStateView.getClusterState())) {
            log.log(LogLevel.DEBUG,
                    "State hasn't changed enough to warrant new cluster state. Not creating new state: " +
                            currentClusterStateView.getClusterState().getTextualDifference(newClusterState));
            log.log(LogLevel.DEBUG, String.format("Not publishing state! newClusterState='%s', currentClusterState='%s'",
                    newClusterState.toString(), currentClusterStateView.getClusterState().toString()));
            return false;
        }

        // Update the version of newClusterState now. This cannot be done prior to similarTo(),
        // since it makes the cluster states different. From now on, the new cluster state is immutable.
        newClusterState.setVersion(currentClusterStateView.getClusterState().getVersion() + 1);

        recordNewClusterStateHasBeenChosen(currentClusterStateView.getClusterState(),
                                           newClusterStateView.getClusterState(), clusterDown.orElse(null));

        // *) Ensure next state is still up to date.
        // This should make nextClusterStateView a deep-copy of currentClusterStateView.
        // If more than the distribution bits and state are deep-copied in
        // createNextVersionOfClusterStateView(), we need to add corresponding statements here.
        // This seems like a hack...
        nextClusterStateView.getClusterState().setDistributionBits(newClusterState.getDistributionBitCount());
        nextClusterStateView.getClusterState().setClusterState(newClusterState.getClusterState());
        mergeIntoNextClusterState(newClusterState);

        currentClusterStateView = newClusterStateView;
        nextStateViewChanged = false;

        stateListener.handleNewSystemState(currentClusterStateView.getClusterState());

        return true;
    }

    public void setLatestSystemStateVersion(int version) {
        currentClusterStateView.getClusterState().setVersion(Math.max(1, version));
        nextStateViewChanged = true;
    }


    // TODO deprecated once we have a baseline state function
    private void setNodeState(NodeInfo node, NodeState newState) {
        NodeState oldState = nextClusterStateView.getClusterState().getNodeState(node.getNode());

        // Correct UP to RETIRED if the node wants to be retired
        if (newState.above(node.getWantedState()))
            newState.setState(node.getWantedState().getState());

        // Keep old description if a new one is not set and we're not going up or in initializing mode
        if ( ! newState.getState().oneOf("ui") && oldState.hasDescription()) {
            newState.setDescription(oldState.getDescription());
        }

        // Keep disk information if not set in new state
        if (newState.getDiskCount() == 0 && oldState.getDiskCount() != 0) {
            newState.setDiskCount(oldState.getDiskCount());
            for (int i=0; i<oldState.getDiskCount(); ++i) {
                newState.setDiskState(i, oldState.getDiskState(i));
            }
        }
        if (newState.equals(oldState)) {
            return;
        }

        eventLog.add(new NodeEvent(node, "Altered node state in cluster state from '" + oldState.toString(true)
                                   + "' to '" + newState.toString(true) + "'.",
                                   NodeEvent.Type.CURRENT, timer.getCurrentTimeInMillis()), isMaster);
        nextClusterStateView.getClusterState().setNodeState(node.getNode(), newState);
        nextStateViewChanged = true;
    }


    // TODO nodeListener is only used via decideNodeStateGivenReportedState -> handlePrematureCrash
    // TODO this will recursively invoke proposeNewNodeState, which will presumably (i.e. hopefully) be a no-op...
    public void handleNewReportedNodeState(NodeInfo node, NodeState reportedState, NodeStateOrHostInfoChangeHandler nodeListener) {
        ClusterState nextState = nextClusterStateView.getClusterState();
        NodeState currentState = nextState.getNodeState(node.getNode());
        log.log(currentState.equals(reportedState) && node.getVersion() == 0 ? LogLevel.SPAM : LogLevel.DEBUG,
                "Got nodestate reply from " + node + ": "
                + node.getReportedState().getTextualDifference(reportedState) + " (Current state is " + currentState.toString(true) + ")");
        long currentTime = timer.getCurrentTimeInMillis();

        if (reportedState.getState().equals(State.DOWN)) {
            node.setTimeOfFirstFailingConnectionAttempt(currentTime);
        }

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

        // FIXME comparison against current cluster state view no longer makes sense!
        NodeState alteredState = decideNodeStateGivenReportedState(node, currentState, reportedState, nodeListener);
        if (alteredState != null) {
            ClusterState clusterState = currentClusterStateView.getClusterState();


            if (alteredState.above(node.getWantedState())) {
                log.log(LogLevel.DEBUG, "Cannot set node in state " + alteredState.getState() + " when wanted state is " + node.getWantedState());
                alteredState.setState(node.getWantedState().getState());
            }


            // TODO handle in baseline
            if (reportedState.getStartTimestamp() > node.getStartTimestamp()) {
                log.log(LogLevel.INFO, String.format("Reported TS %d > info TS %d",
                        reportedState.getStartTimestamp(), node.getStartTimestamp()));
                alteredState.setStartTimestamp(reportedState.getStartTimestamp());
            } else {
                alteredState.setStartTimestamp(0);
            }


            if (!alteredState.similarTo(currentState)) {
                setNodeState(node, alteredState);
            } else if (!alteredState.equals(currentState)) {


                // TODO figure out what to do with init progress vs. new states.
                // TODO .. simply don't include init progress in generated states? are they used anywhere? it's already in reported state
                // TODO why is this an else-if branch? can't multiple of these happen at the same time?!
                if (currentState.getState().equals(State.INITIALIZING) && alteredState.getState().equals(State.INITIALIZING) &&
                    Math.abs(currentState.getInitProgress() - alteredState.getInitProgress()) > 0.000000001)
                {
                    log.log(LogLevel.DEBUG, "Only silently updating init progress for " + node + " in cluster state because new "
                            + "state is too similar to tag new version: " + currentState.getTextualDifference(alteredState));
                    currentState.setInitProgress(alteredState.getInitProgress());
                    nextState.setNodeState(node.getNode(), currentState);

                    NodeState currentNodeState = clusterState.getNodeState(node.getNode());
                    if (currentNodeState.getState().equals(State.INITIALIZING)) {
                        currentNodeState.setInitProgress(alteredState.getInitProgress());
                        clusterState.setNodeState(node.getNode(), currentNodeState);
                    }


                    // TODO handle in baseline
                } else if (alteredState.getMinUsedBits() != currentState.getMinUsedBits()) {
                    log.log(LogLevel.DEBUG, "Altering node state to reflect that min distribution bit count has changed from "
                                            + currentState.getMinUsedBits() + " to " + alteredState.getMinUsedBits());
                    int oldCount = currentState.getMinUsedBits();
                    currentState.setMinUsedBits(alteredState.getMinUsedBits());
                    nextState.setNodeState(node.getNode(), currentState);
                    eventLog.add(new NodeEvent(node, "Altered min distribution bit count from " + oldCount
                                            + " to " + currentState.getMinUsedBits(), NodeEvent.Type.CURRENT, currentTime), isMaster);
                    nextStateViewChanged = true;
                } else {
                    log.log(LogLevel.DEBUG, "Not altering state of " + node + " in cluster state because new state is too similar: "
                            + currentState.getTextualDifference(alteredState));
                }


            } else if (alteredState.getDescription().contains("Listing buckets")) {


                // TODO figure out why this is handled specially and the desired semantics of this
                currentState.setDescription(alteredState.getDescription());
                nextState.setNodeState(node.getNode(), currentState);
                NodeState currentNodeState = clusterState.getNodeState(node.getNode());
                currentNodeState.setDescription(alteredState.getDescription());
                clusterState.setNodeState(node.getNode(), currentNodeState);
            }
        }
    }

    public void handleNewNode(NodeInfo node) {
        setHostName(node);
        String message = "Found new node " + node + " in slobrok at " + node.getRpcAddress();
        eventLog.add(new NodeEvent(node, message, NodeEvent.Type.REPORTED, timer.getCurrentTimeInMillis()), isMaster);
    }


    // TODO move to node state change handler
    public void handleMissingNode(NodeInfo node, NodeStateOrHostInfoChangeHandler nodeListener) {
        removeHostName(node);

        long timeNow = timer.getCurrentTimeInMillis();

        if (node.getLatestNodeStateRequestTime() != null) {
            eventLog.add(new NodeEvent(node, "Node is no longer in slobrok, but we still have a pending state request.", NodeEvent.Type.REPORTED, timeNow), isMaster);
        } else {
            eventLog.add(new NodeEvent(node, "Node is no longer in slobrok. No pending state request to node.", NodeEvent.Type.REPORTED, timeNow), isMaster);
        }

        if (node.getReportedState().getState().equals(State.STOPPING)) {
            log.log(LogLevel.DEBUG, "Node " + node.getNode() + " is no longer in slobrok. Was in stopping state, so assuming it has shut down normally. Setting node down");
            NodeState ns = node.getReportedState().clone();
            ns.setState(State.DOWN);
            handleNewReportedNodeState(node, ns.clone(), nodeListener);
            node.setReportedState(ns, timer.getCurrentTimeInMillis()); // Must reset it to null to get connection attempts counted
        } else {
            log.log(LogLevel.DEBUG, "Node " + node.getNode() + " no longer in slobrok was in state " + node.getReportedState() + ". Waiting to see if it reappears in slobrok");
        }
    }


    // TODO handle to baseline
    /**
     * Propose a new state for a node. This may happen due to an administrator action, orchestration, or
     * a configuration change.
     */
    public void proposeNewNodeState(NodeInfo node, NodeState proposedState) {
        NodeState currentState = nextClusterStateView.getClusterState().getNodeState(node.getNode());
        NodeState currentReported = node.getReportedState(); // TODO: Is there a reason to have both of this and the above?

        NodeState newCurrentState = currentReported.clone();

        newCurrentState.setState(proposedState.getState()).setDescription(proposedState.getDescription());
        if (newCurrentState.getStartTimestamp() == node.getStartTimestamp()) {
            newCurrentState.setStartTimestamp(0); // Already observed FIXME deprecated, only for getting tests to pass
        }

        if (currentState.getState().equals(newCurrentState.getState())) return;

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
        setNodeState(node, newCurrentState);
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

    private void removeHostName(NodeInfo node) {
        hostnames.remove(node.getNodeIndex());
    }


    public boolean watchTimers(ContentCluster cluster, NodeStateOrHostInfoChangeHandler nodeListener) {
        boolean triggeredAnyTimers = false;
        long currentTime = timer.getCurrentTimeInMillis();
        for(NodeInfo node : cluster.getNodeInfo()) {
            NodeState currentStateInSystem = nextClusterStateView.getClusterState().getNodeState(node.getNode());
            NodeState lastReportedState = node.getReportedState();


            // TODO move to own node event listener class
            // If we haven't had slobrok contact in a given amount of time and node is still not considered down,
            // mark it down.
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
                handleNewReportedNodeState(node, state.clone(), nodeListener);
                node.setReportedState(state, currentTime);
                triggeredAnyTimers = true;
            }



            // TODO handle in baseline (temporal state transition)
            // If node is still unavailable after transition time, mark it down
            if (currentStateInSystem.getState().equals(State.MAINTENANCE)
                && ( ! nextStateViewChanged || ! this.nextClusterStateView.getClusterState().getNodeState(node.getNode()).getState().equals(State.DOWN))
                && node.getWantedState().above(new NodeState(node.getNode().getType(), State.DOWN))
                && (lastReportedState.getState().equals(State.DOWN) || node.isRpcAddressOutdated())
                && node.getTransitionTime() + maxTransitionTime.get(node.getNode().getType()) < currentTime)
            {
                eventLog.add(new NodeEvent(node, (currentTime - node.getTransitionTime())
                        + " milliseconds without contact. Marking node down.", NodeEvent.Type.CURRENT, currentTime), isMaster);
                NodeState newState = new NodeState(node.getNode().getType(), State.DOWN).setDescription(
                        (currentTime - node.getTransitionTime()) + " ms without contact. Too long to keep in maintenance. Marking node down");
                    // Keep old description if there is one as it is likely closer to the cause of the problem
                if (currentStateInSystem.hasDescription()) newState.setDescription(currentStateInSystem.getDescription());
                setNodeState(node, newState);
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
                NodeState newState = new NodeState(node.getNode().getType(), State.DOWN).setDescription(
                        (currentTime - node.getInitProgressTime()) + " ms without initialize progress. Assuming node has deadlocked.");
                setNodeState(node, newState);
                handlePrematureCrash(node, nodeListener);
                triggeredAnyTimers = true;
            }



            if (node.getUpStableStateTime() + stableStateTimePeriod <= currentTime
                && lastReportedState.getState().equals(State.UP)
                && node.getPrematureCrashCount() <= maxPrematureCrashes
                && node.getPrematureCrashCount() != 0)
            {
                node.setPrematureCrashCount(0);
                log.log(LogLevel.DEBUG, "Resetting premature crash count on node " + node + " as it has been up for a long time.");
                triggeredAnyTimers = true;
            } else if (node.getDownStableStateTime() + stableStateTimePeriod <= currentTime
                && lastReportedState.getState().equals(State.DOWN)
                && node.getPrematureCrashCount() <= maxPrematureCrashes
                && node.getPrematureCrashCount() != 0)
            {
                node.setPrematureCrashCount(0);
                log.log(LogLevel.DEBUG, "Resetting premature crash count on node " + node + " as it has been down for a long time.");
                triggeredAnyTimers = true;
            }
        }

        // TODO must ensure state generator is called if this returns true
        return triggeredAnyTimers;
    }

    private boolean isControlledShutdown(NodeState state) {
        return (state.getState() == State.STOPPING && (state.getDescription().contains("Received signal 15 (SIGTERM - Termination signal)")
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
        long timeNow = timer.getCurrentTimeInMillis();

        log.log(LogLevel.DEBUG, "Finding new cluster state entry for " + node + " switching state " + currentState.getTextualDifference(reportedState));


        // Set nodes in maintenance if 1) down, or 2) initializing but set retired, to avoid migrating data
        // to the retired node while it is initializing
        // FIXME case 2 now handled in baseline
        // FIXME x 2; why is maxTransitionTime used here...?
        if (currentState.getState().oneOf("ur") && reportedState.getState().oneOf("dis")
            && (node.getWantedState().getState().equals(State.RETIRED) || !reportedState.getState().equals(State.INITIALIZING)))
        {
            long currentTime = timer.getCurrentTimeInMillis(); // FIXME pointless dupe of timeNow
            node.setTransitionTime(currentTime);
            if (node.getUpStableStateTime() + stableStateTimePeriod > currentTime && !isControlledShutdown(reportedState)) {
                log.log(LogLevel.DEBUG, "Stable state: " + node.getUpStableStateTime() + " + " + stableStateTimePeriod + " > " + currentTime);
                eventLog.add(new NodeEvent(node,
                        "Stopped or possibly crashed after " + (currentTime - node.getUpStableStateTime())
                        + " ms, which is before stable state time period."
                        + " Premature crash count is now " + (node.getPrematureCrashCount() + 1) + ".",
                        NodeEvent.Type.CURRENT,
                        timeNow), isMaster);
                if (handlePrematureCrash(node, nodeListener)) return null;
            }
            if (maxTransitionTime.get(node.getNode().getType()) != 0) {
                return new NodeState(node.getNode().getType(), State.MAINTENANCE).setDescription(reportedState.getDescription());
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

    public void handleUpdatedHostInfo(NodeInfo nodeInfo, HostInfo hostInfo) {
        // Only pass the host info to the latest cluster state view.
        currentClusterStateView.handleUpdatedHostInfo(hostnames, nodeInfo, hostInfo);
    }


    // TODO move this out of here. get to da choppa!!!
    public class SystemStateHistoryEntry {

        private final ClusterState state;
        private final long time;

        SystemStateHistoryEntry(ClusterState state, long time) {
            this.state = state;
            this.time = time;
        }

        public ClusterState state() { return state; }

        public long time() { return time; }

    }

}
