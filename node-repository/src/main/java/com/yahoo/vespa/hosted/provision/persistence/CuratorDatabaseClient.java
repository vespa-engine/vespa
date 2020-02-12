// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;

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
    private static final Path lockRoot = root.append("locks");
    private static final Path loadBalancersRoot = root.append("loadBalancers");
    private static final Duration defaultLockTimeout = Duration.ofMinutes(2);

    private final NodeSerializer nodeSerializer;
    private final StringSetSerializer stringSetSerializer = new StringSetSerializer();
    private final CuratorDatabase curatorDatabase;
    private final Clock clock;
    private final Zone zone;
    private final CuratorCounter provisionIndexCounter;

    public CuratorDatabaseClient(NodeFlavors flavors, Curator curator, Clock clock, Zone zone, boolean useCache) {
        this.nodeSerializer = new NodeSerializer(flavors);
        this.zone = zone;
        this.curatorDatabase = new CuratorDatabase(curator, root, useCache);
        this.clock = clock;
        this.provisionIndexCounter = new CuratorCounter(curator, root.append("provisionIndexCounter").getAbsolute());
        initZK();
    }

    public List<HostName> cluster() {
        return curatorDatabase.cluster();
    }

    private void initZK() {
        curatorDatabase.create(root);
        for (Node.State state : Node.State.values())
            curatorDatabase.create(toPath(state));
        curatorDatabase.create(inactiveJobsPath());
        curatorDatabase.create(infrastructureVersionsPath());
        curatorDatabase.create(osVersionsPath());
        curatorDatabase.create(dockerImagesPath());
        curatorDatabase.create(firmwareCheckPath());
        curatorDatabase.create(loadBalancersRoot);
        provisionIndexCounter.initialize(100);
    }

    /**
     * Adds a set of nodes. Rollbacks/fails transaction if any node is not in the expected state.
     */
    public List<Node> addNodesInState(List<Node> nodes, Node.State expectedState) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        for (Node node : nodes) {
            if (node.state() != expectedState)
                throw new IllegalArgumentException(node + " is not in the " + expectedState + " state");

            node = node.with(node.history().recordStateTransition(null, expectedState, Agent.system, clock.instant()));
            curatorTransaction.add(CuratorOperations.create(toPath(node).getAbsolute(), nodeSerializer.toJson(node)));
        }
        transaction.commit();

        for (Node node : nodes)
            log.log(LogLevel.INFO, "Added " + node);

        return nodes;
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
            Node newNode = new Node(node.id(), node.ipConfig(), node.hostname(),
                                    node.parentHostname(), node.flavor(),
                                    newNodeStatus(node, toState),
                                    toState,
                                    toState.isAllocated() ? node.allocation() : Optional.empty(),
                                    node.history().recordStateTransition(node.state(), toState, agent, clock.instant()),
                                    node.type(), node.reports(), node.modelName(), node.reservedTo());
            writeNode(toState, curatorTransaction, node, newNode);
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

    private void writeNode(Node.State toState, CuratorTransaction curatorTransaction, Node node, Node newNode) {
        byte[] nodeData = nodeSerializer.toJson(newNode);
        String currentNodePath = toPath(node).getAbsolute();
        String newNodePath = toPath(toState, newNode.hostname()).getAbsolute();
        if (newNodePath.equals(currentNodePath)) {
            curatorTransaction.add(CuratorOperations.setData(currentNodePath, nodeData));
        } else {
            curatorTransaction.add(CuratorOperations.delete(currentNodePath))
                              .add(CuratorOperations.create(newNodePath, nodeData));
        }
    }

    private Status newNodeStatus(Node node, Node.State toState) {
        if (node.state() != Node.State.failed && toState == Node.State.failed) return node.status().withIncreasedFailCount();
        if (node.state() == Node.State.failed && toState == Node.State.active) return node.status().withDecreasedFailCount(); // fail undo
        if (rebootOnTransitionTo(toState, node)) {
            return node.status().withReboot(node.status().reboot().withIncreasedWanted());
        }
        return node.status();
    }

    /** Returns whether to reboot node as part of transition to given state. This is done to get rid of any lingering
     * unwanted state (e.g. processes) on non-host nodes. */
    private boolean rebootOnTransitionTo(Node.State state, Node node) {
        if (node.type().isDockerHost()) return false; // Reboot of host nodes is handled by NodeRebooter
        if (zone.environment().isTest()) return false; // We want to reuse nodes quickly in test environments

        return node.state() != Node.State.dirty && state == Node.State.dirty;
    }

    /**
     * Returns all nodes which are in one of the given states.
     * If no states are given this returns all nodes.
     */
    public List<Node> getNodes(Node.State ... states) {
        List<Node> nodes = new ArrayList<>();
        if (states.length == 0)
            states = Node.State.values();
        CuratorDatabase.Session session = curatorDatabase.getSession();
        for (Node.State state : states) {
            for (String hostname : session.getChildren(toPath(state))) {
                Optional<Node> node = getNode(session, hostname, state);
                node.ifPresent(nodes::add); // node might disappear between getChildren and getNode
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
    public Optional<Node> getNode(CuratorDatabase.Session session, String hostname, Node.State ... states) {
        if (states.length == 0)
            states = Node.State.values();
        for (Node.State state : states) {
            Optional<byte[]> nodeData = session.getData(toPath(state, hostname));
            if (nodeData.isPresent())
                return nodeData.map((data) -> nodeSerializer.fromJson(state, data));
        }
        return Optional.empty();
    }

    /** 
     * Returns a particular node, or empty if this noe is not in any of the given states.
     * If no states are given this returns the node if it is present in any state.
     */
    public Optional<Node> getNode(String hostname, Node.State ... states) {
        return getNode(curatorDatabase.getSession(), hostname, states);
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
                lockRoot
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
        return lock(lockRoot.append("unallocatedLock"), defaultLockTimeout);
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

    private <T> Optional<T> read(Path path, Function<byte[], T> mapper) {
        return curatorDatabase.getData(path).filter(data -> data.length > 0).map(mapper);
    }


    // Maintenance jobs
    public Set<String> readInactiveJobs() {
        try {
            return read(inactiveJobsPath(), stringSetSerializer::fromJson).orElseGet(HashSet::new);
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
        return lock(lockRoot.append("inactiveJobsLock"), defaultLockTimeout);
    }

    private Path inactiveJobsPath() {
        return root.append("inactiveJobs");
    }


    // Infrastructure versions
    public Map<NodeType, Version> readInfrastructureVersions() {
        return read(infrastructureVersionsPath(), NodeTypeVersionsSerializer::fromJson).orElseGet(TreeMap::new);
    }

    public void writeInfrastructureVersions(Map<NodeType, Version> infrastructureVersions) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.setData(infrastructureVersionsPath().getAbsolute(),
                                                         NodeTypeVersionsSerializer.toJson(infrastructureVersions)));
        transaction.commit();
    }

    public Lock lockInfrastructureVersions() {
        return lock(lockRoot.append("infrastructureVersionsLock"), defaultLockTimeout);
    }

    private Path infrastructureVersionsPath() {
        return root.append("infrastructureVersions");
    }


    // OS versions
    public Map<NodeType, Version> readOsVersions() {
        return read(osVersionsPath(), OsVersionsSerializer::fromJson).orElseGet(TreeMap::new);
    }

    public void writeOsVersions(Map<NodeType, Version> versions) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.setData(osVersionsPath().getAbsolute(),
                                                         OsVersionsSerializer.toJson(versions)));
        transaction.commit();
    }

    public Lock lockOsVersions() {
        return lock(lockRoot.append("osVersionsLock"), defaultLockTimeout);
    }

    private Path osVersionsPath() {
        return root.append("osVersions");
    }


    // Docker images
    public Map<NodeType, DockerImage> readDockerImages() {
        return read(dockerImagesPath(), NodeTypeDockerImagesSerializer::fromJson).orElseGet(TreeMap::new);
    }

    public void writeDockerImages(Map<NodeType, DockerImage> dockerImages) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.setData(dockerImagesPath().getAbsolute(),
                NodeTypeDockerImagesSerializer.toJson(dockerImages)));
        transaction.commit();
    }

    public Lock lockDockerImages() {
        return lock(lockRoot.append("dockerImagesLock"), defaultLockTimeout);
    }

    private Path dockerImagesPath() {
        return root.append("dockerImages");
    }


    // Firmware checks
    /** Stores the instant after which a firmware check is required, or clears any outstanding ones if empty is given. */
    public void writeFirmwareCheck(Optional<Instant> after) {
        byte[] data = after.map(instant -> Long.toString(instant.toEpochMilli()).getBytes())
                           .orElse(new byte[0]);
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.setData(firmwareCheckPath().getAbsolute(), data));
        transaction.commit();
    }

    /** Returns the instant after which a firmware check is required, if any. */
    public Optional<Instant> readFirmwareCheck() {
        return read(firmwareCheckPath(), data -> Instant.ofEpochMilli(Long.parseLong(new String(data))));
    }

    private Path firmwareCheckPath() {
        return root.append("firmwareCheck");
    }


    // Load balancers
    public List<LoadBalancerId> readLoadBalancerIds() {
        return readLoadBalancerIds((ignored) -> true);
    }

    public Map<LoadBalancerId, LoadBalancer> readLoadBalancers(Predicate<LoadBalancerId> filter) {
        return readLoadBalancerIds(filter).stream()
                                          .map(this::readLoadBalancer)
                                          .filter(Optional::isPresent)
                                          .map(Optional::get)
                                          .collect(collectingAndThen(toMap(LoadBalancer::id, Function.identity()),
                                                                     Collections::unmodifiableMap));
    }

    public Optional<LoadBalancer> readLoadBalancer(LoadBalancerId id) {
        return read(loadBalancerPath(id), LoadBalancerSerializer::fromJson);
    }

    public void writeLoadBalancer(LoadBalancer loadBalancer) {
        NestedTransaction transaction = new NestedTransaction();
        writeLoadBalancers(List.of(loadBalancer), transaction);
        transaction.commit();
    }

    public void writeLoadBalancers(Collection<LoadBalancer> loadBalancers, NestedTransaction transaction) {
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        loadBalancers.forEach(loadBalancer -> {
            curatorTransaction.add(createOrSet(loadBalancerPath(loadBalancer.id()),
                                               LoadBalancerSerializer.toJson(loadBalancer)));
        });
    }

    public void removeLoadBalancer(LoadBalancerId loadBalancer) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = curatorDatabase.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.delete(loadBalancerPath(loadBalancer).getAbsolute()));
        transaction.commit();
    }

    public Lock lockLoadBalancers() {
        return lock(lockRoot.append("loadBalancersLock"), defaultLockTimeout);
    }

    private Path loadBalancerPath(LoadBalancerId id) {
        return loadBalancersRoot.append(id.serializedForm());
    }

    private List<LoadBalancerId> readLoadBalancerIds(Predicate<LoadBalancerId> predicate) {
        return curatorDatabase.getChildren(loadBalancersRoot).stream()
                              .map(LoadBalancerId::fromSerializedForm)
                              .filter(predicate)
                              .collect(Collectors.toUnmodifiableList());
    }

    private Transaction.Operation createOrSet(Path path, byte[] data) {
        if (curatorDatabase.exists(path)) {
            return CuratorOperations.setData(path.getAbsolute(), data);
        }
        return CuratorOperations.create(path.getAbsolute(), data);
    }

    /** Returns a given number of unique provision indexes */
    public List<Integer> getProvisionIndexes(int numIndexes) {
        if (numIndexes < 1)
            throw new IllegalArgumentException("numIndexes must be a positive integer, was " + numIndexes);

        int firstProvisionIndex = (int) provisionIndexCounter.add(numIndexes) - numIndexes;
        return IntStream.range(0, numIndexes)
                .mapToObj(i -> firstProvisionIndex + i)
                .collect(Collectors.toList());
    }
}
