// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.yahoo.collections.ListMap;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.NodeRepositoryConfig;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.hosted.provision.maintenance.PeriodicApplicationMaintainer;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.NodeAcl;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;
import com.yahoo.vespa.hosted.provision.node.filter.StateFilter;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;
import com.yahoo.vespa.hosted.provision.persistence.DnsNameResolver;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;
import com.yahoo.vespa.hosted.provision.restapi.v2.NotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The hosted Vespa production node repository, which stores its state in Zookeeper.
 * The node repository knows about all nodes in a zone, their states and manages all transitions between
 * node states.
 * <p>
 * Node repo locking: Locks must be acquired before making changes to the set of nodes, or to the content
 * of the nodes.
 * Unallocated states use a single lock, while application level locks are used for all allocated states
 * such that applications can mostly change in parallel.
 * If both locks are needed acquire the application lock first, then the unallocated lock.
 * <p>
 * Changes to the set of active nodes must be accompanied by changes to the config model of the application.
 * Such changes are not handled by the node repository but by the classes calling it - see
 * {@link com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner} for such changes initiated
 * by the application package and {@link PeriodicApplicationMaintainer}
 * for changes initiated by the node repository.
 * Refer to {@link com.yahoo.vespa.hosted.provision.maintenance.NodeRepositoryMaintenance} for timing details
 * of the node state transitions.
 *
 * @author bratseth
 */
// Node state transitions:
// 1) (new) - > provisioned -> dirty -> ready -> reserved -> active -> inactive -> dirty -> ready
// 2) inactive -> reserved | parked
// 3) reserved -> dirty
// 3) * -> failed | parked -> dirty | active | (removed)
// Nodes have an application assigned when in states reserved, active and inactive.
// Nodes might have an application assigned in dirty.
public class NodeRepository extends AbstractComponent {

    private final CuratorDatabaseClient db;
    private final Curator curator;
    private final Clock clock;
    private final NodeFlavors flavors;
    private final NameResolver nameResolver;
    private final DockerImage dockerImage;

    /**
     * Creates a node repository form a zookeeper provider.
     * This will use the system time to make time-sensitive decisions
     */
    @Inject
    public NodeRepository(NodeRepositoryConfig config, NodeFlavors flavors, Curator curator, Zone zone) {
        this(flavors, curator, Clock.systemUTC(), zone, new DnsNameResolver(), new DockerImage(config.dockerImage()));
    }

    /**
     * Creates a node repository form a zookeeper provider and a clock instance
     * which will be used for time-sensitive decisions.
     */
    public NodeRepository(NodeFlavors flavors, Curator curator, Clock clock, Zone zone, NameResolver nameResolver,
                          DockerImage dockerImage) {
        this.db = new CuratorDatabaseClient(flavors, curator, clock, zone);
        this.curator = curator;
        this.clock = clock;
        this.flavors = flavors;
        this.nameResolver = nameResolver;
        this.dockerImage = dockerImage;

        // read and write all nodes to make sure they are stored in the latest version of the serialized format
        for (Node.State state : Node.State.values())
            db.writeTo(state, db.getNodes(state), Agent.system, Optional.empty());
    }

    /** Returns the curator database client used by this */
    public CuratorDatabaseClient database() { return db; }

    /** Returns the Docker image to use for nodes in this */
    public DockerImage dockerImage() { return dockerImage; }

    /** @return The name resolver used to resolve hostname and ip addresses */
    public NameResolver nameResolver() { return nameResolver; }

    // ---------------- Query API ----------------------------------------------------------------

    /**
     * Finds and returns the node with the hostname in any of the given states, or empty if not found 
     *
     * @param hostname the full host name of the node
     * @param inState the states the node may be in. If no states are given, it will be returned from any state
     * @return the node, or empty if it was not found in any of the given states
     */
    public Optional<Node> getNode(String hostname, Node.State ... inState) {
        return db.getNode(hostname, inState);
    }

    /**
     * Returns all nodes in any of the given states.
     *
     * @param inState the states to return nodes from. If no states are given, all nodes of the given type are returned
     * @return the node, or empty if it was not found in any of the given states
     */
    public List<Node> getNodes(Node.State ... inState) {
        return db.getNodes(inState).stream().collect(Collectors.toList());
    }
    /**
     * Finds and returns the nodes of the given type in any of the given states.
     *
     * @param type the node type to return
     * @param inState the states to return nodes from. If no states are given, all nodes of the given type are returned
     * @return the node, or empty if it was not found in any of the given states
     */
    public List<Node> getNodes(NodeType type, Node.State ... inState) {
        return db.getNodes(inState).stream().filter(node -> node.type().equals(type)).collect(Collectors.toList());
    }

    /**
     * Finds and returns all nodes that are children of the given parent node
     *
     * @param hostname Parent hostname
     * @return List of child nodes
     */
    public List<Node> getChildNodes(String hostname) {
        return db.getNodes().stream()
                .filter(node -> node.parentHostname()
                        .map(parentHostname -> parentHostname.equals(hostname))
                        .orElse(false))
                .collect(Collectors.toList());
    }

    public List<Node> getNodes(ApplicationId id, Node.State ... inState) { return db.getNodes(id, inState); }
    public List<Node> getInactive() { return db.getNodes(Node.State.inactive); }
    public List<Node> getFailed() { return db.getNodes(Node.State.failed); }

    /**
     * Returns a set of nodes that should be trusted by the given node.
     */
    private NodeAcl getNodeAcl(Node node, NodeList candidates) {
        Set<Node> trustedNodes = new TreeSet<>(Comparator.comparing(Node::hostname));
        Set<String> trustedNetworks = new HashSet<>();

        // For all cases below, trust:
        // - nodes in same application
        // - config servers
        node.allocation().ifPresent(allocation -> trustedNodes.addAll(candidates.owner(allocation.owner()).asList()));
        trustedNodes.addAll(getConfigNodes());

        switch (node.type()) {
            case tenant:
                // Tenant nodes in other states than ready, trust:
                // - proxy nodes
                // - parent (Docker) hosts of already trusted nodes. This is needed in a transition period, while
                //   we migrate away from IPv4-only nodes
                trustedNodes.addAll(candidates.parentNodes(trustedNodes).asList()); // TODO: Remove when we no longer have IPv4-only nodes
                trustedNodes.addAll(candidates.nodeType(NodeType.proxy).asList());
                if (node.state() == Node.State.ready) {
                    // Tenant nodes in state ready, trust:
                    // - All tenant nodes in zone. When a ready node is allocated to a an application there's a brief
                    //   window where current ACLs have not yet been applied on the node. To avoid service disruption
                    //   during this window, ready tenant nodes trust all other tenant nodes.
                    trustedNodes.addAll(candidates.nodeType(NodeType.tenant).asList());
                }
                break;

            case config:
                // Config servers trust all nodes
                trustedNodes.addAll(candidates.asList());
                break;

            case proxy:
                // No special rules for proxies
                break;

            case host:
                // Docker bridge network
                trustedNetworks.add("172.17.0.0/16");
                break;

            default:
                throw new IllegalArgumentException(
                        String.format("Don't know how to create ACL for node [hostname=%s type=%s]",
                                node.hostname(), node.type()));
        }

        return new NodeAcl(node, trustedNodes, trustedNetworks);
    }

    /**
     * Creates a list of node ACLs which identify which nodes the given node should trust
     *
     * @param node Node for which to generate ACLs
     * @param children Return ACLs for the children of the given node (e.g. containers on a Docker host)
     * @return List of node ACLs
     */
    public List<NodeAcl> getNodeAcls(Node node, boolean children) {
        NodeList candidates = new NodeList(getNodes());
        if (children) {
            return candidates.childNodes(node).asList().stream()
                    .map(childNode -> getNodeAcl(childNode, candidates))
                    .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        } else {
            return Collections.singletonList(getNodeAcl(node, candidates));
        }
    }

    /** Get config node by hostname */
    public Optional<Node> getConfigNode(String hostname) {
        return getConfigNodes().stream()
                .filter(n -> hostname.equals(n.hostname()))
                .findFirst();
    }

    /** Get default flavor override for an application, if present. */
    public Optional<String> getDefaultFlavorOverride(ApplicationId applicationId) {
        return db.getDefaultFlavorForApplication(applicationId);
    }

    public NodeFlavors getAvailableFlavors() {
        return flavors;
    }

    // ----------------- Node lifecycle -----------------------------------------------------------

    /** Creates a new node object, without adding it to the node repo. If no IP address is given, it will be resolved */
    public Node createNode(String openStackId, String hostname, Set<String> ipAddresses, Set<String> additionalIpAddresses, Optional<String> parentHostname,
                           Flavor flavor, NodeType type) {
        if (ipAddresses.isEmpty()) {
            ipAddresses = nameResolver.getAllByNameOrThrow(hostname);
        }

        return Node.create(openStackId, ImmutableSet.copyOf(ipAddresses), additionalIpAddresses, hostname, parentHostname, flavor, type);
    }

    public Node createNode(String openStackId, String hostname, Set<String> ipAddresses, Optional<String> parentHostname,
                           Flavor flavor, NodeType type) {
        return createNode(openStackId, hostname, ipAddresses, Collections.emptySet(), parentHostname, flavor, type);
    }

    public Node createNode(String openStackId, String hostname, Optional<String> parentHostname,
                           Flavor flavor, NodeType type) {
        return createNode(openStackId, hostname, Collections.emptySet(), parentHostname, flavor, type);
    }

    /** Adds a list of newly created docker container nodes to the node repository as <i>reserved</i> nodes */
    public List<Node> addDockerNodes(List<Node> nodes) {
        for (Node node : nodes) {
            if (!node.flavor().getType().equals(Flavor.Type.DOCKER_CONTAINER)) {
                throw new IllegalArgumentException("Cannot add " + node.hostname() + ": This is not a docker node");
            }
            if (!node.allocation().isPresent()) {
                throw new IllegalArgumentException("Cannot add " + node.hostname() + ": Docker containers needs to be allocated");
            }
            Optional<Node> existing = getNode(node.hostname());
            if (existing.isPresent())
                throw new IllegalArgumentException("Cannot add " + node.hostname() + ": A node with this name already exists");
        }
        try (Mutex lock = lockUnallocated()) {
            return db.addNodesInState(nodes, Node.State.reserved);
        }
    }

    /** Adds a list of (newly created) nodes to the node repository as <i>provisioned</i> nodes */
    public List<Node> addNodes(List<Node> nodes) {
        for (Node node : nodes) {
            Optional<Node> existing = getNode(node.hostname());
            if (existing.isPresent())
                throw new IllegalArgumentException("Cannot add " + node.hostname() + ": A node with this name already exists");
        }
        try (Mutex lock = lockUnallocated()) {
            return db.addNodes(nodes);
        }
    }

    /** Sets a list of nodes ready and returns the nodes in the ready state */
    public List<Node> setReady(List<Node> nodes) {
        try (Mutex lock = lockUnallocated()) {
            List<Node> nodesWithResetFields = nodes.stream()
                    .map(node -> {
                        if (node.state() != Node.State.dirty)
                            throw new IllegalArgumentException("Can not set " + node + " ready. It is not dirty.");
                        return node.with(node.status().withWantToRetire(false).withWantToDeprovision(false));
                    })
                    .collect(Collectors.toList());

            return db.writeTo(Node.State.ready, nodesWithResetFields, Agent.system, Optional.empty());
        }
    }

    public Node setReady(String hostname) {
        Node nodeToReady = getNode(hostname).orElseThrow(() ->
                new NoSuchNodeException("Could not move " + hostname + " to ready: Node not found"));

        if (nodeToReady.state() == Node.State.ready) return nodeToReady;
        return setReady(Collections.singletonList(nodeToReady)).get(0);
    }

    /** Reserve nodes. This method does <b>not</b> lock the node repository */
    public List<Node> reserve(List<Node> nodes) { 
        return db.writeTo(Node.State.reserved, nodes, Agent.application, Optional.empty()); 
    }

    /** Activate nodes. This method does <b>not</b> lock the node repository */
    public List<Node> activate(List<Node> nodes, NestedTransaction transaction) {
        return db.writeTo(Node.State.active, nodes, Agent.application, Optional.empty(), transaction);
    }

    /**
     * Sets a list of nodes to have their allocation removable (active to inactive) in the node repository.
     *
     * @param application the application the nodes belong to
     * @param nodes the nodes to make removable. These nodes MUST be in the active state.
     */
    public void setRemovable(ApplicationId application, List<Node> nodes) {
        try (Mutex lock = lock(application)) {
            List<Node> removableNodes =
                nodes.stream().map(node -> node.with(node.allocation().get().removable()))
                              .collect(Collectors.toList());
            write(removableNodes);
        }
    }

    public void deactivate(ApplicationId application, NestedTransaction transaction) {
        try (Mutex lock = lock(application)) {
            db.writeTo(Node.State.inactive,
                       db.getNodes(application, Node.State.reserved, Node.State.active),
                       Agent.application, Optional.empty(), transaction
            );
        }
    }

    /**
     * Deactivates these nodes in a transaction and returns
     * the nodes in the new state which will hold if the transaction commits.
     * This method does <b>not</b> lock
     */
    public List<Node> deactivate(List<Node> nodes, NestedTransaction transaction) {
        return db.writeTo(Node.State.inactive, nodes, Agent.application, Optional.empty(), transaction);
    }

    /** Move nodes to the dirty state */
    public List<Node> setDirty(List<Node> nodes, Agent agent, String reason) {
        return performOn(NodeListFilter.from(nodes), node -> setDirty(node, agent, reason));
    }

    /**
     * Set a node dirty, which is in the provisioned, failed or parked state.
     * Use this to clean newly provisioned nodes or to recycle failed nodes which have been repaired or put on hold.
     *
     * @throws IllegalArgumentException if the node has hardware failure
     */
    public Node setDirty(Node node, Agent agent, String reason) {
        if (node.status().hardwareFailureDescription().isPresent())
            throw new IllegalArgumentException("Could not deallocate " + node.hostname() + ": It has a hardware failure");

        return db.writeTo(Node.State.dirty, node, agent, Optional.of(reason));
    }

    public Node setDirty(String hostname, Agent agent, String reason) {
        Node node = getNode(hostname, Node.State.provisioned, Node.State.failed, Node.State.parked).orElseThrow(() ->
                new IllegalArgumentException("Could not deallocate " + hostname + ": No such node in the provisioned, failed or parked state"));

        return setDirty(node, agent, reason);
    }

    /**
     * Fails this node and returns it in its new state.
     *
     * @return the node in its new state
     * @throws NoSuchNodeException if the node is not found
     */
    public Node fail(String hostname, Agent agent, String reason) {
        return move(hostname, Node.State.failed, agent, Optional.of(reason));
    }

    /**
     * Fails all the nodes that are children of hostname before finally failing the hostname itself.
     *
     * @return List of all the failed nodes in their new state
     */
    public List<Node> failRecursively(String hostname, Agent agent, String reason) {
        return moveRecursively(hostname, Node.State.failed, agent, Optional.of(reason));
    }

    /**
     * Parks this node and returns it in its new state.
     *
     * @return the node in its new state
     * @throws NoSuchNodeException if the node is not found
     */
    public Node park(String hostname, Agent agent, String reason) {
        return move(hostname, Node.State.parked, agent, Optional.of(reason));
    }

    /**
     * Parks all the nodes that are children of hostname before finally parking the hostname itself.
     *
     * @return List of all the parked nodes in their new state
     */
    public List<Node> parkRecursively(String hostname, Agent agent, String reason) {
        return moveRecursively(hostname, Node.State.parked, agent, Optional.of(reason));
    }

    /**
     * Moves a previously failed or parked node back to the active state.
     *
     * @return the node in its new state
     * @throws NoSuchNodeException if the node is not found
     */
    public Node reactivate(String hostname, Agent agent) {
        return move(hostname, Node.State.active, agent, Optional.empty());
    }

    private List<Node> moveRecursively(String hostname, Node.State toState, Agent agent, Optional<String> reason) {
        List<Node> moved = getChildNodes(hostname).stream()
                .map(child -> move(child, toState, agent, reason))
                .collect(Collectors.toList());

        moved.add(move(hostname, toState, agent, reason));
        return moved;
    }

    private Node move(String hostname, Node.State toState, Agent agent, Optional<String> reason) {
        Node node = getNode(hostname).orElseThrow(() ->
                new NoSuchNodeException("Could not move " + hostname + " to " + toState + ": Node not found"));
        return move(node, toState, agent, reason);
    }

    private Node move(Node node, Node.State toState, Agent agent, Optional<String> reason) {
        if (toState == Node.State.active && ! node.allocation().isPresent())
            throw new IllegalArgumentException("Could not set " + node.hostname() + " active. It has no allocation.");

        try (Mutex lock = lock(node)) {
            if (toState == Node.State.active) {
                for (Node currentActive : getNodes(node.allocation().get().owner(), Node.State.active)) {
                    if (node.allocation().get().membership().cluster().equals(currentActive.allocation().get().membership().cluster())
                        && node.allocation().get().membership().index() == currentActive.allocation().get().membership().index())
                        throw new IllegalArgumentException("Could not move " + node + " to active:" +
                                                           "It has the same cluster and index as an existing node");
                }
            }
            return db.writeTo(toState, node, agent, reason);
        }
    }

    /*
     * This method is used to enable a smooth rollout of dynamic docker flavor allocations. Once we have switch
     * everything this can be simplified to only deleting the node.
     *
     * Should only be called by node-admin for docker containers
     */
    public List<Node> markNodeAvailableForNewAllocation(String hostname) {
        Node node = getNode(hostname).orElseThrow(() -> new NotFoundException("No node with hostname \"" + hostname + '"'));
        if (node.flavor().getType() != Flavor.Type.DOCKER_CONTAINER) {
            throw new IllegalArgumentException(
                    "Cannot make " + hostname + " available for new allocation, must be a docker container node");
        } else if (node.state() != Node.State.dirty) {
            throw new IllegalArgumentException(
                    "Cannot make " + hostname + " available for new allocation, must be in state dirty, but was in " + node.state());
        }

        return removeRecursively(node, true);
    }

    /**
     * Removes all the nodes that are children of hostname before finally removing the hostname itself.
     *
     * @return List of all the nodes that have been removed
     */
    public List<Node> removeRecursively(String hostname) {
        Node node = getNode(hostname).orElseThrow(() -> new NotFoundException("No node with hostname \"" + hostname + '"'));
        return removeRecursively(node, false);
    }

    private List<Node> removeRecursively(Node node, boolean force) {
        try (Mutex lock = lockUnallocated()) {
            List<Node> removed = node.type() != NodeType.host ?
                    new ArrayList<>() :
                    getChildNodes(node.hostname()).stream()
                            .filter(child -> force || verifyRemovalIsAllowed(child, true))
                            .collect(Collectors.toList());

            if (force || verifyRemovalIsAllowed(node, false)) removed.add(node);
            db.removeNodes(removed);

            return removed;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to delete " + node.hostname(), e);
        }
    }

    /**
     * Allowed to a node delete if:
     *  Non-docker-container node: iff in state provisioned|failed|parked
     *  Docker-container-node:
     *    If only removing the container node: node in state ready
     *    If also removing the parent node: child is in state provisioned|failed|parked|ready
     */
    private boolean verifyRemovalIsAllowed(Node nodeToRemove, boolean deletingAsChild) {
        if (nodeToRemove.flavor().getType() == Flavor.Type.DOCKER_CONTAINER && !deletingAsChild) {
            if (nodeToRemove.state() != Node.State.ready) {
                throw new IllegalArgumentException(
                        String.format("Docker container node %s can only be removed when in state ready", nodeToRemove.hostname()));
            }

        } else if (nodeToRemove.flavor().getType() == Flavor.Type.DOCKER_CONTAINER) {
            List<Node.State> legalStates = Arrays.asList(Node.State.provisioned, Node.State.failed, Node.State.parked, Node.State.ready);

            if (! legalStates.contains(nodeToRemove.state())) {
                throw new IllegalArgumentException(String.format("Child node %s can only be removed from following states: %s",
                        nodeToRemove.hostname(), legalStates.stream().map(Node.State::name).collect(Collectors.joining(", "))));
            }
        } else {
            List<Node.State> legalStates = Arrays.asList(Node.State.provisioned, Node.State.failed, Node.State.parked);

            if (! legalStates.contains(nodeToRemove.state())) {
                throw new IllegalArgumentException(String.format("Node %s can only be removed from following states: %s",
                        nodeToRemove.hostname(), legalStates.stream().map(Node.State::name).collect(Collectors.joining(", "))));
            }
        }

        return true;
    }

    /**
     * Increases the restart generation of the active nodes matching the filter.
     * Returns the nodes in their new state.
     */
    public List<Node> restart(NodeFilter filter) {
        return performOn(StateFilter.from(Node.State.active, filter), node -> write(node.withRestart(node.allocation().get().restartGeneration().withIncreasedWanted())));
    }

    /**
     * Increases the reboot generation of the nodes matching the filter.
     * Returns the nodes in their new state.
     */
    public List<Node> reboot(NodeFilter filter) {
        return performOn(filter, node -> write(node.withReboot(node.status().reboot().withIncreasedWanted())));
    }

    /**
     * Writes this node after it has changed some internal state but NOT changed its state field.
     * This does NOT lock the node repository.
     *
     * @return the written node for convenience
     */
    public Node write(Node node) { return db.writeTo(node.state(), node, Agent.system, Optional.empty()); }

    /**
     * Writes these nodes after they have changed some internal state but NOT changed their state field.
     * This does NOT lock the node repository.
     *
     * @return the written nodes for convenience
     */
    public List<Node> write(List<Node> nodes) { return db.writeTo(nodes, Agent.system, Optional.empty()); }

    /**
     * Performs an operation requiring locking on all nodes matching some filter.
     *
     * @param filter the filter determining the set of nodes where the operation will be performed
     * @param action the action to perform
     * @return the set of nodes on which the action was performed, as they became as a result of the operation
     */
    private List<Node> performOn(NodeFilter filter, UnaryOperator<Node> action) {
        List<Node> unallocatedNodes = new ArrayList<>();
        ListMap<ApplicationId, Node> allocatedNodes = new ListMap<>();

        // Group matching nodes by the lock needed
        for (Node node : db.getNodes()) {
            if ( ! filter.matches(node)) continue;
            if (node.allocation().isPresent())
                allocatedNodes.put(node.allocation().get().owner(), node);
            else
                unallocatedNodes.add(node);
        }

        // perform operation while holding locks
        List<Node> resultingNodes = new ArrayList<>();
        try (Mutex lock = lockUnallocated()) {
            for (Node node : unallocatedNodes)
                resultingNodes.add(action.apply(node));
        }
        for (Map.Entry<ApplicationId, List<Node>> applicationNodes : allocatedNodes.entrySet()) {
            try (Mutex lock = lock(applicationNodes.getKey())) {
                for (Node node : applicationNodes.getValue())
                    resultingNodes.add(action.apply(node));
            }
        }
        return resultingNodes;
    }

    // Public for testing
    public List<Node> getConfigNodes() {
        // TODO: Revisit this when config servers are added to the repository
        return Arrays.stream(curator.zooKeeperEnsembleConnectionSpec().split(","))
                .map(hostPort -> hostPort.split(":")[0])
                .map(host -> createNode(host, host, Optional.empty(),
                        flavors.getFlavorOrThrow("v-4-8-100"), // Must be a flavor that exists in Hosted Vespa
                        NodeType.config))
                .collect(Collectors.toList());
    }

    /** Returns the time keeper of this system */
    public Clock clock() { return clock; }

    /** Create a lock which provides exclusive rights to making changes to the given application */
    public Mutex lock(ApplicationId application) { return db.lock(application); }

    /** Create a lock with a timeout which provides exclusive rights to making changes to the given application */
    public Mutex lock(ApplicationId application, Duration timeout) { return db.lock(application, timeout); }

    /** Create a lock which provides exclusive rights to changing the set of ready nodes */
    public Mutex lockUnallocated() { return db.lockInactive(); }

    /** Acquires the appropriate lock for this node */
    private Mutex lock(Node node) {
        return node.allocation().isPresent() ? lock(node.allocation().get().owner()) : lockUnallocated();
    }
}
