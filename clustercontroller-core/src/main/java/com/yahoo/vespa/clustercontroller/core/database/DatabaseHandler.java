// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.FleetControllerContext;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.Timer;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;
import org.apache.zookeeper.KeeperException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data store for the cluster controller.
 * The data is stored and distributed by ZooKeeper.
 */
public class DatabaseHandler {

    private static final Logger logger = Logger.getLogger(DatabaseHandler.class.getName());

    public interface DatabaseContext {
        ContentCluster getCluster();
        FleetController getFleetController();
        NodeListener getNodeStateUpdateListener();
    }

    private static class Data {
        Integer masterVote;
        Integer lastSystemStateVersion;
        Map<Node, NodeState> wantedStates;
        Map<Node, Long> startTimestamps;
        ClusterStateBundle clusterStateBundle;

        void clear() {
            clearNonClusterStateFields();
            lastSystemStateVersion = null;
            clusterStateBundle = null;
        }

        void clearNonClusterStateFields() {
            masterVote = null;
            wantedStates = null;
            startTimestamps = null;
        }
    }
    private class DatabaseListener implements Database.DatabaseListener {
        public void handleZooKeeperSessionDown() {
            fleetControllerContext.log(logger, Level.FINE, () -> "Lost contact with zookeeper server");
            synchronized(monitor) {
                lostZooKeeperConnectionEvent = true;
                monitor.notifyAll();
            }
        }

        public void handleMasterData(Map<Integer, Integer> data) {
            synchronized (monitor) {
                if (masterDataEvent != null && masterDataEvent.equals(data)) {
                    fleetControllerContext.log(logger, Level.FINE, () -> "New master data was the same as the last one. Not responding to it");
                } else {
                    masterDataEvent = data;
                }
                monitor.notifyAll();
            }
        }
    }

    private final FleetControllerContext fleetControllerContext;
    private final DatabaseFactory databaseFactory;
    private final Timer timer;
    private final Object monitor;
    private String zooKeeperAddress;
    private int zooKeeperSessionTimeout = 5000;
    private final Object databaseMonitor = new Object();
    private Database database;

    private final DatabaseListener dbListener = new DatabaseListener();
    private final Data currentlyStored = new Data();
    private final Data pendingStore = new Data();
    private int lastKnownStateBundleVersionWrittenBySelf = -1;
    private long lastZooKeeperConnectionAttempt = 0;
    private int minimumWaitBetweenFailedConnectionAttempts = 10000;
    private boolean lostZooKeeperConnectionEvent = false;
    private Map<Integer, Integer> masterDataEvent = null;

    public DatabaseHandler(FleetControllerContext fleetControllerContext,
                           DatabaseFactory databaseFactory,
                           Timer timer,
                           String zooKeeperAddress,
                           Object monitor) {
        this.fleetControllerContext = fleetControllerContext;
        this.databaseFactory = databaseFactory;
        this.timer = timer;
        pendingStore.masterVote = fleetControllerContext.id().index(); // To begin with we'll vote for ourselves.
        this.monitor = monitor;
        this.zooKeeperAddress = Objects.requireNonNull(zooKeeperAddress, "zooKeeperAddress cannot be null");
    }

    private boolean isDatabaseClosedSafe() {
        synchronized (databaseMonitor) {
            return isClosed();
        }
    }

    public void shutdown(DatabaseContext databaseContext) {
        relinquishDatabaseConnectivity(databaseContext);
    }

    public boolean isClosed() { return database == null || database.isClosed(); }

    public int getLastKnownStateBundleVersionWrittenBySelf() {
        return lastKnownStateBundleVersionWrittenBySelf;
    }

    public void setMinimumWaitBetweenFailedConnectionAttempts(int minimumWaitBetweenFailedConnectionAttempts) {
        this.minimumWaitBetweenFailedConnectionAttempts = minimumWaitBetweenFailedConnectionAttempts;
    }

    public void reset(DatabaseContext databaseContext) {
        final boolean wasRunning;
        synchronized (databaseMonitor) {
            wasRunning = database != null;
            if (wasRunning) {
                fleetControllerContext.log(logger, Level.INFO, "Resetting database state");
                database.close();
                database = null;
            }
        }
        clearSessionMetaData(true);
        databaseContext.getFleetController().lostDatabaseConnection();

        if (wasRunning) {
            fleetControllerContext.log(logger, Level.INFO, "Done resetting database state");
        }
    }

    private void clearSessionMetaData(boolean clearPendingStateWrites) {
        // Preserve who we want to vote for
        Integer currentVote = (pendingStore.masterVote != null ? pendingStore.masterVote : currentlyStored.masterVote);
        currentlyStored.clear();
        if (clearPendingStateWrites) {
            pendingStore.clear();
        } else {
            // If we have pending cluster state writes we cannot drop these on the floor, as otherwise the
            // core CC logic may keep thinking it has persisted writes it really has not. Clearing pending
            // state writes would also prevent the controller from detecting itself being out of sync by
            // triggering CaS violations upon znode writes.
            pendingStore.clearNonClusterStateFields();
        }
        pendingStore.masterVote = currentVote;
        fleetControllerContext.log(logger, Level.FINE, () -> "Cleared session metadata. Pending master vote is now " + pendingStore.masterVote);
    }

    public void setZooKeeperAddress(String address, DatabaseContext databaseContext) {
        Objects.requireNonNull(address, "address cannot be null");
        if (address.equals(zooKeeperAddress)) return;
        fleetControllerContext.log(logger, Level.INFO, "Got new ZooKeeper address to use: " + address);
        zooKeeperAddress = address;
        reset(databaseContext);
    }

    public void setZooKeeperSessionTimeout(int timeout, DatabaseContext databaseContext) {
        if (timeout == zooKeeperSessionTimeout) return;
        fleetControllerContext.log(logger, Level.FINE, () -> "Got new ZooKeeper session timeout of " + timeout + " milliseconds.");
        zooKeeperSessionTimeout = timeout;
        reset(databaseContext);
    }

    private void connect(long currentTime) {
        try {
            lastZooKeeperConnectionAttempt = currentTime;
            synchronized (databaseMonitor) {
                if (database != null) {
                    database.close();
                }
                // We still hold the database lock while calling this, we want to block callers.
                // Don't clear pending state writes in case they were attempted prior to connect()
                // being called, but after receiving a database loss event.
                clearSessionMetaData(false);
                fleetControllerContext.log(logger, Level.INFO, "Setting up new ZooKeeper session at " + zooKeeperAddress);
                DatabaseFactory.Params params = new DatabaseFactory
                        .Params()
                        .databaseAddress(zooKeeperAddress)
                        .databaseSessionTimeout(zooKeeperSessionTimeout)
                        .databaseListener(dbListener);
                database = databaseFactory.create(params);
            }
        } catch (KeeperException.NodeExistsException e) {
            fleetControllerContext.log(logger, Level.FINE, () -> "Cannot create ephemeral fleetcontroller node. ZooKeeper server "
                                                                 + "not seen old fleetcontroller instance disappear? It already exists. Will retry later: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (KeeperException.ConnectionLossException e) {
            fleetControllerContext.log(logger, Level.WARNING, "Failed to connect to ZooKeeper at " + zooKeeperAddress
                                                              + " with session timeout " + zooKeeperSessionTimeout + ": " + e.getMessage());
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            fleetControllerContext.log(logger, Level.WARNING, "Failed to connect to ZooKeeper at " + zooKeeperAddress
                                                              + " with session timeout " + zooKeeperSessionTimeout + ": " + sw);
        }
        fleetControllerContext.log(logger, Level.INFO, "Done setting up new ZooKeeper session at " + zooKeeperAddress);
    }

    /**
     * This is called to attempt the next task against ZooKeeper we want to try.
     *
     * @return true if we did or attempted any work.
     */
    public boolean doNextZooKeeperTask(DatabaseContext databaseContext) {
        boolean didWork = false;
        synchronized (monitor) {
            if (lostZooKeeperConnectionEvent) {
                fleetControllerContext.log(logger, Level.FINE, () -> "doNextZooKeeperTask(): lost connection");
                databaseContext.getFleetController().lostDatabaseConnection();
                lostZooKeeperConnectionEvent = false;
                didWork = true;
                if (masterDataEvent != null) {
                    fleetControllerContext.log(logger, Level.FINE, () -> "Had new master data queued on disconnect. Removing master data event");
                    masterDataEvent = null;
                }
            }
            if (masterDataEvent != null) {
                fleetControllerContext.log(logger, Level.FINE, () -> "doNextZooKeeperTask(): new master data");
                if (!masterDataEvent.containsKey(fleetControllerContext.id().index())) {
                    Integer currentVote = (pendingStore.masterVote != null ? pendingStore.masterVote : currentlyStored.masterVote);
                    assert(currentVote != null);
                    masterDataEvent.put(fleetControllerContext.id().index(), currentVote);
                }
                databaseContext.getFleetController().handleFleetData(masterDataEvent);
                masterDataEvent = null;
                didWork = true;
            }
        }
        if (isDatabaseClosedSafe()) {
            long currentTime = timer.getCurrentTimeInMillis();
            if (currentTime - lastZooKeeperConnectionAttempt < minimumWaitBetweenFailedConnectionAttempts) {
                return false; // Not time to attempt connection yet.
            }
            didWork = true;
            connect(currentTime);
        }
        try {
            synchronized (databaseMonitor) {
                if (database == null || database.isClosed()) {
                    return didWork;
                }
                didWork |= performZooKeeperWrites();
            }
        } catch (CasWriteFailed e) {
            fleetControllerContext.log(logger, Level.WARNING, String.format("CaS write to ZooKeeper failed, another controller " +
                                                                            "has likely taken over ownership: %s", e.getMessage()));
            // Clear DB and master election state. This shall trigger a full re-fetch of all
            // version and election-related metadata.
            relinquishDatabaseConnectivity(databaseContext);
        }
        return didWork;
    }

    private void relinquishDatabaseConnectivity(DatabaseContext databaseContext) {
        // reset() will handle both session clearing and trigger a database loss callback into the CC.
        reset(databaseContext);
    }

    private boolean performZooKeeperWrites() {
        boolean didWork = false;
        if (pendingStore.masterVote != null) {
            didWork = true;
            fleetControllerContext.log(logger, Level.FINE, () -> "Attempting to store master vote "
                                                                 + pendingStore.masterVote + " into zookeeper.");
            if (database.storeMasterVote(pendingStore.masterVote)) {
                fleetControllerContext.log(logger, Level.FINE, () -> "Managed to store master vote "
                                                                     + pendingStore.masterVote + " into zookeeper.");
                currentlyStored.masterVote = pendingStore.masterVote;
                pendingStore.masterVote = null;
            } else {
                fleetControllerContext.log(logger, Level.WARNING, "Failed to store master vote");
                return true;
            }
        }
        if (pendingStore.lastSystemStateVersion != null) {
            didWork = true;
            fleetControllerContext.log(logger, Level.FINE, () -> "Attempting to store last system state version " +
                                                                 pendingStore.lastSystemStateVersion + " into zookeeper.");
            if (database.storeLatestSystemStateVersion(pendingStore.lastSystemStateVersion)) {
                currentlyStored.lastSystemStateVersion = pendingStore.lastSystemStateVersion;
                pendingStore.lastSystemStateVersion = null;
            } else {
                return true;
            }
        }
        if (pendingStore.startTimestamps != null) {
            didWork = true;
            fleetControllerContext.log(logger, Level.FINE, () -> "Attempting to store " + pendingStore.startTimestamps.size() +
                                                                 " start timestamps into zookeeper.");
            if (database.storeStartTimestamps(pendingStore.startTimestamps)) {
                currentlyStored.startTimestamps = pendingStore.startTimestamps;
                pendingStore.startTimestamps = null;
            } else {
                return true;
            }
        }
        if (pendingStore.wantedStates != null) {
            didWork = true;
            fleetControllerContext.log(logger, Level.FINE, () -> "Attempting to store "
                                                                 + pendingStore.wantedStates.size() + " wanted states into zookeeper.");
            if (database.storeWantedStates(pendingStore.wantedStates)) {
                currentlyStored.wantedStates = pendingStore.wantedStates;
                pendingStore.wantedStates = null;
            } else {
                return true;
            }
        }
        if (pendingStore.clusterStateBundle != null) {
            didWork = true;
            fleetControllerContext.log(logger, Level.FINE, () -> "Attempting to store last cluster state bundle with version " +
                                                                 pendingStore.clusterStateBundle.getVersion() + " into zookeeper.");
            if (database.storeLastPublishedStateBundle(pendingStore.clusterStateBundle)) {
                lastKnownStateBundleVersionWrittenBySelf = pendingStore.clusterStateBundle.getVersion();
                currentlyStored.clusterStateBundle = pendingStore.clusterStateBundle;
                pendingStore.clusterStateBundle = null;
            } else {
                return true;
            }
        }
        return didWork;
    }

    public void setMasterVote(DatabaseContext databaseContext, int wantedMasterCandidate) {
        fleetControllerContext.log(logger, Level.FINE, () -> "Checking if master vote has been updated and need to be stored.");
        // Schedule a write if one of the following is true:
        //   - There is already a pending vote to be written, that may have been written already without our knowledge
        //   - We don't know what is actually stored now
        //   - The value is different from the value we know is stored.
        if (pendingStore.masterVote != null || currentlyStored.masterVote == null
            || currentlyStored.masterVote != wantedMasterCandidate)
        {
            fleetControllerContext.log(logger, Level.FINE, () -> "Scheduling master vote " + wantedMasterCandidate + " to be stored in zookeeper.");
            pendingStore.masterVote = wantedMasterCandidate;
            doNextZooKeeperTask(databaseContext);
        }
    }

    public void saveLatestSystemStateVersion(DatabaseContext databaseContext, int version) {
        fleetControllerContext.log(logger, Level.FINE, () -> "Checking if latest system state version has been updated and need to be stored.");
        // Schedule a write if one of the following is true:
        //   - There is already a pending vote to be written, that may have been written already without our knowledge
        //   - We don't know what is actually stored now
        //   - The value is different from the value we know is stored.
        if (pendingStore.lastSystemStateVersion != null || currentlyStored.lastSystemStateVersion == null
            || currentlyStored.lastSystemStateVersion != version)
        {
            fleetControllerContext.log(logger, Level.FINE, () -> "Scheduling new last system state version " + version + " to be stored in zookeeper.");
            pendingStore.lastSystemStateVersion = version;
            doNextZooKeeperTask(databaseContext);
        }
    }

    public int getLatestSystemStateVersion() {
        fleetControllerContext.log(logger, Level.FINE, () -> "Retrieving latest system state version.");
        synchronized (databaseMonitor) {
            if (database != null) {
                currentlyStored.lastSystemStateVersion = database.retrieveLatestSystemStateVersion();
            }
        }
        Integer version = currentlyStored.lastSystemStateVersion;
        if (version == null) {
            fleetControllerContext.log(logger, Level.WARNING, "Failed to retrieve latest system state version from ZooKeeper. Returning version 0.");
            return 0; // FIXME "fail-oblivious" is not a good error handling mode for such a critical component!
        }
        return version;
    }

    public void saveLatestClusterStateBundle(DatabaseContext databaseContext, ClusterStateBundle clusterStateBundle) {
        fleetControllerContext.log(logger, Level.FINE, () -> "Scheduling bundle " + clusterStateBundle + " to be saved to ZooKeeper");
        pendingStore.clusterStateBundle = clusterStateBundle;
        doNextZooKeeperTask(databaseContext);
    }

    // TODO should we expand this to cover _any_ pending ZK write?
    public boolean hasPendingClusterStateMetaDataStore() {
        synchronized (databaseMonitor) {
            return ((pendingStore.clusterStateBundle != null) ||
                    (pendingStore.lastSystemStateVersion != null));
        }
    }

    public ClusterStateBundle getLatestClusterStateBundle() {
        fleetControllerContext.log(logger, Level.FINE, () -> "Retrieving latest cluster state bundle from ZooKeeper");
        synchronized (databaseMonitor) {
            if (database != null && !database.isClosed()) {
                return database.retrieveLastPublishedStateBundle();
            } else {
                return ClusterStateBundle.empty();
            }
        }
    }

    public boolean saveWantedStates(DatabaseContext databaseContext) {
        fleetControllerContext.log(logger, Level.FINE, () -> "Checking whether wanted states have changed compared to zookeeper version.");
        Map<Node, NodeState> wantedStates = new TreeMap<>();
        for (NodeInfo info : databaseContext.getCluster().getNodeInfos()) {
            if (!info.getUserWantedState().equals(new NodeState(info.getNode().getType(), State.UP))) {
                wantedStates.put(info.getNode(), info.getUserWantedState());
            }
        }
        // Schedule a write if one of the following is true:
        //   - There are already a pending vote to be written, that may have been written already without our knowledge
        //   - We don't know what is actually stored now
        //   - The value is different from the value we know is stored.
        if (pendingStore.wantedStates != null || currentlyStored.wantedStates == null
                || !currentlyStored.wantedStates.equals(wantedStates))
        {
            fleetControllerContext.log(logger, Level.FINE, () -> "Scheduling new wanted states to be stored into zookeeper.");
            pendingStore.wantedStates = wantedStates;
            doNextZooKeeperTask(databaseContext);
            return true;
        } else {
            return false;
        }
    }

    public boolean loadWantedStates(DatabaseContext databaseContext) {
        fleetControllerContext.log(logger, Level.FINE, () -> "Retrieving node wanted states.");
        synchronized (databaseMonitor) {
            if (database != null && !database.isClosed()) {
                currentlyStored.wantedStates = database.retrieveWantedStates();
            }
        }
        Map<Node, NodeState> wantedStates = currentlyStored.wantedStates;
        if (wantedStates == null) {
            // We get here if the ZooKeeper client has lost connection to ZooKeeper.
            // TODO: Should instead fail the tick until connected!?
            fleetControllerContext.log(logger, Level.FINE, () -> "Failed to retrieve wanted states from ZooKeeper. Assuming UP for all nodes.");
            wantedStates = new TreeMap<>();
        }
        boolean altered = false;
        for (Node node : wantedStates.keySet()) {
            NodeInfo nodeInfo = databaseContext.getCluster().getNodeInfo(node);
            if (nodeInfo == null) {
                databaseContext.getNodeStateUpdateListener().handleRemovedNode(node);
                altered = true;
                continue;
            }
            NodeState wantedState = wantedStates.get(node);
            if ( ! nodeInfo.getUserWantedState().equals(wantedState)) {
                nodeInfo.setWantedState(wantedState);
                databaseContext.getNodeStateUpdateListener().handleNewWantedNodeState(nodeInfo, wantedState);
                altered = true;
            }
            fleetControllerContext.log(logger, Level.FINE, () -> "Node " + node + " has wanted state " + wantedState);
        }

        // Remove wanted state from any node having a wanted state set that is no longer valid
        for (NodeInfo info : databaseContext.getCluster().getNodeInfos()) {
            NodeState wantedState = wantedStates.get(info.getNode());
            if (wantedState == null && !info.getUserWantedState().equals(new NodeState(info.getNode().getType(), State.UP))) {
                info.setWantedState(null);
                databaseContext.getNodeStateUpdateListener().handleNewWantedNodeState(info, info.getWantedState().clone());
                altered = true;
            }
        }
        return altered;
    }

    public void saveStartTimestamps(DatabaseContext databaseContext) {
        fleetControllerContext.log(logger, Level.FINE, () -> "Scheduling start timestamps to be stored into zookeeper.");
        pendingStore.startTimestamps = databaseContext.getCluster().getStartTimestamps();
        doNextZooKeeperTask(databaseContext);
    }

    public boolean loadStartTimestamps(ContentCluster cluster) {
        fleetControllerContext.log(logger, Level.FINE, () -> "Retrieving start timestamps");
        synchronized (databaseMonitor) {
            if (database == null || database.isClosed()) {
                return false;
            }
            currentlyStored.startTimestamps = database.retrieveStartTimestamps();
        }
        Map<Node, Long> startTimestamps = currentlyStored.startTimestamps;
        if (startTimestamps == null) {
            fleetControllerContext.log(logger, Level.WARNING, "Failed to retrieve start timestamps from ZooKeeper. Cluster state will be bloated with timestamps until we get them set.");
            startTimestamps = new TreeMap<>();
        }
        for (Map.Entry<Node, Long> e : startTimestamps.entrySet()) {
            cluster.setStartTimestamp(e.getKey(), e.getValue());
            fleetControllerContext.log(logger, Level.FINE, () -> "Node " + e.getKey() + " has start timestamp " + e.getValue());
        }
        return true;
    }

}
