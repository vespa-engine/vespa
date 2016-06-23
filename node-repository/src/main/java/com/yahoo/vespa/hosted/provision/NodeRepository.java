// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.google.inject.Inject;
import com.yahoo.collections.ListMap;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.hosted.provision.node.Configuration;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;
import com.yahoo.vespa.hosted.provision.node.filter.StateFilter;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * by the application package and {@link com.yahoo.vespa.hosted.provision.maintenance.ApplicationMaintainer}
 * for changes initiated by the node repository.
 * Refer to {@link com.yahoo.vespa.hosted.provision.maintenance.NodeRepositoryMaintenance} for timing details
 * of the node state transitions.
 *
 * @author bratseth
 */
// Node state transitions:
// 1) (new) - > provisioned -> ready -> reserved -> active -> inactive -> dirty -> ready
// 2) inactive -> reserved
// 3) reserved -> dirty
// 3) * -> failed | parked -> dirty | active | (removed)
// Nodes have an application assigned when in states reserved, active and inactive.
// Nodes might have an application assigned in dirty.
public class NodeRepository extends AbstractComponent {

    private final CuratorDatabaseClient zkClient;

    /**
     * Creates a node repository form a zookeeper provider.
     * This will use the system time to make time-sensitive decisions
     */
    @Inject
    public NodeRepository(NodeFlavors flavors, Curator curator) {
        this(flavors, curator, Clock.systemUTC());
    }

    /**
     * Creates a node repository form a zookeeper provider and a clock instance
     * which will be used for time-sensitive decisions.
     */
    public NodeRepository(NodeFlavors flavors, Curator curator, Clock clock) {
        this.zkClient = new CuratorDatabaseClient(flavors, curator, clock);

        // read and write all nodes to make sure they are stored in the latest version of the serialized format
        for (Node.State state : Node.State.values())
            zkClient.writeTo(state, zkClient.getNodes(state));
    }

    // ---------------- Query API ----------------------------------------------------------------

    /** Finds and returns the node with the given hostname */
    public Optional<Node> getNode(String hostname) {
        for (Node.State state : Node.State.values()) {
            Optional<Node> node = getNode(state, hostname);
            if (node.isPresent())
                return node;
        }
        return Optional.empty();
    }

    /** Finds and returns the node with the given state and hostname, or empty if not found */
    public Optional<Node> getNode(Node.State state, String hostname) {
        return zkClient.getNode(state, hostname);
    }

    public List<Node> getNodes(Node.Type type, Node.State ... inState) {
        return zkClient.getNodes(inState).stream().filter(node -> node.type().equals(type)).collect(Collectors.toList());
    }
    public List<Node> getNodes(ApplicationId id, Node.State ... inState) { return zkClient.getNodes(id, inState); }
    public List<Node> getInactive() { return zkClient.getNodes(Node.State.inactive); }
    public List<Node> getFailed() { return zkClient.getNodes(Node.State.failed); }

    public int getNodeCount(String tenantId, Node.State ... inState) {
        return zkClient.getNodes(inState).stream()
            .filter(node -> node.allocation().get().owner().tenant().value().equals(tenantId))
            .collect(Collectors.counting()).intValue();
    }

    // ----------------- Node lifecycle -----------------------------------------------------------

    /** Creates a new node object, without adding it to the node repo */
    public Node createNode(String openStackId, String hostname, Optional<String> parentHostname,
                           Configuration configuration, Node.Type type) {
        return Node.create(openStackId, hostname, parentHostname, configuration, type);
    }

    /** Adds a list of (newly created) nodes to the node repository as <i>provisioned</i> nodes */
    public List<Node> addNodes(List<Node> nodes) {
        for (Node node : nodes) {
            Optional<Node> existing = getNode(node.hostname());
            if (existing.isPresent())
                throw new IllegalArgumentException("Cannot add " + node.hostname() + ": A node with this name already exists");
        }
        try (Mutex lock = lockUnallocated()) {
            return zkClient.addNodes(nodes);
        }
    }

    /** Sets a list of nodes ready and returns the nodes in the ready state */
    public List<Node> setReady(List<Node> nodes) {
        for (Node node : nodes)
            if (node.state() != Node.State.provisioned && node.state() != Node.State.dirty)
                throw new IllegalArgumentException("Can not set " + node + " ready. It is not provisioned or dirty.");
        try (Mutex lock = lockUnallocated()) {
            return zkClient.writeTo(Node.State.ready, nodes);
        }
    }

    /** Reserve nodes. This method does <b>not</b> lock the node repository */
    public List<Node> reserve(List<Node> nodes) { return zkClient.writeTo(Node.State.reserved, nodes); }

    /** Activate nodes. This method does <b>not</b> lock the node repository */
    public List<Node> activate(List<Node> nodes, NestedTransaction transaction) {
        return zkClient.writeTo(Node.State.active, nodes, transaction);
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
                nodes.stream().map(node -> node.setAllocation(node.allocation().get().makeRemovable()))
                              .collect(Collectors.toList());
            write(removableNodes);
        }
    }

    public void deactivate(ApplicationId application) {
        try (Mutex lock = lock(application)) {
            zkClient.writeTo(Node.State.inactive, zkClient.getNodes(application, Node.State.reserved, Node.State.active));
        }
    }

    /**
     * Deactivates these nodes in a transaction and returns
     * the nodes in the new state which will hold if the transaction commits.
     * This method does <b>not</b> lock
     */
    public List<Node> deactivate(List<Node> nodes, NestedTransaction transaction) {
        return zkClient.writeTo(Node.State.inactive, nodes, transaction);
    }

    /** Deallocates these nodes, causing them to move to the dirty state */
    public List<Node> deallocate(List<Node> nodes) {
        return performOn(NodeListFilter.from(nodes), node -> zkClient.writeTo(Node.State.dirty, node));
    }

    /** 
     * Deallocate a node which is in the failed or parked state. 
     * Use this to recycle failed nodes which have been repaired or put on hold. 
     */
    public Node deallocate(String hostname) {
        Optional<Node> nodeToDeallocate = getNode(Node.State.failed, hostname);
        if ( ! nodeToDeallocate.isPresent())
            nodeToDeallocate = getNode(Node.State.parked, hostname);
        if ( ! nodeToDeallocate.isPresent())
            throw new IllegalArgumentException("Could not deallocate " + hostname + ": No such node in the failed or parked state");
        return deallocate(Collections.singletonList(nodeToDeallocate.get())).get(0);
    }

    /**
     * Fails this node and returns it in its new state.
     *
     * @return the node in its new state
     * @throws IllegalArgumentException if the node is not found
     */
    public Node fail(String hostname) {
        return move(hostname, Node.State.failed);
    }

    /**
     * Parks this node and returns it in its new state.
     *
     * @return the node in its new state
     * @throws IllegalArgumentException if the node is not found
     */
    public Node park(String hostname) {
        return move(hostname, Node.State.parked);
    }

    /**
     * Moves a previously failed or parked node back to the active state.
     *
     * @return the node in its new state
     * @throws IllegalArgumentException if the node is not found
     */
    public Node reactivate(String hostname) {
        return move(hostname, Node.State.active);
    }

    public Node move(String hostname, Node.State toState) {
        Optional<Node> node = getNode(hostname);
        if ( ! node.isPresent())
            throw new IllegalArgumentException("Could not move " + hostname + " to " + toState + ": Node not found");
        try (Mutex lock = lock(node.get())) {
            return zkClient.writeTo(toState, node.get());
        }
    }

    /**
     * Removes a node. A node must be in the failed or parked state before it can be removed.
     *
     * @return true if the node was removed, false if it was not found
     */
    public boolean remove(String hostname) {
        Optional<Node> nodeToRemove = getNode(Node.State.failed, hostname);
        if ( ! nodeToRemove.isPresent())
            nodeToRemove = getNode(Node.State.parked, hostname);
        if ( ! nodeToRemove.isPresent()) 
            return false;
        try (Mutex lock = lock(nodeToRemove.get())) {
            return zkClient.removeNode(nodeToRemove.get().state(), hostname);
        }
    }

    /**
     * Increases the restart generation of the active nodes matching the filter.
     * Returns the nodes in their new state.
     */
    public List<Node> restart(NodeFilter filter) {
        return performOn(StateFilter.from(Node.State.active, filter), node -> write(node.setRestart(node.allocation().get().restartGeneration().increaseWanted())));
    }

    /**
     * Increases the reboot generation of the nodes matching the filter.
     * Returns the nodes in their new state.
     */
    public List<Node> reboot(NodeFilter filter) {
        return performOn(filter, node -> write(node.setReboot(node.status().reboot().increaseWanted())));
    }

    /**
     * Writes this node after it has changed some internal state but NOT changed its state field.
     * This does NOT lock the node repository.
     *
     * @return the written node for convenience
     */
    public Node write(Node node) { return zkClient.writeTo(node.state(), node); }

    /**
     * Writes these nodes after they have changed some internal state but NOT changed their state field.
     * This does NOT lock the node repository.
     *
     * @return the written nodes for convenience
     */
    public List<Node> write(List<Node> nodes) {
        if (nodes.isEmpty()) return Collections.emptyList();

        // decide current state and make sure all nodes have it (alternatively we could create a transaction here)
        Node.State state = nodes.get(0).state();
        for (Node node : nodes) {
            if ( node.state() != state)
                throw new IllegalArgumentException("Multiple states: " + node.state() + " and " + state);
        }
        return zkClient.writeTo(state, nodes);
    }

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
        for (Node node : zkClient.getNodes()) {
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

    /** Create a lock which provides exclusive rights to making changes to the given application */
    public Mutex lock(ApplicationId application) { return zkClient.lock(application); }

    /** Create a lock which provides exclusive rights to changing the set of ready nodes */
    public Mutex lockUnallocated() { return zkClient.lockInactive(); }

    /** Acquires the appropriate lock for this node */
    private Mutex lock(Node node) {
        return node.allocation().isPresent() ? lock(node.allocation().get().owner()) : lockUnallocated();
    }

}
