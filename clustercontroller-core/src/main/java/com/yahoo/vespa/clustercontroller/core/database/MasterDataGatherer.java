// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.*;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.*;

import java.util.logging.Level;

public class MasterDataGatherer {

    private static final Logger log = Logger.getLogger(MasterDataGatherer.class.getName());

    /** Utility function for getting node index from path name of the ephemeral nodes. */
    private static int getIndex(String nodeName) {
        assert(nodeName != null);
        int lastSlash = nodeName.lastIndexOf('/');
        if (lastSlash <= 1) {
            throw new IllegalArgumentException("Unexpected path to nodename: '" + nodeName + "'.");
        }
        return Integer.parseInt(nodeName.substring(lastSlash + 1));
    }

    private final ZooKeeperPaths paths;
    private Map<Integer, Integer> masterData = new TreeMap<>(); // The master state last reported to the fleetcontroller
    private final Map<Integer, Integer> nextMasterData = new TreeMap<>(); // Temporary master state while gathering new info from zookeeper
    private final AsyncCallback.ChildrenCallback childListener = new DirCallback(); // Dir change listener
    private final NodeDataCallback nodeListener = new NodeDataCallback(); // Ephemeral node data change listener

    private final Database.DatabaseListener listener;
    private final ZooKeeper session;
    private final int nodeIndex;

    private final Watcher changeWatcher = new ChangeWatcher();

    /**
     * This class is used to handle node children changed and node data changed events from the zookeeper server.
     * A run to fetch new master data starts with either of these changes, except for the first time on startup,
     * where the constructor triggers a run by requesting dir info, as it starts of knowing nothing.
     */
    private class ChangeWatcher implements Watcher {
        public void process(WatchedEvent watchedEvent) {
            switch (watchedEvent.getType()) {
                case NodeChildrenChanged: // Fleetcontrollers have either connected or disconnected to ZooKeeper
                    log.log(Level.INFO, "Fleetcontroller " + nodeIndex + ": A change occurred in the list of registered fleetcontrollers. Requesting new information");
                    session.getChildren(paths.indexesRoot(), this, childListener, null);
                    break;
                case NodeDataChanged: // A fleetcontroller has changed what node it is voting for
                    log.log(Level.INFO, "Fleetcontroller " + nodeIndex + ": Altered data in node " + watchedEvent.getPath() + ". Requesting new vote");
                    int index = getIndex(watchedEvent.getPath());
                    synchronized (nextMasterData) {
                        nextMasterData.put(index, null);
                    }
                    session.getData(paths.indexOf(index), this, nodeListener, null);
                    break;
                case NodeCreated: // How can this happen? Can one leave watches on non-existing nodes?
                    log.log(Level.WARNING, "Fleetcontroller " + nodeIndex + ": Got unexpected ZooKeeper event NodeCreated");
                    break;
                case NodeDeleted:
                        // We get this event when fleetcontrollers shut down and node in dir disappears. But it should also trigger a NodeChildrenChanged event, so
                        // ignoring this one.
                    log.log(Level.FINE, () -> "Fleetcontroller " + nodeIndex + ": Node deleted event gotten. Ignoring it, expecting a NodeChildrenChanged event too.");
                    break;
                case None:
                    log.log(Level.FINE, () -> "Fleetcontroller " + nodeIndex + ": Got ZooKeeper event None.");
            }
        }
    }

    /**
     * The dir callback class is responsible for handling dir change events (nodes coming up or going down).
     * It will explicitly request the contents of, and set a watch on, all nodes that are present. Nodes
     * for controllers that have disappeared from ZooKeeper are implicitly removed from nextMasterData.
     */
    private class DirCallback implements AsyncCallback.ChildrenCallback {
        public void processResult(int version, String path, Object context, List<String> nodes) {
            if (nodes == null) nodes = new LinkedList<>();
            log.log(Level.INFO, "Fleetcontroller " + nodeIndex + ": Got node list response from " + path + " version " + version + " with " + nodes.size() + " nodes");
            synchronized (nextMasterData) {
                nextMasterData.clear();
                for (String node : nodes) {
                    int index = Integer.parseInt(node);
                    nextMasterData.put(index, null);
                    log.log(Level.FINE, () -> "Fleetcontroller " + nodeIndex + ": Attempting to fetch data in node '"
                                              + paths.indexOf(index) + "' to see vote");
                    session.getData(paths.indexOf(index), changeWatcher, nodeListener, null);
                    // Invocation of cycleCompleted() for fully accumulated election state will happen
                    // as soon as all getData calls have been processed.
                }
            }
        }
    }

    /** The node data callback class is responsible for fetching new votes from fleetcontrollers that have altered their vote. */
    private class NodeDataCallback implements AsyncCallback.DataCallback {

        public void processResult(int code, String path, Object context, byte[] rawData, Stat stat) {
            String data = rawData == null ? null : new String(rawData, StandardCharsets.UTF_8);
            log.log(Level.INFO, "Fleetcontroller " + nodeIndex + ": Got vote data from path " + path +
                    " with code " + code + " and data " + data);

            int index = getIndex(path);
            synchronized (nextMasterData) {
                if (code != KeeperException.Code.OK.intValue()) {
                    if (code == KeeperException.Code.NONODE.intValue()) {
                        log.log(Level.INFO, "Fleetcontroller " + nodeIndex + ": Node at " + path +
                                " removed, got no other option than counting it as down.");
                    } else {
                        log.log(Level.WARNING, "Fleetcontroller " + nodeIndex + ": Failure code " + code +
                                " when listening to node at " + path + ", will assume it's down.");
                    }
                    if (nextMasterData.containsKey(index)) {
                        nextMasterData.remove(index);
                    } else {
                        // May happen when pending data watch error callbacks are triggered concurrently with
                        // internal voting state having already been cleared due to connectivity issues.
                        log.log(Level.INFO, String.format("Fleetcontroller %d: ignoring removal of vote from node %d " +
                                "since it was not present in existing vote mapping", nodeIndex, index));
                    }
                } else {
                    Integer value = Integer.valueOf(data);
                    if (nextMasterData.containsKey(index)) {
                        if (value.equals(nextMasterData.get(index))) {
                            log.log(Level.FINE, () -> "Fleetcontroller " + nodeIndex + ": Got vote from fleetcontroller " + index + ", which already was " + value + ".");
                        } else {
                            log.log(Level.INFO, "Fleetcontroller " + nodeIndex + ": Got vote from fleetcontroller " + index + ". Altering vote from " + nextMasterData.get(index) + " to " + value + ".");
                            nextMasterData.put(index, value);
                        }
                    } else {
                        log.log(Level.WARNING, "Fleetcontroller " + nodeIndex + ": Got vote from fleetcontroller " + index + " which is not alive according to current state. Ignoring it");
                    }
                }
                for(Integer vote : nextMasterData.values()) {
                    if (vote == null) {
                        log.log(Level.FINEST, () -> "Fleetcontroller " + nodeIndex + ": Still not received votes from all fleet controllers. Awaiting more responses.");
                        return;
                    }
                }
            }
            log.log(Level.FINE, () -> "Fleetcontroller " + nodeIndex + ": Got votes for all fleetcontrollers. Sending event with new fleet data for update");
            cycleCompleted();
        }
    }

    /** Constructor setting up the various needed members, and initializing the first data fetch to start things up */
    public MasterDataGatherer(ZooKeeper session, ZooKeeperPaths paths, Database.DatabaseListener listener, int nodeIndex) {
        this.paths = paths;
        this.session = session;
        this.listener = listener;
        this.nodeIndex = nodeIndex;
        if (session.getState().equals(ZooKeeper.States.CONNECTED)) {
            restart();
        }
    }

    /** Calling restart, ignores what we currently know and starts another cycle. Typically called after reconnecting to ZooKeeperServer. */
    public void restart() {
        synchronized (nextMasterData) {
            masterData = new TreeMap<>();
            nextMasterData.clear();
            session.getChildren(paths.indexesRoot(), changeWatcher, childListener, null);
        }
    }

    /** Function to be called when we have new consistent master election. */
    public void cycleCompleted() {
        Map<Integer, Integer> copy;
        synchronized (nextMasterData) {
            if (nextMasterData.equals(masterData)) {
                log.log(Level.FINE, () -> "Fleetcontroller " + nodeIndex + ": No change in master data detected, not sending it on");
                // for(Integer i : nextMasterData.keySet()) { System.err.println(i + " -> " + nextMasterData.get(i)); }
                return;
            }
            masterData = new TreeMap<>(nextMasterData);
            copy = masterData;
        }
        log.log(Level.FINE, () -> "Fleetcontroller " + nodeIndex + ": Got new master data, sending it on");
        listener.handleMasterData(copy);
    }

}
