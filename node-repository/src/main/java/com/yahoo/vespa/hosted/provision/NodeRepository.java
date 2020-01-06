// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.google.inject.Inject;
import com.yahoo.collections.ListMap;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
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
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerList;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;
import com.yahoo.vespa.hosted.provision.maintenance.JobControl;
import com.yahoo.vespa.hosted.provision.maintenance.NodeFailer;
import com.yahoo.vespa.hosted.provision.maintenance.PeriodicApplicationMaintainer;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.NodeAcl;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;
import com.yahoo.vespa.hosted.provision.node.filter.StateFilter;
import com.yahoo.vespa.hosted.provision.os.OsVersions;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;
import com.yahoo.vespa.hosted.provision.persistence.DnsNameResolver;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;
import com.yahoo.vespa.hosted.provision.provisioning.DockerImages;
import com.yahoo.vespa.hosted.provision.provisioning.FirmwareChecks;
import com.yahoo.vespa.hosted.provision.restapi.v2.NotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
// 1) (new) - > provisioned -> (dirty ->) ready -> reserved -> active -> inactive -> dirty -> ready
// 2) inactive -> reserved | parked
// 3) reserved -> dirty
// 3) * -> failed | parked -> dirty | active | (removed)
// Nodes have an application assigned when in states reserved, active and inactive.
// Nodes might have an application assigned in dirty.
public class NodeRepository extends AbstractComponent {

    private final CuratorDatabaseClient db;
    private final Clock clock;
    private final Zone zone;
    private final NodeFlavors flavors;
    private final NameResolver nameResolver;
    private final OsVersions osVersions;
    private final InfrastructureVersions infrastructureVersions;
    private final FirmwareChecks firmwareChecks;
    private final DockerImages dockerImages;
    private final JobControl jobControl;

    /**
     * Creates a node repository from a zookeeper provider.
     * This will use the system time to make time-sensitive decisions
     */
    @Inject
    public NodeRepository(NodeRepositoryConfig config, NodeFlavors flavors, Curator curator, Zone zone) {
        this(flavors, curator, Clock.systemUTC(), zone, new DnsNameResolver(), DockerImage.fromString(config.dockerImage()), config.useCuratorClientCache());
    }

    /**
     * Creates a node repository from a zookeeper provider and a clock instance
     * which will be used for time-sensitive decisions.
     */
    public NodeRepository(NodeFlavors flavors, Curator curator, Clock clock, Zone zone, NameResolver nameResolver,
                          DockerImage dockerImage, boolean useCuratorClientCache) {
        this.db = new CuratorDatabaseClient(flavors, curator, clock, zone, useCuratorClientCache);
        this.zone = zone;
        this.clock = clock;
        this.flavors = flavors;
        this.nameResolver = nameResolver;
        this.osVersions = new OsVersions(this);
        this.infrastructureVersions = new InfrastructureVersions(db);
        this.firmwareChecks = new FirmwareChecks(db, clock);
        this.dockerImages = new DockerImages(db, dockerImage);
        this.jobControl = new JobControl(db);

        // read and write all nodes to make sure they are stored in the latest version of the serialized format
        for (Node.State state : Node.State.values())
            db.writeTo(state, db.getNodes(state), Agent.system, Optional.empty());
    }

    /** Returns the curator database client used by this */
    public CuratorDatabaseClient database() { return db; }

    /** Returns the Docker image to use for nodes in this */
    public DockerImage dockerImage(NodeType nodeType) { return dockerImages.dockerImageFor(nodeType); }

    /** @return The name resolver used to resolve hostname and ip addresses */
    public NameResolver nameResolver() { return nameResolver; }

    /** Returns the OS versions to use for nodes in this */
    public OsVersions osVersions() { return osVersions; }

    /** Returns the infrastructure versions to use for nodes in this */
    public InfrastructureVersions infrastructureVersions() { return infrastructureVersions; }

    /** Returns the status of firmware checks for hosts managed by this. */
    public FirmwareChecks firmwareChecks() { return firmwareChecks; }

    /** Returns the docker images to use for nodes in this. */
    public DockerImages dockerImages() { return dockerImages; }

    /** Returns the status of maintenance jobs managed by this. */
    public JobControl jobControl() { return jobControl; }

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
        return new ArrayList<>(db.getNodes(inState));
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

    /** Returns a filterable list of all nodes in this repository */
    public NodeList list() {
        return NodeList.copyOf(getNodes());
    }

    /** Returns a locked list of all nodes in this repository */
    public LockedNodeList list(Mutex lock) {
        return new LockedNodeList(getNodes(), lock);
    }

    /** Returns a filterable list of all load balancers in this repository */
    public LoadBalancerList loadBalancers() {
        return LoadBalancerList.copyOf(database().readLoadBalancers().values());
    }

    public List<Node> getNodes(ApplicationId id, Node.State ... inState) { return db.getNodes(id, inState); }
    public List<Node> getInactive() { return db.getNodes(Node.State.inactive); }
    public List<Node> getFailed() { return db.getNodes(Node.State.failed); }

    /**
     * Returns the ACL for the node (trusted nodes, networks and ports)
     */
    private NodeAcl getNodeAcl(Node node, NodeList candidates, LoadBalancerList loadBalancers) {
        Set<Node> trustedNodes = new TreeSet<>(Comparator.comparing(Node::hostname));
        Set<Integer> trustedPorts = new LinkedHashSet<>();
        Set<String> trustedNetworks = new LinkedHashSet<>();

        // For all cases below, trust:
        // - SSH: If the Docker host has one container, and it is using the Docker host's network namespace,
        //   opening up SSH to the Docker host is done here as a trusted port. For simplicity all nodes have
        //   SSH opened (which is safe for 2 reasons: SSH daemon is not run inside containers, and NPT networks
        //   will (should) not forward port 22 traffic to container).
        // - parent host (for health checks and metrics)
        // - nodes in same application
        // - load balancers allocated to application
        trustedPorts.add(22);
        candidates.parentOf(node).ifPresent(trustedNodes::add);
        node.allocation().ifPresent(allocation -> {
            trustedNodes.addAll(candidates.owner(allocation.owner()).asList());
            loadBalancers.owner(allocation.owner()).asList().stream()
                         .map(LoadBalancer::instance)
                         .map(LoadBalancerInstance::networks)
                         .forEach(trustedNetworks::addAll);
        });

        switch (node.type()) {
            case tenant:
                // Tenant nodes in other states than ready, trust:
                // - config servers
                // - proxy nodes
                // - parents of the nodes in the same application: If some of the nodes are on a different IP versions
                //   or only a subset of them are dual-stacked, the communication between the nodes may be NATed
                //   with via parent's IP address.
                trustedNodes.addAll(candidates.nodeType(NodeType.config).asList());
                trustedNodes.addAll(candidates.nodeType(NodeType.proxy).asList());
                node.allocation().ifPresent(allocation ->
                        trustedNodes.addAll(candidates.parentsOf(candidates.owner(allocation.owner()).asList()).asList()));

                if (node.state() == Node.State.ready) {
                    // Tenant nodes in state ready, trust:
                    // - All tenant nodes in zone. When a ready node is allocated to a an application there's a brief
                    //   window where current ACLs have not yet been applied on the node. To avoid service disruption
                    //   during this window, ready tenant nodes trust all other tenant nodes.
                    trustedNodes.addAll(candidates.nodeType(NodeType.tenant).asList());
                }
                break;

            case config:
                // Config servers trust:
                // - all nodes
                // - port 4443 from the world
                trustedNodes.addAll(candidates.asList());
                trustedPorts.add(4443);
                break;

            case proxy:
                // Proxy nodes trust:
                // - config servers
                // - all connections from the world on 4080 (insecure tb removed), and 4443
                trustedNodes.addAll(candidates.nodeType(NodeType.config).asList());
                trustedPorts.add(443);
                trustedPorts.add(4080);
                trustedPorts.add(4443);
                break;

            case controller:
                // Controllers:
                // - port 4443 (HTTPS + Athenz) from the world
                // - port 443 (HTTPS + Okta) from the world
                trustedPorts.add(4443);
                trustedPorts.add(443);
                break;

            default:
                throw new IllegalArgumentException(
                        String.format("Don't know how to create ACL for node [hostname=%s type=%s]",
                                      node.hostname(), node.type()));
        }

        return new NodeAcl(node, trustedNodes, trustedNetworks, trustedPorts);
    }

    /**
     * Creates a list of node ACLs which identify which nodes the given node should trust
     *
     * @param node Node for which to generate ACLs
     * @param children Return ACLs for the children of the given node (e.g. containers on a Docker host)
     * @return List of node ACLs
     */
    public List<NodeAcl> getNodeAcls(Node node, boolean children) {
        NodeList candidates = list();
        LoadBalancerList loadBalancers = loadBalancers();
        if (children) {
            return candidates.childrenOf(node).asList().stream()
                             .map(childNode -> getNodeAcl(childNode, candidates, loadBalancers))
                             .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        }
        return Collections.singletonList(getNodeAcl(node, candidates, loadBalancers));
    }

    public NodeFlavors getAvailableFlavors() {
        return flavors;
    }

    // ----------------- Node lifecycle -----------------------------------------------------------

    /** Creates a new node object, without adding it to the node repo. If no IP address is given, it will be resolved */
    public Node createNode(String openStackId, String hostname, IP.Config ipConfig, Optional<String> parentHostname,
                           Flavor flavor, NodeType type) {
        if (ipConfig.primary().isEmpty()) { // TODO: Remove this. Only test code hits this path
            ipConfig = ipConfig.with(nameResolver.getAllByNameOrThrow(hostname));
        }
        return Node.create(openStackId, ipConfig, hostname, parentHostname, Optional.empty(), flavor, type);
    }

    public Node createNode(String openStackId, String hostname, Optional<String> parentHostname, Flavor flavor,
                           NodeType type) {
        return createNode(openStackId, hostname, IP.Config.EMPTY, parentHostname, flavor, type);
    }

    /** Adds a list of newly created docker container nodes to the node repository as <i>reserved</i> nodes */
    public List<Node> addDockerNodes(LockedNodeList nodes) {
        for (Node node : nodes) {
            if (!node.flavor().getType().equals(Flavor.Type.DOCKER_CONTAINER)) {
                throw new IllegalArgumentException("Cannot add " + node.hostname() + ": This is not a docker node");
            }
            if (!node.allocation().isPresent()) {
                throw new IllegalArgumentException("Cannot add " + node.hostname() + ": Docker containers needs to be allocated");
            }
            Optional<Node> existing = getNode(node.hostname());
            if (existing.isPresent())
                throw new IllegalArgumentException("Cannot add " + node.hostname() + ": A node with this name already exists (" +
                                                   existing.get() + ", " + existing.get().history() + "). Node to be added: " +
                                                   node + ", " + node.history());
        }
        return db.addNodesInState(nodes.asList(), Node.State.reserved);
    }

    /** Adds a list of (newly created) nodes to the node repository as <i>provisioned</i> nodes */
    public List<Node> addNodes(List<Node> nodes) {
        try (Mutex lock = lockAllocation()) {
            for (int i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);
                var message = "Cannot add " + node.hostname() + ": A node with this name already exists";

                // Check for existing node
                if (getNode(node.hostname()).isPresent()) throw new IllegalArgumentException(message);

                // Check for duplicates in given list
                for (int j = 0; j < i; j++) {
                    var other = nodes.get(j);
                    if (node.equals(other)) throw new IllegalArgumentException(message);
                }
            }
            return db.addNodesInState(IP.Config.verify(nodes, list(lock)), Node.State.provisioned);
        }
    }

    /** Sets a list of nodes ready and returns the nodes in the ready state */
    public List<Node> setReady(List<Node> nodes, Agent agent, String reason) {
        try (Mutex lock = lockAllocation()) {
            List<Node> nodesWithResetFields = nodes.stream()
                    .map(node -> {
                        if (node.state() != Node.State.provisioned && node.state() != Node.State.dirty)
                            throw new IllegalArgumentException("Can not set " + node + " ready. It is not provisioned or dirty.");
                        return node.with(node.status().withWantToRetire(false).withWantToDeprovision(false));
                    })
                    .collect(Collectors.toList());

            return db.writeTo(Node.State.ready, nodesWithResetFields, agent, Optional.of(reason));
        }
    }

    public Node setReady(String hostname, Agent agent, String reason) {
        Node nodeToReady = getNode(hostname).orElseThrow(() ->
                new NoSuchNodeException("Could not move " + hostname + " to ready: Node not found"));

        if (nodeToReady.state() == Node.State.ready) return nodeToReady;
        return setReady(Collections.singletonList(nodeToReady), agent, reason).get(0);
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
            write(removableNodes, lock);
        }
    }

    public void deactivate(ApplicationId application, NestedTransaction transaction) {
        try (Mutex lock = lock(application)) {
            deactivate(db.getNodes(application, Node.State.reserved, Node.State.active), transaction);
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
        return performOn(NodeListFilter.from(nodes), (node, lock) -> setDirty(node, agent, reason));
    }

    /**
     * Set a node dirty, allowed if it is in the provisioned, inactive, failed or parked state.
     * Use this to clean newly provisioned nodes or to recycle failed nodes which have been repaired or put on hold.
     *
     * @throws IllegalArgumentException if the node has hardware failure
     */
    public Node setDirty(Node node, Agent agent, String reason) {
        return db.writeTo(Node.State.dirty, node, agent, Optional.of(reason));
    }

    public List<Node> dirtyRecursively(String hostname, Agent agent, String reason) {
        Node nodeToDirty = getNode(hostname).orElseThrow(() ->
                new IllegalArgumentException("Could not deallocate " + hostname + ": Node not found"));

        List<Node> nodesToDirty =
                (nodeToDirty.type().isDockerHost() ?
                        Stream.concat(list().childrenOf(hostname).asList().stream(), Stream.of(nodeToDirty)) :
                        Stream.of(nodeToDirty))
                .filter(node -> node.state() != Node.State.dirty)
                .collect(Collectors.toList());

        List<String> hostnamesNotAllowedToDirty = nodesToDirty.stream()
                .filter(node -> node.state() != Node.State.provisioned)
                .filter(node -> node.state() != Node.State.failed)
                .filter(node -> node.state() != Node.State.parked)
                .map(Node::hostname)
                .collect(Collectors.toList());
        if (!hostnamesNotAllowedToDirty.isEmpty()) {
            throw new IllegalArgumentException("Could not deallocate " + hostname + ": " +
                    String.join(", ", hostnamesNotAllowedToDirty) + " must be in either provisioned, failed or parked state");
        }

        return nodesToDirty.stream()
                .map(node -> setDirty(node, agent, reason))
                .collect(Collectors.toList());
    }

    /**
     * Fails this node and returns it in its new state.
     *
     * @return the node in its new state
     * @throws NoSuchNodeException if the node is not found
     */
    public Node fail(String hostname, Agent agent, String reason) {
        return move(hostname, true, Node.State.failed, agent, Optional.of(reason));
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
    public Node park(String hostname, boolean keepAllocation, Agent agent, String reason) {
        return move(hostname, keepAllocation, Node.State.parked, agent, Optional.of(reason));
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
    public Node reactivate(String hostname, Agent agent, String reason) {
        return move(hostname, true, Node.State.active, agent, Optional.of(reason));
    }

    private List<Node> moveRecursively(String hostname, Node.State toState, Agent agent, Optional<String> reason) {
        List<Node> moved = list().childrenOf(hostname).asList().stream()
                                         .map(child -> move(child, toState, agent, reason))
                                         .collect(Collectors.toList());

        moved.add(move(hostname, true, toState, agent, reason));
        return moved;
    }

    private Node move(String hostname, boolean keepAllocation, Node.State toState, Agent agent, Optional<String> reason) {
        Node node = getNode(hostname).orElseThrow(() ->
                new NoSuchNodeException("Could not move " + hostname + " to " + toState + ": Node not found"));

        if (!keepAllocation && node.allocation().isPresent()) {
            node = node.withoutAllocation();
        }

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
     * This method is used by the REST API to handle readying nodes for new allocations. For tenant docker
     * containers this will remove the node from node repository, otherwise the node will be moved to state ready.
     */
    public Node markNodeAvailableForNewAllocation(String hostname, Agent agent, String reason) {
        Node node = getNode(hostname).orElseThrow(() -> new NotFoundException("No node with hostname '" + hostname + "'"));
        if (node.flavor().getType() == Flavor.Type.DOCKER_CONTAINER && node.type() == NodeType.tenant) {
            if (node.state() != Node.State.dirty) {
                throw new IllegalArgumentException(
                        "Cannot make " + hostname + " available for new allocation, must be in state dirty, but was in " + node.state());
            }
            return removeRecursively(node, true).get(0);
        }

        if (node.state() == Node.State.ready) return node;

        Node parentHost = node.parentHostname().flatMap(this::getNode).orElse(node);
        List<String> failureReasons = NodeFailer.reasonsToFailParentHost(parentHost);
        if (!failureReasons.isEmpty()) {
            throw new IllegalArgumentException("Node " + hostname + " cannot be readied because it has hard failures: " + failureReasons);
        }

        return setReady(Collections.singletonList(node), agent, reason).get(0);
    }

    /**
     * Removes all the nodes that are children of hostname before finally removing the hostname itself.
     *
     * @return List of all the nodes that have been removed
     */
    public List<Node> removeRecursively(String hostname) {
        Node node = getNode(hostname).orElseThrow(() -> new NotFoundException("No node with hostname '" + hostname + "'"));
        return removeRecursively(node, false);
    }

    public List<Node> removeRecursively(Node node, boolean force) {
        try (Mutex lock = lockAllocation()) {
            List<Node> removed = new ArrayList<>();

             if (node.type().isDockerHost()) {
                 list().childrenOf(node).asList().stream()
                       .filter(child -> force || canRemove(child, true))
                       .forEach(removed::add);
             }

            if (force || canRemove(node, false)) removed.add(node);
            db.removeNodes(removed);

            return removed;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to delete " + node.hostname(), e);
        }
    }

    /**
     * Returns whether given node can be removed. Removal is allowed if:
     *  Tenant node: node is unallocated
     *  Non-Docker-container node: iff in state provisioned|failed|parked
     *  Docker-container-node:
     *    If only removing the container node: node in state ready
     *    If also removing the parent node: child is in state provisioned|failed|parked|dirty|ready
     */
    private boolean canRemove(Node node, boolean deletingAsChild) {
        if (node.type() == NodeType.tenant && node.allocation().isPresent()) {
            throw new IllegalArgumentException("Node is currently allocated and cannot be removed: " +
                                               node.allocation().get());
        }
        if (node.flavor().getType() == Flavor.Type.DOCKER_CONTAINER && !deletingAsChild) {
            if (node.state() != Node.State.ready) {
                throw new IllegalArgumentException(
                        String.format("Docker container %s can only be removed when in ready state", node.hostname()));
            }

        } else if (node.flavor().getType() == Flavor.Type.DOCKER_CONTAINER) {
            Set<Node.State> legalStates = EnumSet.of(Node.State.provisioned, Node.State.failed, Node.State.parked,
                                                     Node.State.dirty, Node.State.ready);

            if (! legalStates.contains(node.state())) {
                throw new IllegalArgumentException(String.format("Child node %s can only be removed from following states: %s",
                        node.hostname(), legalStates.stream().map(Node.State::name).collect(Collectors.joining(", "))));
            }
        } else {
            Set<Node.State> legalStates = EnumSet.of(Node.State.provisioned, Node.State.failed, Node.State.parked);

            if (! legalStates.contains(node.state())) {
                throw new IllegalArgumentException(String.format("Node %s can only be removed from following states: %s",
                        node.hostname(), legalStates.stream().map(Node.State::name).collect(Collectors.joining(", "))));
            }
        }

        return true;
    }

    /**
     * Increases the restart generation of the active nodes matching the filter.
     *
     * @return the nodes in their new state.
     */
    public List<Node> restart(NodeFilter filter) {
        return performOn(StateFilter.from(Node.State.active, filter), (node, lock) -> write(node.withRestart(node.allocation().get().restartGeneration().withIncreasedWanted()), lock));
    }

    /**
     * Increases the reboot generation of the nodes matching the filter.
     * @return the nodes in their new state.
     */
    public List<Node> reboot(NodeFilter filter) {
        return performOn(filter, (node, lock) -> write(node.withReboot(node.status().reboot().withIncreasedWanted()), lock));
    }

    /**
     * Set target OS version of all nodes matching given filter.
     *
     * @return the nodes in their new state.
     */
    public List<Node> upgradeOs(NodeFilter filter, Optional<Version> version) {
        return performOn(filter, (node, lock) -> {
            var newStatus = node.status().withOsVersion(node.status().osVersion().withWanted(version));
            return write(node.with(newStatus), lock);
        });
    }

    /**
     * Writes this node after it has changed some internal state but NOT changed its state field.
     * This does NOT lock the node repository implicitly, but callers are expected to already hold the lock.
     *
     * @param lock Already acquired lock
     * @return the written node for convenience
     */
    public Node write(Node node, Mutex lock) { return write(List.of(node), lock).get(0); }

    /**
     * Writes these nodes after they have changed some internal state but NOT changed their state field.
     * This does NOT lock the node repository implicitly, but callers are expected to already hold the lock.
     *
     * @param lock Already acquired lock
     * @return the written nodes for convenience
     */
    public List<Node> write(List<Node> nodes, @SuppressWarnings("unused") Mutex lock) {
        return db.writeTo(nodes, Agent.system, Optional.empty());
    }

    /**
     * Performs an operation requiring locking on all nodes matching some filter.
     *
     * @param filter the filter determining the set of nodes where the operation will be performed
     * @param action the action to perform
     * @return the set of nodes on which the action was performed, as they became as a result of the operation
     */
    private List<Node> performOn(NodeFilter filter, BiFunction<Node, Mutex, Node> action) {
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
        try (Mutex lock = lockAllocation()) {
            for (Node node : unallocatedNodes)
                resultingNodes.add(action.apply(node, lock));
        }
        for (Map.Entry<ApplicationId, List<Node>> applicationNodes : allocatedNodes.entrySet()) {
            try (Mutex lock = lock(applicationNodes.getKey())) {
                for (Node node : applicationNodes.getValue())
                    resultingNodes.add(action.apply(node, lock));
            }
        }
        return resultingNodes;
    }

    /** Returns the time keeper of this system */
    public Clock clock() { return clock; }

    /** Returns the zone of this system */
    public Zone zone() { return zone; }

    /** Create a lock which provides exclusive rights to making changes to the given application */
    public Mutex lock(ApplicationId application) { return db.lock(application); }

    /** Create a lock with a timeout which provides exclusive rights to making changes to the given application */
    public Mutex lock(ApplicationId application, Duration timeout) { return db.lock(application, timeout); }

    /** Create a lock which provides exclusive rights to allocating nodes */
    public Mutex lockAllocation() { return db.lockInactive(); }

    /** Acquires the appropriate lock for this node */
    public Mutex lock(Node node) {
        return node.allocation().isPresent() ? lock(node.allocation().get().owner()) : lockAllocation();
    }

}
