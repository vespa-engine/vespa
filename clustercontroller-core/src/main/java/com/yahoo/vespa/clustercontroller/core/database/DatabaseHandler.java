// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.Timer;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeAddedOrRemovedListener;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;
import org.apache.zookeeper.KeeperException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Data store for the cluster controller.
 * The data is stored and distributed by ZooKeeper.
 */
public class DatabaseHandler {

    private static Logger log = Logger.getLogger(DatabaseHandler.class.getName());

    public interface Context {
        ContentCluster getCluster();
        FleetController getFleetController();
        NodeAddedOrRemovedListener getNodeAddedOrRemovedListener();
        NodeStateOrHostInfoChangeHandler getNodeStateUpdateListener();
    }

    private class Data {
        Integer masterVote;
        Integer lastSystemStateVersion;
        Map<Node, NodeState> wantedStates;
        Map<Node, Long> startTimestamps;

        void clear() {
            masterVote = null;
            lastSystemStateVersion = null;
            wantedStates = null;
            startTimestamps = null;
        }
    }
    private class DatabaseListener implements Database.DatabaseListener {
        public void handleZooKeeperSessionDown() {
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Lost contact with zookeeper server");
            synchronized(monitor) {
                lostZooKeeperConnectionEvent = true;
                monitor.notifyAll();
            }
        }

        public void handleMasterData(Map<Integer, Integer> data) {
            synchronized (monitor) {
                if (masterDataEvent != null && masterDataEvent.equals(data)) {
                    log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": New master data was the same as the last one. Not responding to it");
                } else {
                    masterDataEvent = data;
                }
                monitor.notifyAll();
            }
        }
    }

    private final Timer timer;
    private final int nodeIndex;
    private final Object monitor;
    private String zooKeeperAddress;
    private int zooKeeperSessionTimeout = 5000;
    private final Object databaseMonitor = new Object();

    /** This is always ZooKeeperDatabase */
    // TODO: Get rid of the interface as it is both unnecessary and gives a false impression of independence
    private Database database;

    private DatabaseListener dbListener = new DatabaseListener();
    private final Data currentlyStored = new Data();
    private final Data pendingStore = new Data();
    private long lastZooKeeperConnectionAttempt = 0;
    private static final int minimumWaitBetweenFailedConnectionAttempts = 10000;
    private boolean lostZooKeeperConnectionEvent = false;
    private Map<Integer, Integer> masterDataEvent = null;

    public DatabaseHandler(Timer timer, String zooKeeperAddress, int ourIndex, Object monitor) throws InterruptedException
    {
        this.timer = timer;
        this.nodeIndex = ourIndex;
        pendingStore.masterVote = ourIndex; // To begin with we'll vote for ourselves.
        this.monitor = monitor;
        this.zooKeeperAddress = zooKeeperAddress;
    }

    private boolean isDatabaseClosedSafe() {
        synchronized (databaseMonitor) {
            return database == null || database.isClosed();
        }
    }

    public void shutdown(FleetController fleetController) {
        reset();
        fleetController.lostDatabaseConnection();
    }

    public boolean isClosed() { return database == null || database.isClosed(); }

    public void reset() {
        final boolean wasRunning;
        synchronized (databaseMonitor) {
            wasRunning = database != null;
            if (wasRunning) {
                log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex + ": Resetting database state");
                database.close();
                database = null;
            }
        }
        clearSessionMetaData();

        if (wasRunning) {
            log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex + ": Done resetting database state");
        }
    }

    private void clearSessionMetaData() {
        // Preserve who we want to vote for
        Integer currentVote = (pendingStore.masterVote != null ? pendingStore.masterVote : currentlyStored.masterVote);
        currentlyStored.clear();
        pendingStore.clear();
        pendingStore.masterVote = currentVote;
        log.log(LogLevel.DEBUG, "Cleared session metadata. Pending master vote is now "
                    + pendingStore.masterVote);
    }

    public void setZooKeeperAddress(String address) {
        if (address == null && zooKeeperAddress == null) return;
        if (address != null && zooKeeperAddress != null && address.equals(zooKeeperAddress)) return;
        if (zooKeeperAddress != null) {
            log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex + ": " + (address == null ? "Stopped using ZooKeeper." : "Got new ZooKeeper address to use: " + address));
        }
        zooKeeperAddress = address;
        reset();
    }

    public void setZooKeeperSessionTimeout(int timeout) {
        if (timeout == zooKeeperSessionTimeout) return;
        log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Got new ZooKeeper session timeout of " + timeout + " milliseconds.");
        zooKeeperSessionTimeout = timeout;
        reset();
    }

    private boolean usingZooKeeper() { return (zooKeeperAddress != null); }

    private void connect(ContentCluster cluster, long currentTime) throws InterruptedException {
        try {
            lastZooKeeperConnectionAttempt = currentTime;
            synchronized (databaseMonitor) {
                if (database != null) {
                    database.close();
                }
                // We still hold the database lock while calling this, we want to block callers.
                clearSessionMetaData();
                log.log(LogLevel.INFO,
                        "Fleetcontroller " + nodeIndex + ": Setting up new ZooKeeper session at " + zooKeeperAddress);
                database = new ZooKeeperDatabase(cluster,
                        nodeIndex, zooKeeperAddress, zooKeeperSessionTimeout, dbListener);
            }
        } catch (KeeperException.NodeExistsException e) {
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Cannot create ephemeral fleetcontroller node. ZooKeeper server "
                    + "not seen old fleetcontroller instance disappear? It already exists. Will retry later: " + e.getMessage());
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (KeeperException.ConnectionLossException e) {
            log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Failed to connect to ZooKeeper at " + zooKeeperAddress
                    + " with session timeout " + zooKeeperSessionTimeout + ": " + e.getMessage());
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Failed to connect to ZooKeeper at " + zooKeeperAddress
                    + " with session timeout " + zooKeeperSessionTimeout + ": " + sw);
        }
        log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex + ": Done setting up new ZooKeeper session at " + zooKeeperAddress);
    }

    /**
     * This is called to attempt the next task against ZooKeeper we want to try.
     *
     * @return true if we did or attempted any work.
     */
    public boolean doNextZooKeeperTask(Context context) throws InterruptedException {
        boolean didWork = false;
        synchronized (monitor) {
            if (zooKeeperAddress == null) return false; // If not using zookeeper no work to be done
            if (lostZooKeeperConnectionEvent) {
                log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": doNextZooKeeperTask(): lost connection");
                context.getFleetController().lostDatabaseConnection();
                lostZooKeeperConnectionEvent = false;
                didWork = true;
                if (masterDataEvent != null) {
                    log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Had new master data queued on disconnect. Removing master data event");
                    masterDataEvent = null;
                }
            }
            if (masterDataEvent != null) {
                log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": doNextZooKeeperTask(): new master data");
                if (!masterDataEvent.containsKey(nodeIndex)) {
                    Integer currentVote = (pendingStore.masterVote != null ? pendingStore.masterVote : currentlyStored.masterVote);
                    assert(currentVote != null);
                    masterDataEvent.put(nodeIndex, currentVote);
                }
                context.getFleetController().handleFleetData(masterDataEvent);
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
            connect(context.getCluster(), currentTime);
        }
        synchronized (databaseMonitor) {
            if (database == null || database.isClosed()) {
                return didWork;
            }
            if (pendingStore.masterVote != null) {
                didWork = true;
                log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Attempting to store master vote "
                        + pendingStore.masterVote + " into zookeeper.");
                if (database.storeMasterVote(pendingStore.masterVote)) {
                    log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Managed to store master vote "
                            + pendingStore.masterVote + " into zookeeper.");
                    currentlyStored.masterVote = pendingStore.masterVote;
                    pendingStore.masterVote = null;
                } else {
                    log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Failed to store master vote");
                    return didWork;
                }
            }
            if (pendingStore.lastSystemStateVersion != null) {
                didWork = true;
                log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex
                        + ": Attempting to store last system state version " + pendingStore.lastSystemStateVersion
                        + " into zookeeper.");
                // TODO guard version write with a CaS predicated on the version we last read/wrote.
                // TODO Drop leadership status if there is a mismatch, as it implies we're racing with another leader.
                if (database.storeLatestSystemStateVersion(pendingStore.lastSystemStateVersion)) {
                    currentlyStored.lastSystemStateVersion = pendingStore.lastSystemStateVersion;
                    pendingStore.lastSystemStateVersion = null;
                } else {
                    return didWork;
                }
            }
            if (pendingStore.startTimestamps != null) {
                didWork = true;
                log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Attempting to store "
                        + pendingStore.startTimestamps.size() + " start timestamps into zookeeper.");
                if (database.storeStartTimestamps(pendingStore.startTimestamps)) {
                    currentlyStored.startTimestamps = pendingStore.startTimestamps;
                    pendingStore.startTimestamps = null;
                } else {
                    return didWork;
                }
            }
            if (pendingStore.wantedStates != null) {
                didWork = true;
                log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Attempting to store "
                        + pendingStore.wantedStates.size() + " wanted states into zookeeper.");
                if (database.storeWantedStates(pendingStore.wantedStates)) {
                    currentlyStored.wantedStates = pendingStore.wantedStates;
                    pendingStore.wantedStates = null;
                } else {
                    return didWork;
                }
            }
        }
        return didWork;
    }

    public void setMasterVote(Context context, int wantedMasterCandidate) throws InterruptedException {
        log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Checking if master vote has been updated and need to be stored.");
        // Schedule a write if one of the following is true:
        //   - There is already a pending vote to be written, that may have been written already without our knowledge
        //   - We don't know what is actually stored now
        //   - The value is different from the value we know is stored.
        if (pendingStore.masterVote != null || currentlyStored.masterVote == null
            || currentlyStored.masterVote != wantedMasterCandidate)
        {
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Scheduling master vote " + wantedMasterCandidate + " to be stored in zookeeper.");
            pendingStore.masterVote = wantedMasterCandidate;
            doNextZooKeeperTask(context);
        }
    }

    public void saveLatestSystemStateVersion(Context context, int version) throws InterruptedException {
        log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Checking if latest system state version has been updated and need to be stored.");
        // Schedule a write if one of the following is true:
        //   - There is already a pending vote to be written, that may have been written already without our knowledge
        //   - We don't know what is actually stored now
        //   - The value is different from the value we know is stored.
        if (pendingStore.lastSystemStateVersion != null || currentlyStored.lastSystemStateVersion == null
            || currentlyStored.lastSystemStateVersion != version)
        {
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Scheduling new last system state version " + version + " to be stored in zookeeper.");
            pendingStore.lastSystemStateVersion = version;
            doNextZooKeeperTask(context);
        }
    }

    public int getLatestSystemStateVersion() throws InterruptedException {
        log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Retrieving latest system state version.");
        synchronized (databaseMonitor) {
            if (database != null && !database.isClosed()) {
                currentlyStored.lastSystemStateVersion = database.retrieveLatestSystemStateVersion();
            }
        }
        Integer version = currentlyStored.lastSystemStateVersion;
        if (version == null) {
            if (usingZooKeeper()) {
                log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Failed to retrieve latest system state version from ZooKeeper. Returning version 0.");
            }
            return 0;
        }
        return version;
    }

    public void saveWantedStates(Context context) throws InterruptedException {
        log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Checking whether wanted states have changed compared to zookeeper version.");
        Map<Node, NodeState> wantedStates = new TreeMap<>();
        for (NodeInfo info : context.getCluster().getNodeInfo()) {
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
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Scheduling new wanted states to be stored into zookeeper.");
            pendingStore.wantedStates = wantedStates;
            doNextZooKeeperTask(context);
        }
    }

    public boolean loadWantedStates(Context context) throws InterruptedException {
        log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Retrieving node wanted states.");
        synchronized (databaseMonitor) {
            if (database != null && !database.isClosed()) {
                currentlyStored.wantedStates = database.retrieveWantedStates();
            }
        }
        Map<Node, NodeState> wantedStates = currentlyStored.wantedStates;
        if (wantedStates == null) {
            if (usingZooKeeper()) {
                log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Failed to retrieve wanted states from ZooKeeper. Assuming UP for all nodes.");
            }
            wantedStates = new TreeMap<>();
        }
        boolean altered = false;
        for (Node node : wantedStates.keySet()) {
            NodeInfo nodeInfo = context.getCluster().getNodeInfo(node);
            if (nodeInfo == null) continue; // ignore wanted state of nodes which doesn't exist
            NodeState wantedState = wantedStates.get(node);
            if ( ! nodeInfo.getUserWantedState().equals(wantedState)) {
                nodeInfo.setWantedState(wantedState);
                context.getNodeStateUpdateListener().handleNewWantedNodeState(nodeInfo, wantedState);
                altered = true;
            }
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Node " + node + " has wanted state " + wantedState);
        }

        // Remove wanted state from any node having a wanted state set that is no longer valid
        for (NodeInfo info : context.getCluster().getNodeInfo()) {
            NodeState wantedState = wantedStates.get(info.getNode());
            if (wantedState == null && !info.getUserWantedState().equals(new NodeState(info.getNode().getType(), State.UP))) {
                info.setWantedState(null);
                context.getNodeStateUpdateListener().handleNewWantedNodeState(info, info.getWantedState().clone());
                altered = true;
            }
        }
        return altered;
    }

    public void saveStartTimestamps(Context context) throws InterruptedException {
        log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Scheduling start timestamps to be stored into zookeeper.");
        pendingStore.startTimestamps = context.getCluster().getStartTimestamps();
        doNextZooKeeperTask(context);
    }

    public boolean loadStartTimestamps(ContentCluster cluster) throws InterruptedException {
        log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Retrieving start timestamps");
        synchronized (databaseMonitor) {
            if (database == null || database.isClosed()) return false;
            currentlyStored.startTimestamps = database.retrieveStartTimestamps();
        }
        Map<Node, Long> startTimestamps = currentlyStored.startTimestamps;
        if (startTimestamps == null) {
            if (usingZooKeeper()) {
                log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Failed to retrieve start timestamps from ZooKeeper. Cluster state will be bloated with timestamps until we get them set.");
            }
            startTimestamps = new TreeMap<>();
        }
        for (Map.Entry<Node, Long> e : startTimestamps.entrySet()) {
            cluster.setStartTimestamp(e.getKey(), e.getValue());
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Node " + e.getKey() + " has start timestamp " + e.getValue());
        }
        return true;
    }

}
