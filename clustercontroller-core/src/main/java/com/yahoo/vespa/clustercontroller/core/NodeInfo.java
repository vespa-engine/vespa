// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.collections.Pair;
import com.yahoo.jrt.Target;
import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.distribution.Group;
import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.rpc.RPCCommunicator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Represents a node in a content cluster.
 */
abstract public class NodeInfo implements Comparable<NodeInfo> {

    public static Logger log = Logger.getLogger(NodeInfo.class.getName());

    private final ContentCluster cluster;
    private Node node;
    private String rpcAddress;
    /** If set to a timestamp, we haven't seen this node in slobrok since then. If not set, it is currently in slobrok. */
    private Long lastSeenInSlobrok;
    private List<Pair<GetNodeStateRequest, Long>> pendingNodeStateRequests = new LinkedList<>();
    private NodeState reportedState;
    private NodeState wantedState;

    /** Whether this node has been configured to be retired and should therefore always return retired as its wanted state */
    private boolean configuredRetired;
    /**
     * Node has been observed transitioning from Init to Down at least once during the last "premature crash count"
     * period. Gets reset whenever the crash count is reset to zero after a period of stability.
     *
     * Flag can also be explicitly toggled by external code, such as if a reported node state
     * handler discovers "reverse" init progress. This indicates a "silent" down edge and should be
     * handled as such.
     *
     * It is an explicit choice that we only do this on an edge to Down (and not Stopping). Stopping implies
     * an administrative action, not that the node itself is unstable.
     */
    private boolean recentlyObservedUnstableDuringInit;

    /** The time we set the current state last. */
    private long nextAttemptTime;
    /** Cached connection to this node. */
    private Target connection;
    /** We cache last connection we did request info on, as we want to report appropriate error for node regardless of whether other commands have created new connection. */
    public Target lastRequestInfoConnection;
    /** Sets the version we assumed we were when opening this connection. (Needed in case we need to do some sort of handshaking to decrease version. */
    private int connectionVersion;
    /**
     * Counts the number of attempts we have tried since last time we had
     * contact with the node. (Used to retry fast early)
     */
    private int connectionAttemptCount;
    /**
     * Set to 0 each time we get a successful node state reply from a node.
     * Set to the current time each time we do a node state request, if the value was 0 to begin with.
     * Thus, if value is not 0, this is the start of the period where we could not talk to the node.
     */
    private long timeOfFirstFailingConnectionAttempt;
    /**
     * Sets the version of the state transaction that this node accepts.
     * Version 0 is the original one, with getnodestate command (legacy, not supported).
     * Version 1 is for the getnodestate2 command ((legacy, not supported).
     * Version 2 is for the getnodestate3 command
     * Version 3 adds support for setdistributionstates
     */
    private int version;

    private Map<Integer, ClusterState> systemStateVersionSent = new TreeMap<>();
    private ClusterState systemStateVersionAcknowledged;
    /**
     * When a node goes from an up state to a down state, update this flag with the start timestamp the node had before going down.
     * The cluster state broadcaster will use this to identify whether distributors have restarted.
     */
    private long wentDownWithStartTime = 0;
    private ClusterState wentDownAtClusterState;

    private long transitionTime = -1;
    private long initProgressTime = -1;
    private long upStableStateTime = -1;
    private long downStableStateTime = -1;

    private int prematureCrashCount = 0;

    /** Remember last time we adjusted version, such that if we had multiple requests pending when we did, we can avoid printing error right after. */
    private long adjustedVersionTime = 0;

    private HostInfo hostInfo = HostInfo.createHostInfo("{}");

    private Group group;

    // NOTE: See update(node) below
    NodeInfo(ContentCluster cluster, Node n, boolean configuredRetired, String rpcAddress, Distribution distribution) {
        if (cluster == null) throw new IllegalArgumentException("Cluster not set");
        reportedState = new NodeState(n.getType(), State.DOWN);
        wantedState = new NodeState(n.getType(), State.UP);
        this.cluster = cluster;
        this.node = n;
        this.connectionAttemptCount = 0;
        this.timeOfFirstFailingConnectionAttempt = 0;
        this.version = getLatestVersion();
        this.connectionVersion = getLatestVersion();
        this.configuredRetired = configuredRetired;
        this.recentlyObservedUnstableDuringInit = false;
        this.rpcAddress = rpcAddress;
        this.lastSeenInSlobrok = null;
        this.nextAttemptTime = 0;
        setGroup(distribution);
    }

    public void setRpcAddress(String rpcAddress) {
        this.rpcAddress = rpcAddress;
        resetConnectionInformation();
    }

    private void resetConnectionInformation() {
        this.lastSeenInSlobrok = null;
        this.nextAttemptTime = 0;
        this.version = getLatestVersion();
        this.connectionVersion = getLatestVersion();
    }

    public long getWentDownWithStartTime() { return wentDownWithStartTime; }

    public long getStartTimestamp() { return cluster.getStartTimestamp(node); }
    public void setStartTimestamp(long ts) { cluster.setStartTimestamp(node, ts); }

    public void setTransitionTime(long time) { transitionTime = time; }
    public long getTransitionTime() { return transitionTime; }

    public void setInitProgressTime(long time) { initProgressTime = time; }
    public long getInitProgressTime() { return initProgressTime; }

    public long getUpStableStateTime() { return upStableStateTime; }

    public long getDownStableStateTime() { return downStableStateTime; }

    public int getConnectionAttemptCount() { return connectionAttemptCount; }

    public boolean recentlyObservedUnstableDuringInit() {
        return recentlyObservedUnstableDuringInit;
    }
    public void setRecentlyObservedUnstableDuringInit(boolean unstable) {
        recentlyObservedUnstableDuringInit = unstable;
    }

    public void setPrematureCrashCount(int count) {
        if (count == 0) {
            recentlyObservedUnstableDuringInit = false;
        }
        if (prematureCrashCount != count) {
            prematureCrashCount = count;
            log.log(LogLevel.DEBUG, "Premature crash count on " + toString() + " set to " + count);
        }
    }
    public int getPrematureCrashCount() { return prematureCrashCount; }

    public boolean isPendingGetNodeStateRequest(GetNodeStateRequest r) {
        for(Pair<GetNodeStateRequest, Long> it : pendingNodeStateRequests) {
            if (it.getFirst() == r) return true;
        }
        return false;
    }

    public void setConfiguredRetired(boolean retired) {
        this.configuredRetired = retired;
    }

    public void setNextGetStateAttemptTime(long timeInMillis) {
        nextAttemptTime = timeInMillis;
    }

    // TODO: This implements hashCode and compareTo, but not equals ... that's odd

    @Override
    public int compareTo(NodeInfo info) {
        return node.compareTo(info.node);
    }

    @Override
    public int hashCode() {
        return node.hashCode();
    }

    @Override
    public String toString() { return node.toString(); }

    public void setGroup(Distribution distribution) {
        this.group = null;
        if (distribution != null) {
            this.group = distribution.getRootGroup().getGroupForNode(node.getIndex());
        }
    }

    public Group getGroup() {
        return group;
    }

    public int getLatestVersion() {
        return RPCCommunicator.SET_DISTRIBUTION_STATES_RPC_VERSION;
    }

    public String getSlobrokAddress() {
        return "storage/cluster." + cluster.getName() + "/" + node.getType() + "/" + node.getIndex();
    }

    public void markRpcAddressOutdated(Timer timer) {
        lastSeenInSlobrok = timer.getCurrentTimeInMillis();
    }
    public void markRpcAddressLive() {
        lastSeenInSlobrok = null;
    }

    public Node getNode() { return node; }

    public boolean isDistributor() {
        return node.getType().equals(NodeType.DISTRIBUTOR);
    }

    public boolean isStorage() {
        return node.getType().equals(NodeType.STORAGE);
    }

    public int getNodeIndex() {
        return node.getIndex();
    }

    public ContentCluster getCluster() { return cluster; }

    /** Returns true if the node is currentl registered in slobrok */
    // FIXME why is this called "isRpcAddressOutdated" then???
    public boolean isRpcAddressOutdated() { return lastSeenInSlobrok != null; }

    public Long getRpcAddressOutdatedTimestamp() { return lastSeenInSlobrok; }

    public void abortCurrentNodeStateRequests() {
        for(Pair<GetNodeStateRequest, Long> it : pendingNodeStateRequests) {
            it.getFirst().abort();
        }
        pendingNodeStateRequests.clear();
    }

    public void setCurrentNodeStateRequest(GetNodeStateRequest r, long timeInMS) {
        pendingNodeStateRequests.add(new Pair<>(r, timeInMS));
    }

    public String getRpcAddress() { return rpcAddress; }

    public NodeState getReportedState() { return reportedState; }

    /** Returns the wanted state of this node - which can either be set by a user or configured */
    public NodeState getWantedState() {
        NodeState retiredState = new NodeState(node.getType(), State.RETIRED);
        // Don't let configure retired state override explicitly set Down and Maintenance.
        if (configuredRetired && wantedState.above(retiredState)) {
            return retiredState;
        }
        return wantedState;
    }

    /** Returns the wanted state set directly by a user (i.e not configured) */
    public NodeState getUserWantedState() { return wantedState; }

    public long getTimeOfFirstFailingConnectionAttempt() {
        return timeOfFirstFailingConnectionAttempt;
    }

    public Long getLatestNodeStateRequestTime() {
        if (pendingNodeStateRequests.isEmpty()) return null;
        return pendingNodeStateRequests.get(pendingNodeStateRequests.size() - 1).getSecond();
    }

    public void setTimeOfFirstFailingConnectionAttempt(long timeInMS) {
        if (timeOfFirstFailingConnectionAttempt == 0) {
            timeOfFirstFailingConnectionAttempt = timeInMS;
        }
    }

    public void removePendingGetNodeStateRequest(GetNodeStateRequest request) {
        for (int i=0, n=pendingNodeStateRequests.size(); i<n; ++i) {
            if (pendingNodeStateRequests.get(i).getFirst() == request) {
                pendingNodeStateRequests.remove(i);
                break;
            }
        }
    }

    public void setReportedState(NodeState state, long time) {
        if (state == null) {
            state = new NodeState(node.getType(), State.DOWN);
        }
        if (state.getState().oneOf("dsm") && !reportedState.getState().oneOf("dsm")) {
            wentDownWithStartTime = reportedState.getStartTimestamp();
            wentDownAtClusterState = getNewestSystemStateSent();
            log.log(LogLevel.DEBUG, "Setting going down timestamp of node " + node + " to " + wentDownWithStartTime);
        }
        if (state.getState().equals(State.DOWN) && !reportedState.getState().oneOf("d")) {
            downStableStateTime = time;
            log.log(LogLevel.DEBUG, "Down stable state on " + toString() + " altered to " + time);
            if (reportedState.getState() == State.INITIALIZING) {
                recentlyObservedUnstableDuringInit = true;
            }
        } else if (state.getState().equals(State.UP) && !reportedState.getState().oneOf("u")) {
            upStableStateTime = time;
            log.log(LogLevel.DEBUG, "Up stable state on " + toString() + " altered to " + time);
        }
        if (!state.getState().validReportedNodeState(node.getType())) {
            throw new IllegalStateException("Trying to set illegal reported node state: " + state);
        }
        if (state.getState().oneOf("sd")) {
                // If we have multiple descriptions, assume that the first one happening after a node goes down is the most interesting one
            if (!reportedState.getState().oneOf("ui") && reportedState.hasDescription()) {
                state.setDescription(reportedState.getDescription());
            }
            reportedState = state;
            if (connectionAttemptCount < Integer.MAX_VALUE) {
                ++connectionAttemptCount;
            }
            if (connectionAttemptCount < 5) {
                nextAttemptTime = time + 100;
            } else if (connectionAttemptCount < 20) {
                nextAttemptTime = time + 250;
            } else if (connectionAttemptCount < 100) {
                nextAttemptTime = time + 1000;
            } else {
                nextAttemptTime = time + 5000;
            }
            log.log(LogLevel.SPAM, "Failed to get state from node " + toString() + ", scheduling next attempt in " + (nextAttemptTime - time) + " ms.");
        } else {
            connectionAttemptCount = 0;
            timeOfFirstFailingConnectionAttempt = 0;
            reportedState = state;
            if (version == 0 || state.getState().equals(State.STOPPING)) {
                nextAttemptTime = time + cluster.getPollingFrequency();
                log.log(LogLevel.SPAM, "Scheduling next attempt to get state from " + toString() + " in " + (nextAttemptTime - time) + " ms (polling freq).");
            } else {
                nextAttemptTime = time;
            }
        }
        log.log(LogLevel.SPAM, "Set reported state of node " + this + " to " + reportedState + ". Next connection attempt is at " + nextAttemptTime);
    }

    /** Sets the wanted state. The wanted state is taken as UP if a null argument is given */
    public void setWantedState(NodeState state) {
        if (state == null)
            state = new NodeState(node.getType(), State.UP);
        NodeState newWanted = new NodeState(node.getType(), state.getState());
        newWanted.setDescription(state.getDescription());
        if (!newWanted.equals(state)) {
            try{
                throw new Exception();
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log.warning("Attempted to set wanted state with more than just a main state. Extra data stripped. Original data '" + state.serialize(true) + ":\n" + sw.toString());
            }
        }
        wantedState = newWanted;
        log.log(LogLevel.SPAM, "Set wanted state of node " + this + " to " + wantedState + ".");
    }

    public long getTimeForNextStateRequestAttempt() {
        return nextAttemptTime;
    }

    /** @return True if we demoted communication version so this can be valid error. */
    public boolean notifyNoSuchMethodError(String methodName, Timer timer) {
        if (methodName.equals(RPCCommunicator.SET_DISTRIBUTION_STATES_RPC_METHOD_NAME)) {
            if (version == RPCCommunicator.SET_DISTRIBUTION_STATES_RPC_VERSION) {
                downgradeToRpcVersion(RPCCommunicator.LEGACY_SET_SYSTEM_STATE2_RPC_VERSION, methodName, timer);
                return true;
            } else if (timer.getCurrentTimeInMillis() - 2000 < adjustedVersionTime) {
                log.log(LogLevel.DEBUG, () -> "Node " + toString() + " does not support " + methodName + " call. Version already downgraded, so ignoring it.");
                return true;
            }
        }
        log.log(LogLevel.WARNING, "Node " + toString() + " does not support " + methodName + " which it should.");
        return false;
    }

    private void downgradeToRpcVersion(int newVersion, String methodName, Timer timer) {
        log.log(LogLevel.DEBUG, () -> String.format("Node %s does not support %s call. Downgrading to version %d.",
                toString(), methodName, newVersion));
        version = newVersion;
        nextAttemptTime = 0;
        adjustedVersionTime = timer.getCurrentTimeInMillis();
    }

    public Target getConnection() {
        return connection;
    }

    public Target setConnection(Target t) {
        this.connection = t;
        this.connectionVersion = getLatestVersion();
        return t;
    }

    public int getVersion() { return version; }
    public int getConnectionVersion() { return connectionVersion; }
    public void setConnectionVersion(int version) { connectionVersion = version; }

    public ClusterState getNewestSystemStateSent() {
        ClusterState last = null;
        for (ClusterState s : systemStateVersionSent.values()) {
            if (last == null || last.getVersion() < s.getVersion()) {
                last = s;
            }
        }
        return last;
    }
    public int getNewestSystemStateVersionSent() {
        ClusterState last = getNewestSystemStateSent();
        return last == null ? -1 : last.getVersion();
    }
    public int getSystemStateVersionAcknowledged() {
        return (systemStateVersionAcknowledged == null ? -1 : systemStateVersionAcknowledged.getVersion());
    }
    public void setSystemStateVersionSent(ClusterState state) {
        if (state == null) throw new Error("Should not clear info for last version sent");
        if (systemStateVersionSent.containsKey(state.getVersion())) {
            throw new IllegalStateException("We have already sent cluster state version " + state.getVersion() + " to " + node);
        }
        systemStateVersionSent.put(state.getVersion(), state);
    }
    public void setSystemStateVersionAcknowledged(Integer version, boolean success) {
        if (version == null) throw new Error("Should not clear info for last version acked");
        if (!systemStateVersionSent.containsKey(version)) {
            throw new IllegalStateException("Got response for cluster state " + version + " which is not tracked as pending for node " + node);
        }
        ClusterState state = systemStateVersionSent.remove(version);
        if (success && (systemStateVersionAcknowledged == null || systemStateVersionAcknowledged.getVersion() < state.getVersion())) {
            systemStateVersionAcknowledged = state;
            if (wentDownWithStartTime != 0
                && (wentDownAtClusterState == null || wentDownAtClusterState.getVersion() < state.getVersion())
                && !state.getNodeState(node).getState().oneOf("dsm"))
            {
                log.log(LogLevel.DEBUG, "Clearing going down timestamp of node " + node + " after receiving ack of cluster state " + state);
                wentDownWithStartTime = 0;
            }
        }
    }

    public void setHostInfo(HostInfo hostInfo) {
        // Note: This will blank out any hostInfo we already had, if the parsing fails.
        // This is intentional, to make sure we're never left with stale data.
        this.hostInfo = hostInfo;
    }

    public HostInfo getHostInfo() { return hostInfo; }

    /**
     * @return vtag if set or null otherwise and on errors.
     */
    public String getVtag() {
        return hostInfo.getVtag().getVersionOrNull();
    }
}
