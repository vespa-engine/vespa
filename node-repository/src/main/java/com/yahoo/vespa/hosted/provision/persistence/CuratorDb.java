// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import ai.vespa.http.DomainName;
import com.yahoo.component.Version;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.archive.ArchiveUris;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.os.OsVersionChange;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.stream.CustomCollectors.toLinkedMap;
import static java.util.stream.Collectors.collectingAndThen;

/**.
 * A persistent database for the contents of the node repository, backed by {@link CachingCurator}.
 *
 * Nodes are stored in /provision/v1/nodes/[hostname].
 *
 * The responsibility of this class is to turn operations on the level of node states, applications and nodes
 * into operations on the level of file paths and bytes.
 *
 * @author bratseth
 */
public class CuratorDb {

    private static final Logger log = Logger.getLogger(CuratorDb.class.getName());

    private static final Path root = Path.fromString("/provision/v1");
    private static final Path lockPath = root.append("locks");
    private static final Path nodesPath = root.append("nodes");
    private static final Path loadBalancersPath = root.append("loadBalancers");
    private static final Path applicationsPath = root.append("applications");
    private static final Path inactiveJobsPath = root.append("inactiveJobs");
    private static final Path infrastructureVersionsPath = root.append("infrastructureVersions");
    private static final Path osVersionsPath = root.append("osVersions");
    private static final Path firmwareCheckPath = root.append("firmwareCheck");
    private static final Path archiveUrisPath = root.append("archiveUri");

    private static final Duration defaultLockTimeout = Duration.ofMinutes(1);

    private final NodeSerializer nodeSerializer;
    private final CachingCurator db;
    private final Clock clock;
    private final CuratorCounter provisionIndexCounter;

    public CuratorDb(NodeFlavors flavors, Curator curator, Clock clock, boolean useCache, long nodeCacheSize) {
        this.nodeSerializer = new NodeSerializer(flavors, nodeCacheSize);
        this.db = new CachingCurator(curator, root, useCache);
        this.clock = clock;
        this.provisionIndexCounter = new CuratorCounter(curator, root.append("provisionIndexCounter"));
        initZK();
    }

    public List<String> cluster() {
        return db.cluster().stream().map(HostName::value).toList();
    }

    private void initZK() {
        db.create(root);
        db.create(nodesPath);
        db.create(applicationsPath);
        db.create(inactiveJobsPath);
        db.create(infrastructureVersionsPath);
        db.create(osVersionsPath);
        db.create(firmwareCheckPath);
        db.create(archiveUrisPath);
        db.create(loadBalancersPath);
        provisionIndexCounter.initialize(100);
    }

    /** Adds a set of nodes. Rollbacks/fails transaction if any node is not in the expected state. */
    public List<Node> addNodesInState(List<Node> nodes, Node.State expectedState, Agent agent, NestedTransaction transaction) {
        CuratorTransaction curatorTransaction = db.newCuratorTransactionIn(transaction);
        for (Node node : nodes) {
            if (node.state() != expectedState)
                throw new IllegalArgumentException(node + " is not in the " + expectedState + " state");

            node = node.with(node.history().recordStateTransition(null, expectedState, agent, clock.instant()));
            byte[] serialized = nodeSerializer.toJson(node);
            curatorTransaction.add(CuratorOperations.create(nodePath(node).getAbsolute(), serialized));
        }
        transaction.onCommitted(() -> nodes.forEach(node -> log.log(Level.INFO, "Added " + node)));
        return nodes;
    }

    public List<Node> addNodesInState(List<Node> nodes, Node.State expectedState, Agent agent) {
        NestedTransaction transaction = new NestedTransaction();
        List<Node> writtenNodes = addNodesInState(nodes, expectedState, agent, transaction);
        transaction.commit();
        return writtenNodes;
    }

    /** Removes given nodes in transaction */
    public void removeNodes(List<Node> nodes, NestedTransaction transaction) {
        for (Node node : nodes) {
            CuratorTransaction curatorTransaction = db.newCuratorTransactionIn(transaction);
            curatorTransaction.add(CuratorOperations.delete(nodePath(node).getAbsolute()));
        }
        transaction.onCommitted(() -> nodes.forEach(node -> log.log(Level.INFO, "Removed node " + node.hostname() + " in state " + node.state())));
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
     * Writes the given nodes to the given state (whether they are already in this state or another),
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

        CuratorTransaction curatorTransaction = db.newCuratorTransactionIn(transaction);
        for (Node node : nodes) {
            Node newNode = new Node(node.id(), node.ipConfig(), node.hostname(),
                                    node.parentHostname(), node.flavor(),
                                    newNodeStatus(node, toState),
                                    toState,
                                    toState.isAllocated() ? node.allocation() : Optional.empty(),
                                    node.history().recordStateTransition(node.state(), toState, agent, clock.instant()),
                                    node.type(), node.reports(), node.modelName(), node.reservedTo(),
                                    node.exclusiveToApplicationId(), node.exclusiveToClusterType(), node.switchHostname(),
                                    node.trustedCertificates(), node.cloudAccount(), node.wireguardPubKey());
            curatorTransaction.add(createOrSet(nodePath(newNode), nodeSerializer.toJson(newNode)));
            writtenNodes.add(newNode);
        }

        transaction.onCommitted(() -> { // schedule logging on commit of nodes which changed state
            for (Node node : nodes) {
                if (toState != node.state())
                    log.log(Level.INFO, agent + " moved " + node + " to " + toState + reason.map(s -> ": " + s).orElse(""));
            }
        });
        return writtenNodes;
    }

    private Status newNodeStatus(Node node, Node.State toState) {
        if (node.state() != Node.State.failed && toState == Node.State.failed) return node.status().withIncreasedFailCount();
        if (node.state() == Node.State.failed && toState == Node.State.active) return node.status().withDecreasedFailCount(); // fail undo
        return node.status();
    }

    /** Returns all existing nodes */
    public List<Node> readNodes() {
        CachingCurator.Session session = db.getSession();
        return session.getChildren(nodesPath).stream()
                      .flatMap(hostname -> readNode(session, hostname).stream())
                      .toList();
    }

    private Optional<Node> readNode(CachingCurator.Session session, String hostname) {
        return session.getData(nodePath(hostname)).map(nodeSerializer::fromJson);
    }

    /** Read node with given hostname, if any such node exists */
    public Optional<Node> readNode(String hostname) {
        return readNode(db.getSession(), hostname);
    }

    private Path nodePath(Node node) {
        return nodePath(node.hostname());
    }

    private Path nodePath(String hostname) {
        return nodesPath.append(hostname);
    }

    /** Creates and returns the path to the lock for this application */
    private Path lockPath(ApplicationId application) {
        Path lockPath = CuratorDb.lockPath.append(application.tenant().value())
                                          .append(application.application().value())
                                          .append(application.instance().value());
        db.create(lockPath);
        return lockPath;
    }

    /** Acquires the single cluster-global, reentrant lock for all non-active nodes */
    public Lock lockInactive() {
        return lockInactive(defaultLockTimeout);
    }

    /** Acquires the single cluster-global, reentrant lock for all non-active nodes */
    public Lock lockInactive(Duration timeout) {
        return db.lock(lockPath.append("unallocatedLock"), timeout);
    }

    /** Acquires the single cluster-global, reentrant lock for active nodes of this application */
    public Lock lock(ApplicationId application) {
        return lock(application, defaultLockTimeout);
    }

    /** Acquires the single cluster-global, reentrant lock with the specified timeout for active nodes of this application */
    public Lock lock(ApplicationId application, Duration timeout) {
        try {
            return db.lock(lockPath(application), timeout);
        }
        catch (UncheckedTimeoutException e) {
            throw new ApplicationLockException(e);
        }
    }

    // Applications -----------------------------------------------------------

    public List<ApplicationId> readApplicationIds() {
        return db.getChildren(applicationsPath).stream()
                 .map(ApplicationId::fromSerializedForm)
                 .toList();
    }

    public Optional<Application> readApplication(ApplicationId id) {
        return read(applicationPath(id), ApplicationSerializer::fromJson);
    }

    public void writeApplication(Application application, NestedTransaction transaction) {
        db.newCuratorTransactionIn(transaction)
          .add(createOrSet(applicationPath(application.id()),
                                        ApplicationSerializer.toJson(application)));
    }

    public void deleteApplication(ApplicationTransaction transaction) {
        if (db.exists(applicationPath(transaction.application())))
            db.newCuratorTransactionIn(transaction.nested())
              .add(CuratorOperations.delete(applicationPath(transaction.application()).getAbsolute()));
    }

    private Path applicationPath(ApplicationId id) {
        return applicationsPath.append(id.serializedForm());
    }

    // Maintenance jobs -----------------------------------------------------------

    public Lock lockMaintenanceJob(String jobName) {
        return db.lock(lockPath.append("maintenanceJobLocks").append(jobName), defaultLockTimeout);
    }

    // Infrastructure versions -----------------------------------------------------------

    public Map<NodeType, Version> readInfrastructureVersions() {
        return read(infrastructureVersionsPath, NodeTypeVersionsSerializer::fromJson).orElseGet(TreeMap::new);
    }

    public void writeInfrastructureVersions(Map<NodeType, Version> infrastructureVersions) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = db.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.setData(infrastructureVersionsPath.getAbsolute(),
                                                         NodeTypeVersionsSerializer.toJson(infrastructureVersions)));
        transaction.commit();
    }

    public Lock lockInfrastructureVersions() {
        return db.lock(lockPath.append("infrastructureVersionsLock"), defaultLockTimeout);
    }

    // OS versions -----------------------------------------------------------

    public OsVersionChange readOsVersionChange() {
        return read(osVersionsPath, OsVersionChangeSerializer::fromJson).orElse(OsVersionChange.NONE);
    }

    public void writeOsVersionChange(OsVersionChange change) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = db.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.setData(osVersionsPath.getAbsolute(),
                                                         OsVersionChangeSerializer.toJson(change)));
        transaction.commit();
    }

    public Lock lockOsVersionChange() {
        return db.lock(lockPath.append("osVersionsLock"), defaultLockTimeout);
    }

    // Firmware checks -----------------------------------------------------------

    /** Stores the instant after which a firmware check is required, or clears any outstanding ones if empty is given. */
    public void writeFirmwareCheck(Optional<Instant> after) {
        byte[] data = after.map(instant -> Long.toString(instant.toEpochMilli()).getBytes())
                           .orElse(new byte[0]);
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = db.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.setData(firmwareCheckPath.getAbsolute(), data));
        transaction.commit();
    }

    /** Returns the instant after which a firmware check is required, if any. */
    public Optional<Instant> readFirmwareCheck() {
        return read(firmwareCheckPath, data -> Instant.ofEpochMilli(Long.parseLong(new String(data))));
    }

    // Archive URIs -----------------------------------------------------------

    public void writeArchiveUris(ArchiveUris archiveUris) {
        byte[] data = ArchiveUriSerializer.toJson(archiveUris);
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = db.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.setData(archiveUrisPath.getAbsolute(), data));
        transaction.commit();
    }

    public ArchiveUris readArchiveUris() {
        return read(archiveUrisPath, ArchiveUriSerializer::fromJson).orElseGet(() -> new ArchiveUris(Map.of(), Map.of()));
    }

    public Lock lockArchiveUris() {
        return db.lock(lockPath.append("archiveUris"), defaultLockTimeout);
    }

    public Lock lockMaintenance(ApplicationId application) {
        return db.lock(lockPath.append("maintenanceDeployment").append(application.serializedForm()),
                       Duration.ofSeconds(3));
    }

    // Load balancers -----------------------------------------------------------

    public List<LoadBalancerId> readLoadBalancerIds() {
        return readLoadBalancerIds((ignored) -> true);
    }

    public Map<LoadBalancerId, LoadBalancer> readLoadBalancers(Predicate<LoadBalancerId> filter) {
        return readLoadBalancerIds(filter).stream()
                                          .map(this::readLoadBalancer)
                                          .filter(Optional::isPresent)
                                          .map(Optional::get)
                                          .collect(collectingAndThen(toLinkedMap(LoadBalancer::id,
                                                                                 Function.identity()),
                                                                     Collections::unmodifiableMap));
    }

    public Optional<LoadBalancer> readLoadBalancer(LoadBalancerId id) {
        return read(loadBalancerPath(id), LoadBalancerSerializer::fromJson);
    }

    public void writeLoadBalancer(LoadBalancer loadBalancer, LoadBalancer.State fromState) {
        NestedTransaction transaction = new NestedTransaction();
        writeLoadBalancers(List.of(loadBalancer), fromState, transaction);
        transaction.commit();
    }

    public void writeLoadBalancers(Collection<LoadBalancer> loadBalancers, LoadBalancer.State fromState, NestedTransaction transaction) {
        CuratorTransaction curatorTransaction = db.newCuratorTransactionIn(transaction);
        loadBalancers.forEach(loadBalancer -> {
            curatorTransaction.add(createOrSet(loadBalancerPath(loadBalancer.id()),
                                               LoadBalancerSerializer.toJson(loadBalancer)));
        });
        transaction.onCommitted(() -> {
            for (var lb : loadBalancers) {
                if (lb.state() == fromState) continue;
                Optional<String> target = lb.instance().flatMap(instance -> instance.hostname().map(DomainName::value).or(instance::ipAddress));
                if (fromState == null) {
                    log.log(Level.INFO, () -> "Creating " + lb.id() + target.map(t -> " (" +  t + ")").orElse("") +
                                              " in " + lb.state());
                } else {
                    log.log(Level.INFO, () -> "Moving " + lb.id() + target.map(t -> " (" +  t + ")").orElse("") +
                                              " from " + fromState +
                                              " to " + lb.state());
                }
            }
        });
    }

    public void removeLoadBalancer(LoadBalancerId loadBalancer) {
        NestedTransaction transaction = new NestedTransaction();
        CuratorTransaction curatorTransaction = db.newCuratorTransactionIn(transaction);
        curatorTransaction.add(CuratorOperations.delete(loadBalancerPath(loadBalancer).getAbsolute()));
        transaction.commit();
    }

    private Path loadBalancerPath(LoadBalancerId id) {
        return loadBalancersPath.append(id.serializedForm());
    }

    private List<LoadBalancerId> readLoadBalancerIds(Predicate<LoadBalancerId> predicate) {
        return db.getChildren(loadBalancersPath).stream()
                 .map(LoadBalancerId::fromSerializedForm)
                 .filter(predicate)
                 .toList();
    }

    /** Returns a given number of unique provision indices */
    public List<Integer> readProvisionIndices(int count) {
        if (count < 1)
            throw new IllegalArgumentException("count must be a positive integer, was " + count);

        int firstIndex = (int) provisionIndexCounter.add(count) - count;
        return IntStream.range(0, count)
                        .mapToObj(i -> firstIndex + i)
                        .toList();
    }

    public CacheStats cacheStats() {
        return db.cacheStats();
    }

    public CacheStats nodeSerializerCacheStats() {
        return nodeSerializer.cacheStats();
    }

    private <T> Optional<T> read(Path path, Function<byte[], T> mapper) {
        return db.getData(path).filter(data -> data.length > 0).map(mapper);
    }

    private Transaction.Operation createOrSet(Path path, byte[] data) {
        return db.exists(path) ? CuratorOperations.setData(path.getAbsolute(), data)
                                            : CuratorOperations.create(path.getAbsolute(), data);
    }

}
