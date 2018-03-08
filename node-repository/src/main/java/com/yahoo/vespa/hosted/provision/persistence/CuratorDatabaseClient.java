// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    private static final Duration defaultLockTimeout = Duration.ofMinutes(1);

    private final NodeSerializer nodeSerializer;
    private final StringSetSerializer stringSetSerializer = new StringSetSerializer();

    private final CuratorDatabase curatorDatabase;

    private final Clock clock;
    
    private final Zone zone;

    public CuratorDatabaseClient(NodeFlavors flavors, Curator curator, Clock clock, Zone zone) {
        this.nodeSerializer = new NodeSerializer(flavors);
        this.zone = zone;
        boolean useCache = zone.system().equals(SystemName.cd);
        this.curatorDatabase = new CuratorDatabase(curator, root, useCache);
        this.clock = clock;
        initZK();
    }

    private void initZK() {
        curatorDatabase.create(root);
        for (Node.State state : Node.State.values())
            curatorDatabase.create(toPath(state));
        curatorDatabase.create(inactiveJobsPath());
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

            node = node.with(node.history().recordStateTransition(null, expectedState, Agent.system, clock.instant()));
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
     * Removes multiple nodes in a single transaction.
     *
     * @param nodes list of the nodes to remove
     */
    public void removeNodes(List<Node> nodes) {
        NestedTransaction transaction = new NestedTransaction();

        for (Node node : nodes) {
            Path path = toPath(node.state(), node.hostname());
            CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
            curatorTransaction.add(CuratorOperations.delete(path.getAbsolute()));
        }

        transaction.commit();
        nodes.forEach(node -> log.log(LogLevel.INFO, "Removed node " + node.hostname() + " in state " + node.state()));
    }

    /**
     * Writes the given nodes and returns a copy of the incoming nodes in their persisted state.
     *
     * @param  nodes the list of nodes to write
     * @param  agent the agent causing this change
     * @return the nodes in their persisted state
     */
    public List<Node> writeTo(List<Node> nodes, Agent agent, Optional<String> reason) {
        if (nodes.isEmpty()) return Collections.emptyList();

        List<Node> writtenNodes = new ArrayList<>(nodes.size());

        try (NestedTransaction nestedTransaction = new NestedTransaction()) {
            Map<Node.State, List<Node>> nodesByState = nodes.stream().collect(Collectors.groupingBy(Node::state));
            for (Map.Entry<Node.State, List<Node>> entry : nodesByState.entrySet()) {
                writtenNodes.addAll(writeTo(entry.getKey(), entry.getValue(), agent, reason, nestedTransaction));
            }
            nestedTransaction.commit();
        }

        return writtenNodes;
    }

    /**
     * Writes the given nodes to the given state (whether or not they are already in this state or another),
     * and returns a copy of the incoming nodes in their persisted state.
     *
     * @param  toState the state to write the nodes to
     * @param  nodes the list of nodes to write
     * @param  agent the agent causing this change
     * @return the nodes in their persisted state
     */
    public List<Node> writeTo(Node.State toState, List<Node> nodes,
                              Agent agent, Optional<String> reason) {
        try (NestedTransaction nestedTransaction = new NestedTransaction()) {
            List<Node> writtenNodes = writeTo(toState, nodes, agent, reason, nestedTransaction);
            nestedTransaction.commit();
            return writtenNodes;
        }
    }
    public Node writeTo(Node.State toState, Node node, Agent agent, Optional<String> reason) {
        return writeTo(toState, Collections.singletonList(node), agent, reason).get(0);
    }

    /**
     * Adds to the given transaction operations to write the given nodes to the given state,
     * and returns a copy of the nodes in the state they will have if the transaction is committed.
     *
     * @param  toState the state to write the nodes to
     * @param  nodes the list of nodes to write
     * @param  agent the agent causing this change
     * @param  reason an optional reason to be logged, for humans
     * @param  transaction the transaction to which write operations are added by this
     * @return the nodes in their state as it will be written if committed
     */
    public List<Node> writeTo(Node.State toState, List<Node> nodes,
                              Agent agent, Optional<String> reason,
                              NestedTransaction transaction) {
        if (nodes.isEmpty()) return nodes;

        List<Node> writtenNodes = new ArrayList<>(nodes.size());

        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        for (Node node : nodes) {
            Node newNode = new Node(node.openStackId(), node.ipAddresses(), node.additionalIpAddresses(), node.hostname(),
                                    node.parentHostname(), node.flavor(),
                                    newNodeStatus(node, toState),
                                    toState,
                                    toState.isAllocated() ? node.allocation() : Optional.empty(),
                                    recordStateTransition(node, toState, agent),
                                    node.type());
            curatorTransaction.add(CuratorOperations.delete(toPath(node).getAbsolute()))
                              .add(CuratorOperations.create(toPath(toState, newNode.hostname()).getAbsolute(), nodeSerializer.toJson(newNode)));
            writtenNodes.add(newNode);
        }

        transaction.onCommitted(() -> { // schedule logging on commit of nodes which changed state
            for (Node node : nodes) {
                if (toState != node.state())
                    log.log(LogLevel.INFO, agent + " moved " + node + " to " + toState + reason.map(s -> ": " + s).orElse(""));
            }
        });
        return writtenNodes;
    }

    private History recordStateTransition(Node node, Node.State toState, Agent agent) {
        return node.history().recordStateTransition(node.state(), toState, agent, clock.instant());
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
    public Lock lockInactive() {
        return lock(root.append("locks").append("unallocatedLock"), defaultLockTimeout);
    }

    /** Acquires the single cluster-global, reentrant lock for active nodes of this application */
    public Lock lock(ApplicationId application) {
        return lock(application, defaultLockTimeout);
    }

    /** Acquires the single cluster-global, reentrant lock with the specified timeout for active nodes of this application */
    public Lock lock(ApplicationId application, Duration timeout) {
        try {
            return lock(lockPath(application), timeout);
        }
        catch (UncheckedTimeoutException e) {
            throw new ApplicationLockException(e);
        }
    }

    private Lock lock(Path path, Duration timeout) {
        return curatorDatabase.lock(path, timeout);
    }

    /**
     * Returns a default flavor specific for an application, or empty if not available.
     */
    public Optional<String> getDefaultFlavorForApplication(ApplicationId applicationId) {
        Optional<byte[]> utf8DefaultFlavor = curatorDatabase.getData(defaultFlavorPath(applicationId));
        return utf8DefaultFlavor.map((flavor) -> new String(flavor, StandardCharsets.UTF_8));
    }

    private Path defaultFlavorPath(ApplicationId applicationId) {
        return root.append("defaultFlavor").append(applicationId.serializedForm());
    }

    public Set<String> readInactiveJobs() {
        try {
            byte[] data = curatorDatabase.getData(inactiveJobsPath()).get();
            if (data.length == 0) return new HashSet<>(); // inactive jobs has never been written
            return stringSetSerializer.fromJson(data);
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Error reading inactive jobs, deleting inactive state");
            writeInactiveJobs(Collections.emptySet());
            return new HashSet<>();
        }
    }

    public void writeInactiveJobs(Set<String> inactiveJobs) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.setData(inactiveJobsPath().getAbsolute(),
                                                         stringSetSerializer.toJson(inactiveJobs)));
        transaction.commit();
    }
    
    public Lock lockInactiveJobs() {
        return lock(root.append("locks").append("inactiveJobsLock"), defaultLockTimeout);
    }

    private Path inactiveJobsPath() {
        return root.append("inactiveJobs");
    }
    
}
