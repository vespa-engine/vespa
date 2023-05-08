// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.AnnotatedClusterState;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.FleetControllerContext;
import com.yahoo.vespa.clustercontroller.core.rpc.EnvelopedClusterStateBundleCodec;
import com.yahoo.vespa.clustercontroller.core.rpc.SlimeClusterStateBundleCodec;
import com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZooKeeperDatabase extends Database {

    private static final Logger log = Logger.getLogger(ZooKeeperDatabase.class.getName());
    private static final Charset utf8 = StandardCharsets.UTF_8;
    private static final List<ACL> acl = ZooDefs.Ids.OPEN_ACL_UNSAFE;

    private final ZooKeeperPaths paths;
    private final Database.DatabaseListener listener;
    private final ZooKeeperWatcher watcher = new ZooKeeperWatcher();
    private final ZooKeeper session;
    private boolean sessionOpen = true;
    private final FleetControllerContext context;
    private final MasterDataGatherer masterDataGatherer;
    // Expected ZK znode versions. Note: these are _not_ -1 as that would match anything.
    // We expect the caller to invoke the load methods prior to calling any store methods.
    private int lastKnownStateBundleZNodeVersion = -2;
    private int lastKnownStateVersionZNodeVersion = -2;

    private class ZooKeeperWatcher implements Watcher {
        private Event.KeeperState state = null;

        public Event.KeeperState getState() { return (state == null ? Event.KeeperState.SyncConnected : state); }

        public void process(WatchedEvent watchedEvent) {
            // Shouldn't get events after we expire, but just be sure we stop them here.
            if (state != null && state.equals(Event.KeeperState.Expired)) {
                context.log(log, Level.WARNING, "Got event from ZooKeeper session after it expired");
                return;
            }
            Event.KeeperState newState = watchedEvent.getState();
            if (state == null || !state.equals(newState)) switch (newState) {
                case Expired:
                    context.log(log, Level.INFO, "Zookeeper session expired");
                    sessionOpen = false;
                    listener.handleZooKeeperSessionDown();
                    break;
                case Disconnected:
                    context.log(log, Level.INFO, "Lost connection to zookeeper server");
                    sessionOpen = false;
                    listener.handleZooKeeperSessionDown();
                    break;
                case SyncConnected:
                    context.log(log, Level.INFO, "Connection to zookeeper server established. Refetching master data");
                    if (masterDataGatherer != null) {
                        masterDataGatherer.restart();
                    }
            }
            switch (watchedEvent.getType()) {
                case NodeChildrenChanged: // Fleetcontrollers have either connected or disconnected to ZooKeeper
                    context.log(log, Level.WARNING, "Got unexpected ZooKeeper event NodeChildrenChanged");
                    break;
                case NodeDataChanged: // A fleetcontroller have changed what node it is voting for
                    context.log(log, Level.WARNING, "Got unexpected ZooKeeper event NodeDataChanged");
                    break;
                case NodeCreated: // How can this happen? Can one leave watches on non-existing nodes?
                    context.log(log, Level.WARNING, "Got unexpected ZooKeeper event NodeCreated");
                    break;
                case NodeDeleted: // We're not watching any nodes for whether they are deleted or not.
                    context.log(log, Level.WARNING, "Got unexpected ZooKeeper event NodeDeleted");
                    break;
                case None:
                    if (state != null && state.equals(watchedEvent.getState())) {
                        context.log(log, Level.WARNING, "Got None type event that didn't even alter session state. What does that indicate?");
                    }
            }
            state = watchedEvent.getState();
        }
    }

    public ZooKeeperDatabase(FleetControllerContext context, String address, int timeout, DatabaseListener zksl) throws IOException, KeeperException, InterruptedException {
        this.context = context;
        this.paths = new ZooKeeperPaths(context.id());
        session = new ZooKeeper(address, timeout, watcher, new ZkClientConfigBuilder().toConfig());
        boolean completedOk = false;
        try{
            this.listener = zksl;
            setupRoot();
            context.log(log, Level.FINEST, "Asking for initial data on master election");
            masterDataGatherer = new MasterDataGatherer(session, paths, listener, context.id().index());
            completedOk = true;
        } finally {
            if (!completedOk) session.close();
        }
    }

    private void createNode(String path, byte[] value) throws KeeperException, InterruptedException {
        try{
            if (session.exists(path, false) != null) {
                context.log(log, Level.FINE, () -> "Zookeeper node '" + path + "' already exists. Not creating it");
                return;
            }
            session.create(path, value, acl, CreateMode.PERSISTENT);
            context.log(log, Level.FINE, () -> "Created zookeeper node '" + path + "'");
        } catch (KeeperException.NodeExistsException e) {
            context.log(log, Level.FINE, "Node to create existed, but this is normal as other nodes " +
                                         "may create them at the same time.");
        }
    }

    private void setupRoot() throws KeeperException, InterruptedException {
        String[] pathElements = paths.root().substring(1).split("/");
        String path = "";
        for (String elem : pathElements) {
            path += "/" + elem;
            createNode(path, new byte[0]);
        }
        createNode(paths.indexesRoot(), new byte[0]);
        createNode(paths.wantedStates(), new byte[0]);
        createNode(paths.startTimestamps(), new byte[0]);
        createNode(paths.latestVersion(), Integer.valueOf(0).toString().getBytes(utf8));
        createNode(paths.publishedStateBundle(), new byte[0]);
        byte[] val = String.valueOf(context.id().index()).getBytes(utf8);
        deleteNodeIfExists(paths.indexOfMe());
        context.log(log, Level.INFO, "Creating ephemeral master vote node with vote to self.");
        session.create(paths.indexOfMe(), val, acl, CreateMode.EPHEMERAL);
    }

    private void deleteNodeIfExists(String path) throws KeeperException, InterruptedException {
        if (session.exists(path, false) != null) {
            context.log(log, Level.INFO, "Removing master vote node at " + path);
            session.delete(path, -1);
        }
    }

    /**
     * If this is called, we assume we're in shutdown situation, or we are doing it because we need a new session.
     * Thus we only need to free up resources, no need to notify anyone.
     */
    public void close() {
        sessionOpen = false;
        try{
            context.log(log, Level.FINE, () -> "Trying to close ZooKeeper session 0x"
                    + Long.toHexString(session.getSessionId()));
            session.close();
        } catch (InterruptedException e) {
            context.log(log, Level.WARNING, "Got interrupt exception while closing session: " + e);
        }
    }

    public boolean isClosed() {
        return (!sessionOpen || watcher.getState().equals(Watcher.Event.KeeperState.Expired));
    }

    private void maybeLogExceptionWarning(Exception e, String message) {
        if (sessionOpen) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            context.log(log, Level.WARNING, message + ". Exception: " + e.getMessage() + "\n" + sw);
        }
    }

    public boolean storeMasterVote(int wantedMasterIndex) {
        byte[] val = String.valueOf(wantedMasterIndex).getBytes(utf8);
        try{
            session.setData(paths.indexOfMe(), val, -1);
            context.log(log, Level.INFO, "Stored new vote in ephemeral node. " + context.id().index() + " -> " + wantedMasterIndex);
            return true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to create our ephemeral node and store master vote");
        }
        return false;
    }
    public boolean storeLatestSystemStateVersion(int version) {
        byte[] data = Integer.toString(version).getBytes(utf8);
        try{
            context.log(log, Level.INFO, "Storing new cluster state version in ZooKeeper: " + version);
            var stat = session.setData(paths.latestVersion(), data, lastKnownStateVersionZNodeVersion);
            lastKnownStateVersionZNodeVersion = stat.getVersion();
            return true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (KeeperException.BadVersionException e) {
            throw new CasWriteFailed(String.format("version mismatch in cluster state version znode (expected %d): %s",
                    lastKnownStateVersionZNodeVersion, e.getMessage()), e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to store latest system state version used " + version);
            return false;
        }
    }

    public Integer retrieveLatestSystemStateVersion() {
        Stat stat = new Stat();
        context.log(log, Level.FINE, "Fetching latest cluster state at '%s'", paths.latestVersion());
        final byte[] data;
        try {
            data = session.getData(paths.latestVersion(), false, stat);
        } catch (KeeperException.NoNodeException e) {
            // Initial condition: No latest version has ever been written (or ZK state completely wiped!)
            lastKnownStateVersionZNodeVersion = 0;
            maybeLogExceptionWarning(e, "No latest system state found");
            return null;
        } catch (InterruptedException | KeeperException e) {
            throw new RuntimeException("Failed to get " + paths.latestVersion(), e);
        }

        lastKnownStateVersionZNodeVersion = stat.getVersion();
        final Integer versionNumber = Integer.valueOf(new String(data, utf8));
        context.log(log, Level.INFO, "Read cluster state version %d from ZooKeeper (znode version %d)", versionNumber, stat.getVersion());
        return versionNumber;
    }

    public boolean storeWantedStates(Map<Node, NodeState> states) {
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
                sb.append(node).append(':').append(toStore.serialize(true)).append('\n');
            }
        }
        byte[] val = sb.toString().getBytes(utf8);
        try{
            context.log(log, Level.FINE, () -> "Storing wanted states at '" + paths.wantedStates() + "'");
            session.setData(paths.wantedStates(), val, -1);
            return true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to store wanted states in ZooKeeper");
            return false;
        }
    }

    public Map<Node, NodeState> retrieveWantedStates() {
        try{
            context.log(log, Level.FINE, () -> "Fetching wanted states at '" + paths.wantedStates() + "'");
            Stat stat = new Stat();
            byte[] data = session.getData(paths.wantedStates(), false, stat);
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
                        context.log(log, Level.WARNING, "Ignoring invalid wantedstate line in zookeeper '" + token + "'.");
                    }
                }
            }
            return wanted;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to retrieve wanted states from ZooKeeper");
            return null;
        }
    }

    @Override
    public boolean storeStartTimestamps(Map<Node, Long> timestamps) {
        if (timestamps == null) timestamps = new TreeMap<>();
        StringBuilder sb = new StringBuilder();
        for (Node n : timestamps.keySet()) {
            Long timestamp = timestamps.get(n);
            sb.append(n.toString()).append(':').append(timestamp).append('\n');
        }
        byte[] val = sb.toString().getBytes(utf8);
        try{
            context.log(log, Level.FINE, () -> "Storing start timestamps at '" + paths.startTimestamps() + "'");
            session.setData(paths.startTimestamps(), val, -1);
            return true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to store start timestamps in ZooKeeper");
            return false;
        }
    }

    @Override
    public Map<Node, Long> retrieveStartTimestamps() {
        try{
            context.log(log, Level.FINE, () -> "Fetching start timestamps at '" + paths.startTimestamps() + "'");
            Stat stat = new Stat();
            byte[] data = session.getData(paths.startTimestamps(), false, stat);
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
                        context.log(log, Level.WARNING, "Ignoring invalid starttimestamp line in zookeeper '" + token + "'.");
                    }
                }
            }
            return wanted;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to retrieve start timestamps from ZooKeeper");
            return null;
        }
    }

    @Override
    public boolean storeLastPublishedStateBundle(ClusterStateBundle stateBundle) {
        EnvelopedClusterStateBundleCodec envelopedBundleCodec = new SlimeClusterStateBundleCodec();
        byte[] encodedBundle = envelopedBundleCodec.encodeWithEnvelope(stateBundle);
        try{
            context.log(log,
                        Level.FINE,
                        () -> String.format("Storing published state bundle %s at '%s' with expected znode version %d",
                                            stateBundle, paths.publishedStateBundle(), lastKnownStateBundleZNodeVersion));
            var stat = session.setData(paths.publishedStateBundle(), encodedBundle, lastKnownStateBundleZNodeVersion);
            lastKnownStateBundleZNodeVersion = stat.getVersion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (KeeperException.BadVersionException e) {
            throw new CasWriteFailed(String.format("version mismatch in cluster state bundle znode (expected %d): %s",
                    lastKnownStateBundleZNodeVersion, e.getMessage()), e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to store last published cluster state bundle in ZooKeeper");
            return false;
        }
        return true;
    }

    @Override
    public ClusterStateBundle retrieveLastPublishedStateBundle() {
        Stat stat = new Stat();
        try {
            byte[] data = session.getData(paths.publishedStateBundle(), false, stat);
            lastKnownStateBundleZNodeVersion = stat.getVersion();
            if (data != null && data.length != 0) {
                EnvelopedClusterStateBundleCodec envelopedBundleCodec = new SlimeClusterStateBundleCodec();
                return envelopedBundleCodec.decodeWithEnvelope(data);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            maybeLogExceptionWarning(e, "Failed to retrieve last published cluster state bundle from " +
                    "ZooKeeper, will use an empty state as baseline");
        }
        // If we return a default, empty bundle, writes dependent on this bundle should only
        // succeed if the previous znode version is 0, i.e. not yet created.
        lastKnownStateBundleZNodeVersion = 0;
        return ClusterStateBundle.ofBaselineOnly(AnnotatedClusterState.emptyState());
    }

}
