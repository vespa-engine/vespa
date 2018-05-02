// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import com.yahoo.vespa.clustercontroller.core.AnnotatedClusterState;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.rpc.EnvelopedClusterStateBundleCodec;
import com.yahoo.vespa.clustercontroller.core.rpc.SlimeClusterStateBundleCodec;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.data.ACL;
import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vdslib.state.Node;

import java.util.logging.Logger;
import java.util.*;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

public class ZooKeeperDatabase extends Database {

    private static Logger log = Logger.getLogger(ZooKeeperDatabase.class.getName());
    private static Charset utf8 = Charset.forName("UTF8");
    private static final List<ACL> acl = ZooDefs.Ids.OPEN_ACL_UNSAFE;

    private final String zooKeeperRoot;
    private final Database.DatabaseListener listener;
    private final ZooKeeperWatcher watcher = new ZooKeeperWatcher();
    private final ZooKeeper session;
    private boolean sessionOpen = true;
    private final int nodeIndex;
    private final MasterDataGatherer masterDataGatherer;
    private boolean reportErrors = true;

    public void stopErrorReporting() {
        reportErrors = false;
    }

    private class ZooKeeperWatcher implements Watcher {
        private Event.KeeperState state = null;

        public Event.KeeperState getState() { return (state == null ? Event.KeeperState.SyncConnected : state); }

        public void process(WatchedEvent watchedEvent) {
            // Shouldn't get events after we expire, but just be sure we stop them here.
            if (state != null && state.equals(Event.KeeperState.Expired)) {
                log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Got event from ZooKeeper session after it expired");
                return;
            }
            Event.KeeperState newState = watchedEvent.getState();
            if (state == null || !state.equals(newState)) switch (newState) {
                case Expired:
                    log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex + ": Zookeeper session expired");
                    sessionOpen = false;
                    listener.handleZooKeeperSessionDown();
                    break;
                case Disconnected:
                    log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex + ": Lost connection to zookeeper server");
                    sessionOpen = false;
                    listener.handleZooKeeperSessionDown();
                    break;
                case SyncConnected:
                    log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex + ": Connection to zookeeper server established. Refetching master data");
                    if (masterDataGatherer != null) {
                        masterDataGatherer.restart();
                    }
            }
            switch (watchedEvent.getType()) {
                case NodeChildrenChanged: // Fleetcontrollers have either connected or disconnected to ZooKeeper
                    log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Got unexpected ZooKeeper event NodeChildrenChanged");
                    break;
                case NodeDataChanged: // A fleetcontroller have changed what node it is voting for
                    log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Got unexpected ZooKeeper event NodeDataChanged");
                    break;
                case NodeCreated: // How can this happen? Can one leave watches on non-existing nodes?
                    log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Got unexpected ZooKeeper event NodeCreated");
                    break;
                case NodeDeleted: // We're not watching any nodes for whether they are deleted or not.
                    log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Got unexpected ZooKeeper event NodeDeleted");
                    break;
                case None:
                    if (state != null && state.equals(watchedEvent.getState())) {
                        log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Got None type event that didn't even alter session state. What does that indicate?");
                    }
            }
            state = watchedEvent.getState();
        }
    };

    public ZooKeeperDatabase(ContentCluster cluster, int nodeIndex, String address, int timeout, Database.DatabaseListener zksl) throws IOException, KeeperException, InterruptedException {
        this.nodeIndex = nodeIndex;
        zooKeeperRoot = "/vespa/fleetcontroller/" + cluster.getName() + "/";
        session = new ZooKeeper(address, timeout, watcher);
        boolean completedOk = false;
        try{
            this.listener = zksl;
            setupRoot();
            log.log(LogLevel.SPAM, "Fleetcontroller " + nodeIndex + ": Asking for initial data on master election");
            masterDataGatherer = new MasterDataGatherer(session, zooKeeperRoot, listener, nodeIndex);
            completedOk = true;
        } finally {
            if (!completedOk) session.close();
        }
    }

    private void createNode(String prefix, String nodename, byte value[]) throws KeeperException, InterruptedException {
        try{
            if (session.exists(prefix + nodename, false) != null) {
                log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Zookeeper node '" + prefix + nodename + "' already exists. Not creating it");
                return;
            }
            session.create(prefix + nodename, value, acl, CreateMode.PERSISTENT);
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Created zookeeper node '" + prefix + nodename + "'");
        } catch (KeeperException.NodeExistsException e) {
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Node to create existed, "
                                  + "but this is normal as other nodes may create them at the same time.");
        }
    }

    private void setupRoot() throws KeeperException, InterruptedException {
        String pathElements[] = zooKeeperRoot.substring(1).split("/");
        String path = "";
        for (String elem : pathElements) {
            path += "/" + elem;
            createNode("", path, new byte[0]);
        }
        createNode(zooKeeperRoot, "indexes", new byte[0]);
        createNode(zooKeeperRoot, "wantedstates", new byte[0]);
        createNode(zooKeeperRoot, "starttimestamps", new byte[0]);
        createNode(zooKeeperRoot, "latestversion", Integer.valueOf(0).toString().getBytes(utf8));
        createNode(zooKeeperRoot, "published_state_bundle", new byte[0]); // TODO dedupe string constants
        byte val[] = String.valueOf(nodeIndex).getBytes(utf8);
        deleteNodeIfExists(getMyIndexPath());
        log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex +
                ": Creating ephemeral master vote node with vote to self.");
        session.create(getMyIndexPath(), val, acl, CreateMode.EPHEMERAL);
    }

    private void deleteNodeIfExists(String path) throws KeeperException, InterruptedException {
        if (session.exists(path, false) != null) {
            log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex + ": Removing master vote node.");
            session.delete(path, -1);
        }
    }

    private String getMyIndexPath() {
        return zooKeeperRoot + "indexes/" + nodeIndex;
    }

    /**
     * If this is called, we assume we're in shutdown situation, or we are doing it because we need a new session.
     * Thus we only need to free up resources, no need to notify anyone.
     */
    public void close() {
        sessionOpen = false;
        try{
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Trying to close ZooKeeper session 0x"
                    + Long.toHexString(session.getSessionId()));
            session.close();
        } catch (InterruptedException e) {
            log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Got interrupt exception while closing session: " + e);
        }
    }

    public boolean isClosed() {
        return (!sessionOpen || watcher.getState().equals(Watcher.Event.KeeperState.Expired));
    }

    private void maybeLogExceptionWarning(Exception e, String message) {
        if (sessionOpen && reportErrors) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log.log(LogLevel.WARNING, String.format("Fleetcontroller %s: %s. Exception: %s\n%s",
                    nodeIndex, message, e.getMessage(), sw.toString()));
        }
    }

    public boolean storeMasterVote(int wantedMasterIndex) throws InterruptedException {
        byte val[] = String.valueOf(wantedMasterIndex).getBytes(utf8);
        try{
            session.setData(getMyIndexPath(), val, -1);
            log.log(LogLevel.INFO, "Fleetcontroller " + nodeIndex + ": Stored new vote in ephemeral node. " + nodeIndex + " -> " + wantedMasterIndex);
            return true;
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to create our ephemeral node and store master vote");
        }
        return false;
    }
    public boolean storeLatestSystemStateVersion(int version) throws InterruptedException {
        byte data[] = Integer.toString(version).getBytes(utf8);
        try{
            log.log(LogLevel.INFO, String.format("Fleetcontroller %d: Storing new cluster state version in ZooKeeper: %d", nodeIndex, version));
            session.setData(zooKeeperRoot + "latestversion", data, -1);
            return true;
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to store latest system state version used " + version);
            return false;
        }
    }

    public Integer retrieveLatestSystemStateVersion() throws InterruptedException {
        Stat stat = new Stat();
        try{
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Fetching latest cluster state at '" + zooKeeperRoot + "latestversion'");
            byte[] data = session.getData(zooKeeperRoot + "latestversion", false, stat);
            final Integer versionNumber = Integer.valueOf(new String(data, utf8));
            log.log(LogLevel.INFO, String.format("Fleetcontroller %d: Read cluster state version %d from ZooKeeper", nodeIndex, versionNumber));
            return versionNumber;
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to retrieve latest system state version used. Returning null");
            return null;
        }
    }

    public boolean storeWantedStates(Map<Node, NodeState> states) throws InterruptedException {
        if (states == null) states = new TreeMap<>();
        StringBuilder sb = new StringBuilder();
        for (Node node : states.keySet()) {
            NodeState nodeState = states.get(node);
            if (!nodeState.equals(new NodeState(node.getType(), State.UP))) {
                NodeState toStore = new NodeState(node.getType(), nodeState.getState());
                toStore.setDescription(nodeState.getDescription());
                if (!toStore.equals(nodeState)) {
                    log.warning("Attempted to store wanted state with more than just a main state. Extra data stripped. Original data '" + nodeState.serialize(true));
                }
                sb.append(node.toString()).append(':').append(toStore.serialize(true)).append('\n');
            }
        }
        byte val[] = sb.toString().getBytes(utf8);
        try{
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Storing wanted states at '" + zooKeeperRoot + "wantedstates'");
            session.setData(zooKeeperRoot + "wantedstates", val, -1);
            return true;
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to store wanted states in ZooKeeper");
            return false;
        }
    }

    public Map<Node, NodeState> retrieveWantedStates() throws InterruptedException {
        try{
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Fetching wanted states at '" + zooKeeperRoot + "wantedstates'");
            Stat stat = new Stat();
            byte[] data = session.getData(zooKeeperRoot + "wantedstates", false, stat);
            Map<Node, NodeState> wanted = new TreeMap<>();
            if (data != null && data.length > 0) {
                StringTokenizer st = new StringTokenizer(new String(data, utf8), "\n", false);
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    int colon = token.indexOf(':');
                    try{
                        if (colon < 0) throw new Exception();
                        Node node = new Node(token.substring(0, colon));
                        NodeState nodeState = NodeState.deserialize(node.getType(), token.substring(colon + 1));
                        wanted.put(node, nodeState);
                    } catch (Exception e) {
                        log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Ignoring invalid wantedstate line in zookeeper '" + token + "'.");
                    }
                }
            }
            return wanted;
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to retrieve wanted states from ZooKeeper");
            return null;
        }
    }

    @Override
    public boolean storeStartTimestamps(Map<Node, Long> timestamps) throws InterruptedException {
        if (timestamps == null) timestamps = new TreeMap<>();
        StringBuilder sb = new StringBuilder();
        for (Node n : timestamps.keySet()) {
            Long timestamp = timestamps.get(n);
            sb.append(n.toString()).append(':').append(timestamp).append('\n');
        }
        byte val[] = sb.toString().getBytes(utf8);
        try{
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Storing start timestamps at '" + zooKeeperRoot + "starttimestamps");
            session.setData(zooKeeperRoot + "starttimestamps", val, -1);
            return true;
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to store start timestamps in ZooKeeper");
            return false;
        }
    }

    @Override
    public Map<Node, Long> retrieveStartTimestamps() throws InterruptedException {
        try{
            log.log(LogLevel.DEBUG, "Fleetcontroller " + nodeIndex + ": Fetching start timestamps at '" + zooKeeperRoot + "starttimestamps'");
            Stat stat = new Stat();
            byte[] data = session.getData(zooKeeperRoot + "starttimestamps", false, stat);
            Map<Node, Long> wanted = new TreeMap<Node, Long>();
            if (data != null && data.length > 0) {
                StringTokenizer st = new StringTokenizer(new String(data, utf8), "\n", false);
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    int colon = token.indexOf(':');
                    try{
                        if (colon < 0) throw new Exception();
                        Node n = new Node(token.substring(0, colon));
                        Long timestamp =  Long.valueOf(token.substring(colon + 1));
                        wanted.put(n, timestamp);
                    } catch (Exception e) {
                        log.log(LogLevel.WARNING, "Fleetcontroller " + nodeIndex + ": Ignoring invalid starttimestamp line in zookeeper '" + token + "'.");
                    }
                }
            }
            return wanted;
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to retrieve start timestamps from ZooKeeper");
            return null;
        }
    }

    @Override
    public boolean storeLastPublishedStateBundle(ClusterStateBundle stateBundle) throws InterruptedException {
        EnvelopedClusterStateBundleCodec envelopedBundleCodec = new SlimeClusterStateBundleCodec();
        byte[] encodedBundle = envelopedBundleCodec.encodeWithEnvelope(stateBundle);
        try{
            log.log(LogLevel.DEBUG, () -> String.format("Fleetcontroller %d: Storing published state bundle %s at '%spublished_state_bundle'",
                    nodeIndex, stateBundle, zooKeeperRoot));
            // TODO CAS on expected zknode version
            session.setData(zooKeeperRoot + "published_state_bundle", encodedBundle, -1);
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to store last published cluster state bundle in ZooKeeper");
            return false;
        }
        return true;
    }

    @Override
    public ClusterStateBundle retrieveLastPublishedStateBundle() throws InterruptedException {
        Stat stat = new Stat();
        try {
            byte[] data = session.getData(zooKeeperRoot + "published_state_bundle", false, stat);
            if (data != null && data.length != 0) {
                EnvelopedClusterStateBundleCodec envelopedBundleCodec = new SlimeClusterStateBundleCodec();
                return envelopedBundleCodec.decodeWithEnvelope(data);
            }
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to retrieve last published cluster state bundle from " +
                    "ZooKeeper, will use an empty state as baseline");
        }
        return ClusterStateBundle.ofBaselineOnly(AnnotatedClusterState.emptyState());
    }

}
