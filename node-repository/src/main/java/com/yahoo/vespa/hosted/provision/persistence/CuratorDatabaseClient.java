// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Client which reads and writes nodes to a curator database.
 * Nodes are stored in files named <code>/provision/v1/[nodestate]/[hostname]</code>.
 *
 * The responsibility of this class is to turn operations on the level of node states, applications and nodes
 * into operations on the level of file paths and bytes.
 *
 * @author bratseth
 */
public class CuratorDatabaseClient {

    private static final Logger log = Logger.getLogger(CuratorDatabaseClient.class.getName());

    private static final Path root = Path.fromString("/provision/v1");

    private final NodeSerializer nodeSerializer;

    private final CuratorDatabase curatorDatabase;

    /** Used to serialize and de-serialize JSON data stored in ZK */
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final Clock clock;
    
    private final Zone zone;

    public CuratorDatabaseClient(NodeFlavors flavors, Curator curator, Clock clock, Zone zone, NameResolver nameResolver) {
        this.nodeSerializer = new NodeSerializer(flavors, nameResolver);
        this.zone = zone;
        jsonMapper.registerModule(new JodaModule());
        this.curatorDatabase = new CuratorDatabase(curator, root, /* useCache: */ false);
        this.clock = clock;
        initZK();
    }

    private void initZK() {
        curatorDatabase.create(root);
        for (Node.State state : Node.State.values())
            curatorDatabase.create(toPath(state));
    }

    /**
     * Adds a set of nodes. Rollbacks/fails transaction if any node is not in the expected state.
     */
    public List<Node> addNodesInState(List<Node> nodes, Node.State expectedState) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        for (Node node : nodes) {
            if (node.state() != expectedState)
                throw new IllegalArgumentException(node + " is not in the " + node.state() + " state");
            curatorTransaction.add(CuratorOperations.create(toPath(node).getAbsolute(), nodeSerializer.toJson(node)));
        }
        transaction.commit();

        for (Node node : nodes)
            log.log(LogLevel.INFO, "Added " + node);

        return nodes;
    }

    /**
     * Adds a set of nodes in the initial, provisioned state.
     *
     * @return the given nodes for convenience.
     */
    public List<Node> addNodes(List<Node> nodes) {
        return addNodesInState(nodes, Node.State.provisioned);
    }

    /**
     * Removes a node.
     *
     * @param state the current state of the node
     * @param hostName the host name of the node to remove
     * @return true if the node was removed, false if it was not found
     */
    public boolean removeNode(Node.State state, String hostName) {
        Path path = toPath(state, hostName);
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.delete(path.getAbsolute()));
        transaction.commit();
        log.log(LogLevel.INFO, "Removed: " + state + " node " + hostName);
        return true;
    }

    /**
     * Writes the given nodes to the given state (whether or not they are already in this state or another),
     * and returns a copy of the incoming nodes in their persisted state.
     *
     * @param  toState the state to write the nodes to
     * @param  nodes the list of nodes to write
     * @return the nodes in their persisted state
     */
    public List<Node> writeTo(Node.State toState, List<Node> nodes) {
        try (NestedTransaction nestedTransaction = new NestedTransaction()) {
            List<Node> writtenNodes = writeTo(toState, nodes, nestedTransaction);
            nestedTransaction.commit();
            return writtenNodes;
        }
    }
    public Node writeTo(Node.State toState, Node node) {
        return writeTo(toState, Collections.singletonList(node)).get(0);
    }

    /**
     * Adds to the given transaction operations to write the given nodes to the given state,
     * and returns a copy of the nodes in the state they will have if the transaction is committed.
     *
     * @param  toState the state to write the nodes to
     * @param  nodes the list of nodes to write
     * @param  transaction the transaction to which write operations are added by this
     * @return the nodes in their state as it will be written if committed
     */
    public List<Node> writeTo(Node.State toState, List<Node> nodes, NestedTransaction transaction) {
        if (nodes.isEmpty()) return nodes;

        List<Node> writtenNodes = new ArrayList<>(nodes.size());

        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        for (Node node : nodes) {
            Node newNode = new Node(node.openStackId(), node.ipAddresses(), node.hostname(),
                                    node.parentHostname(), node.flavor(),
                                    newNodeStatus(node, toState),
                                    toState,
                                    toState.isAllocated() ? node.allocation() : Optional.empty(),
                                    newNodeHistory(node, toState),
                                    node.type());
            curatorTransaction.add(CuratorOperations.delete(toPath(node).getAbsolute()))
                              .add(CuratorOperations.create(toPath(toState, newNode.hostname()).getAbsolute(), nodeSerializer.toJson(newNode)));
            writtenNodes.add(newNode);
        }

        transaction.onCommitted(() -> { // schedule logging on commit of nodes which changed state
            for (Node node : nodes) {
                if (toState != node.state())
                    log.log(LogLevel.INFO, "Moved to " + toState + ": " + node);
            }
        });
        return writtenNodes;
    }

    private Status newNodeStatus(Node node, Node.State toState) {
        if (node.state() != Node.State.failed && toState == Node.State.failed) return node.status().withIncreasedFailCount();
        if (node.state() == Node.State.failed && toState == Node.State.active) return node.status().withDecreasedFailCount(); // fail undo
        // Increase reboot generation when node is moved to dirty unless quick reuse is prioritized. 
        // This gets rid of lingering processes, updates OS packages if necessary and tests that reboot succeeds.
        if (node.state() != Node.State.dirty && toState == Node.State.dirty && !needsFastNodeReuse(zone))
            return node.status().withReboot(node.status().reboot().withIncreasedWanted());

        return node.status();
    }

    /** In automated test environments, nodes need to be reused quickly to achieve fast test turnaronud time */
    private boolean needsFastNodeReuse(Zone zone) {
        return zone.environment() == Environment.staging || zone.environment() == Environment.test;
    }

    private History newNodeHistory(Node node, Node.State toState) {
        History history = node.history();

        // wipe history when a node *becomes* ready to avoid expiring based on events under the previous allocation
        if (node.state() != Node.State.ready && toState == Node.State.ready)
            history = History.empty();

        return history.recordStateTransition(node.state(), toState, clock.instant());
    }

    /**
     * Returns all nodes which are in one of the given states.
     * If no states are given this returns all nodes.
     */
    public List<Node> getNodes(Node.State ... states) {
        List<Node> nodes = new ArrayList<>();
        if (states.length == 0)
            states = Node.State.values();
        for (Node.State state : states) {
            for (String hostname : curatorDatabase.getChildren(toPath(state))) {
                Optional<Node> node = getNode(hostname, state);
                if (node.isPresent()) nodes.add(node.get()); // node might disappear between getChildren and getNode
            }
        }
        return nodes;
    }

    /** 
     * Returns all nodes allocated to the given application which are in one of the given states 
     * If no states are given this returns all nodes.
     */
    public List<Node> getNodes(ApplicationId applicationId, Node.State ... states) {
        List<Node> nodes = getNodes(states);
        nodes.removeIf(node -> ! node.allocation().isPresent() || ! node.allocation().get().owner().equals(applicationId));
        return nodes;
    }

    /** 
     * Returns a particular node, or empty if this noe is not in any of the given states.
     * If no states are given this returns the node if it is present in any state.
     */
    public Optional<Node> getNode(String hostname, Node.State ... states) {
        if (states.length == 0)
            states = Node.State.values();
        for (Node.State state : states) {
            Optional<byte[]> nodeData = curatorDatabase.getData(toPath(state, hostname));
            if (nodeData.isPresent())
                return nodeData.map((data) -> nodeSerializer.fromJson(state, data));
        }
        return Optional.empty();
    }

    private Path toPath(Node.State nodeState) { return root.append(toDir(nodeState)); }

    private Path toPath(Node node) {
        return root.append(toDir(node.state())).append(node.hostname());
    }

    private Path toPath(Node.State nodeState, String nodeName) {
        return root.append(toDir(nodeState)).append(nodeName);
    }

    /** Creates an returns the path to the lock for this application */
    private Path lockPath(ApplicationId application) {
        Path lockPath = 
                root
                .append("locks")
                .append(application.tenant().value())
                .append(application.application().value())
                .append(application.instance().value());
        curatorDatabase.create(lockPath);
        return lockPath;
    }

    private String toDir(Node.State state) {
        switch (state) {
            case active: return "allocated"; // legacy name
            case dirty: return "dirty";
            case failed: return "failed";
            case inactive: return "deallocated"; // legacy name
            case parked : return "parked";
            case provisioned: return "provisioned";
            case ready: return "ready";
            case reserved: return "reserved";
            default: throw new RuntimeException("Node state " + state + " does not map to a directory name");
        }
    }

    /** Acquires the single cluster-global, reentrant lock for all non-active nodes */
    public CuratorMutex lockInactive() {
        return lock(root.append("locks").append("unallocatedLock"));
    }

    /** Acquires the single cluster-global, reentrant lock for active nodes of this application */
    public CuratorMutex lock(ApplicationId application) {
        return lock(lockPath(application));
    }

    /** Acquires the single cluster-global, reentrant lock for all non-active nodes */
    public CuratorMutex lock(Path path) {
        return curatorDatabase.lock(path);
    }

}
