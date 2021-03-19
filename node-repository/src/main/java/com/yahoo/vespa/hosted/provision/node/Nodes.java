// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.collections.ListMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.NoSuchNodeException;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.maintenance.NodeFailer;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;
import com.yahoo.vespa.hosted.provision.node.filter.StateFilter;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;
import com.yahoo.vespa.hosted.provision.restapi.NotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The nodes in the node repo and their state transitions
 *
 * @author bratseth
 */
// Node state transitions:
// 1) (new) | deprovisioned - > provisioned -> (dirty ->) ready -> reserved -> active -> inactive -> dirty -> ready
// 2) inactive -> reserved | parked
// 3) reserved -> dirty
// 4) * -> failed | parked -> (breakfixed) -> dirty | active | deprovisioned
// 5) deprovisioned -> (forgotten)
// Nodes have an application assigned when in states reserved, active and inactive.
// Nodes might have an application assigned in dirty.
public class Nodes {

    private final Zone zone;
    private final Clock clock;
    private final CuratorDatabaseClient db;

    public Nodes(CuratorDatabaseClient db, Zone zone, Clock clock) {
        this.zone = zone;
        this.clock = clock;
        this.db = db;
    }

    // ---------------- Query API ----------------------------------------------------------------

    /**
     * Finds and returns the node with the hostname in any of the given states, or empty if not found
     *
     * @param hostname the full host name of the node
     * @param inState the states the node may be in. If no states are given, it will be returned from any state
     * @return the node, or empty if it was not found in any of the given states
     */
    public Optional<Node> node(String hostname, Node.State... inState) {
        return db.readNode(hostname, inState);
    }

    /**
     * Returns a list of nodes in this repository in any of the given states
     *
     * @param inState the states to return nodes from. If no states are given, all nodes of the given type are returned
     */
    public NodeList list(Node.State... inState) {
        return NodeList.copyOf(db.readNodes(inState));
    }

    /** Returns a locked list of all nodes in this repository */
    public LockedNodeList list(Mutex lock) {
        return new LockedNodeList(list().asList(), lock);
    }

    /**
     * Returns whether the zone managed by this node repository seems to be working.
     * If too many nodes are not responding, there is probably some zone-wide issue
     * and we should probably refrain from making changes to it.
     */
    public boolean isWorking() {
        NodeList activeNodes = list(Node.State.active);
        if (activeNodes.size() <= 5) return true; // Not enough data to decide
        NodeList downNodes = activeNodes.down();
        return ! ( (double)downNodes.size() / (double)activeNodes.size() > 0.2 );
    }

    // ----------------- Node lifecycle -----------------------------------------------------------

    /** Adds a list of newly created reserved nodes to the node repository */
    public List<Node> addReservedNodes(LockedNodeList nodes) {
        for (Node node : nodes) {
            if ( ! node.flavor().getType().equals(Flavor.Type.DOCKER_CONTAINER))
                illegal("Cannot add " + node + ": This is not a child node");
            if (node.allocation().isEmpty())
                illegal("Cannot add " + node + ": Child nodes need to be allocated");
            Optional<Node> existing = node(node.hostname());
            if (existing.isPresent())
                illegal("Cannot add " + node + ": A node with this name already exists (" +
                        existing.get() + ", " + existing.get().history() + "). Node to be added: " +
                        node + ", " + node.history());
        }
        return db.addNodesInState(nodes.asList(), Node.State.reserved, Agent.system);
    }

    /**
     * Adds a list of (newly created) nodes to the node repository as provisioned nodes.
     * If any of the nodes already exists in the deprovisioned state, the new node will be merged
     * with the history of that node.
     */
    public List<Node> addNodes(List<Node> nodes, Agent agent) {
        try (Mutex lock = lockUnallocated()) {
            List<Node> nodesToAdd =  new ArrayList<>();
            List<Node> nodesToRemove = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);

                // Check for duplicates
                for (int j = 0; j < i; j++) {
                    if (node.equals(nodes.get(j)))
                        illegal("Cannot add nodes: " + node + " is duplicated in the argument list");
                }

                Optional<Node> existing = node(node.hostname());
                if (existing.isPresent()) {
                    if (existing.get().state() != Node.State.deprovisioned)
                        illegal("Cannot add " + node + ": A node with this name already exists");
                    node = node.with(existing.get().history());
                    node = node.with(existing.get().reports());
                    node = node.with(node.status().withFailCount(existing.get().status().failCount()));
                    if (existing.get().status().firmwareVerifiedAt().isPresent())
                        node = node.with(node.status().withFirmwareVerifiedAt(existing.get().status().firmwareVerifiedAt().get()));
                    nodesToRemove.add(existing.get());
                }

                nodesToAdd.add(node);
            }
            List<Node> resultingNodes = db.addNodesInState(IP.Config.verify(nodesToAdd, list(lock)), Node.State.provisioned, agent);
            db.removeNodes(nodesToRemove);
            return resultingNodes;
        }
    }

    /** Sets a list of nodes ready and returns the nodes in the ready state */
    public List<Node> setReady(List<Node> nodes, Agent agent, String reason) {
        try (Mutex lock = lockUnallocated()) {
            List<Node> nodesWithResetFields = nodes.stream()
                                                   .map(node -> {
                                                       if (node.state() != Node.State.provisioned && node.state() != Node.State.dirty)
                                                           illegal("Can not set " + node + " ready. It is not provisioned or dirty.");
                                                       return node.withWantToRetire(false, false, Agent.system, clock.instant());
                                                   })
                                                   .collect(Collectors.toList());

            return db.writeTo(Node.State.ready, nodesWithResetFields, agent, Optional.of(reason));
        }
    }

    public Node setReady(String hostname, Agent agent, String reason) {
        Node nodeToReady = node(hostname).orElseThrow(() ->
                                                                 new NoSuchNodeException("Could not move " + hostname + " to ready: Node not found"));

        if (nodeToReady.state() == Node.State.ready) return nodeToReady;
        return setReady(List.of(nodeToReady), agent, reason).get(0);
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
            List<Node> removableNodes = nodes.stream()
                                             .map(node -> node.with(node.allocation().get().removable(true)))
                                             .collect(Collectors.toList());
            write(removableNodes, lock);
        }
    }

    /**
     * Deactivates these nodes in a transaction and returns the nodes in the new state which will hold if the
     * transaction commits.
     */
    public List<Node> deactivate(List<Node> nodes, ApplicationTransaction transaction) {
        var stateless = NodeList.copyOf(nodes).stateless();
        var stateful  = NodeList.copyOf(nodes).stateful();
        List<Node> written = new ArrayList<>();
        written.addAll(deallocate(stateless.asList(), Agent.application, "Deactivated by application", transaction.nested()));
        written.addAll(db.writeTo(Node.State.inactive, stateful.asList(), Agent.application, Optional.empty(), transaction.nested()));
        return written;

    }

    /** Move nodes to the dirty state */
    public List<Node> deallocate(List<Node> nodes, Agent agent, String reason) {
        return performOn(NodeListFilter.from(nodes), (node, lock) -> deallocate(node, agent, reason));
    }

    public List<Node> deallocateRecursively(String hostname, Agent agent, String reason) {
        Node nodeToDirty = node(hostname).orElseThrow(() ->
                                                                 new IllegalArgumentException("Could not deallocate " + hostname + ": Node not found"));

        List<Node> nodesToDirty =
                (nodeToDirty.type().isHost() ?
                 Stream.concat(list().childrenOf(hostname).asList().stream(), Stream.of(nodeToDirty)) :
                 Stream.of(nodeToDirty))
                        .filter(node -> node.state() != Node.State.dirty)
                        .collect(Collectors.toList());

        List<String> hostnamesNotAllowedToDirty = nodesToDirty.stream()
                                                              .filter(node -> node.state() != Node.State.provisioned)
                                                              .filter(node -> node.state() != Node.State.failed)
                                                              .filter(node -> node.state() != Node.State.parked)
                                                              .filter(node -> node.state() != Node.State.breakfixed)
                                                              .map(Node::hostname)
                                                              .collect(Collectors.toList());
        if ( ! hostnamesNotAllowedToDirty.isEmpty())
            illegal("Could not deallocate " + nodeToDirty + ": " +
                    hostnamesNotAllowedToDirty + " are not in states [provisioned, failed, parked, breakfixed]");

        return nodesToDirty.stream().map(node -> deallocate(node, agent, reason)).collect(Collectors.toList());
    }

    /**
     * Set a node dirty  or parked, allowed if it is in the provisioned, inactive, failed or parked state.
     * Use this to clean newly provisioned nodes or to recycle failed nodes which have been repaired or put on hold.
     */
    public Node deallocate(Node node, Agent agent, String reason) {
        NestedTransaction transaction = new NestedTransaction();
        Node deallocated = deallocate(node, agent, reason, transaction);
        transaction.commit();
        return deallocated;
    }

    public List<Node> deallocate(List<Node> nodes, Agent agent, String reason, NestedTransaction transaction) {
        return nodes.stream().map(node -> deallocate(node, agent, reason, transaction)).collect(Collectors.toList());
    }

    public Node deallocate(Node node, Agent agent, String reason, NestedTransaction transaction) {
        if (node.state() != Node.State.parked && agent != Agent.operator
            && (node.status().wantToDeprovision() || retiredByOperator(node)))
            return park(node.hostname(), false, agent, reason, transaction);
        else
            return db.writeTo(Node.State.dirty, List.of(node), agent, Optional.of(reason), transaction).get(0);
    }

    private static boolean retiredByOperator(Node node) {
        return node.status().wantToRetire() && node.history().event(History.Event.Type.wantToRetire)
                                                   .map(History.Event::agent)
                                                   .map(agent -> agent == Agent.operator)
                                                   .orElse(false);
    }

    /**
     * Fails this node and returns it in its new state.
     *
     * @return the node in its new state
     * @throws NoSuchNodeException if the node is not found
     */
    public Node fail(String hostname, Agent agent, String reason) {
        return fail(hostname, true, agent, reason);
    }

    public Node fail(String hostname, boolean keepAllocation, Agent agent, String reason) {
        return move(hostname, keepAllocation, Node.State.failed, agent, Optional.of(reason));
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
        NestedTransaction transaction = new NestedTransaction();
        Node parked = park(hostname, keepAllocation, agent, reason, transaction);
        transaction.commit();
        return parked;
    }

    public Node park(String hostname, boolean keepAllocation, Agent agent, String reason, NestedTransaction transaction) {
        return move(hostname, keepAllocation, Node.State.parked, agent, Optional.of(reason), transaction);
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

    /**
     * Moves a host to breakfixed state, removing any children.
     */
    public List<Node> breakfixRecursively(String hostname, Agent agent, String reason) {
        Node node = node(hostname).orElseThrow(() ->
                                                          new NoSuchNodeException("Could not breakfix " + hostname + ": Node not found"));

        try (Mutex lock = lockUnallocated()) {
            requireBreakfixable(node);
            List<Node> removed = removeChildren(node, false);
            removed.add(move(node, Node.State.breakfixed, agent, Optional.of(reason)));
            return removed;
        }
    }

    private List<Node> moveRecursively(String hostname, Node.State toState, Agent agent, Optional<String> reason) {
        List<Node> moved = list().childrenOf(hostname).asList().stream()
                                 .map(child -> move(child, toState, agent, reason))
                                 .collect(Collectors.toList());

        moved.add(move(hostname, true, toState, agent, reason));
        return moved;
    }

    private Node move(String hostname, boolean keepAllocation, Node.State toState, Agent agent, Optional<String> reason) {
        NestedTransaction transaction = new NestedTransaction();
        Node moved = move(hostname, keepAllocation, toState, agent, reason, transaction);
        transaction.commit();
        return moved;
    }

    private Node move(String hostname, boolean keepAllocation, Node.State toState, Agent agent, Optional<String> reason,
                      NestedTransaction transaction) {
        Node node = node(hostname).orElseThrow(() ->
                                                          new NoSuchNodeException("Could not move " + hostname + " to " + toState + ": Node not found"));

        if (!keepAllocation && node.allocation().isPresent()) {
            node = node.withoutAllocation();
        }

        return move(node, toState, agent, reason, transaction);
    }

    private Node move(Node node, Node.State toState, Agent agent, Optional<String> reason) {
        NestedTransaction transaction = new NestedTransaction();
        Node moved = move(node, toState, agent, reason, transaction);
        transaction.commit();
        return moved;
    }

    private Node move(Node node, Node.State toState, Agent agent, Optional<String> reason, NestedTransaction transaction) {
        if (toState == Node.State.active && node.allocation().isEmpty())
            illegal("Could not set " + node + " active. It has no allocation.");

        // TODO: Work out a safe lock acquisition strategy for moves, e.g. migrate to lockNode.
        try (Mutex lock = lock(node)) {
            if (toState == Node.State.active) {
                for (Node currentActive : list(Node.State.active).owner(node.allocation().get().owner())) {
                    if (node.allocation().get().membership().cluster().equals(currentActive.allocation().get().membership().cluster())
                        && node.allocation().get().membership().index() == currentActive.allocation().get().membership().index())
                        illegal("Could not set " + node + " active: Same cluster and index as " + currentActive);
                }
            }
            return db.writeTo(toState, List.of(node), agent, reason, transaction).get(0);
        }
    }

    /*
     * This method is used by the REST API to handle readying nodes for new allocations. For Linux
     * containers this will remove the node from node repository, otherwise the node will be moved to state ready.
     */
    public Node markNodeAvailableForNewAllocation(String hostname, Agent agent, String reason) {
        Node node = node(hostname).orElseThrow(() -> new NotFoundException("No node with hostname '" + hostname + "'"));
        if (node.flavor().getType() == Flavor.Type.DOCKER_CONTAINER && node.type() == NodeType.tenant) {
            if (node.state() != Node.State.dirty)
                illegal("Cannot make " + node  + " available for new allocation as it is not in state [dirty]");
            return removeRecursively(node, true).get(0);
        }

        if (node.state() == Node.State.ready) return node;

        Node parentHost = node.parentHostname().flatMap(this::node).orElse(node);
        List<String> failureReasons = NodeFailer.reasonsToFailParentHost(parentHost);
        if ( ! failureReasons.isEmpty())
            illegal(node + " cannot be readied because it has hard failures: " + failureReasons);

        return setReady(List.of(node), agent, reason).get(0);
    }

    /**
     * Removes all the nodes that are children of hostname before finally removing the hostname itself.
     *
     * @return a List of all the nodes that have been removed or (for hosts) deprovisioned
     */
    public List<Node> removeRecursively(String hostname) {
        Node node = node(hostname).orElseThrow(() -> new NotFoundException("No node with hostname '" + hostname + "'"));
        return removeRecursively(node, false);
    }

    public List<Node> removeRecursively(Node node, boolean force) {
        try (Mutex lock = lockUnallocated()) {
            requireRemovable(node, false, force);

            if (node.type().isHost()) {
                List<Node> removed = removeChildren(node, force);
                if (zone.getCloud().dynamicProvisioning() || node.type() != NodeType.host)
                    db.removeNodes(List.of(node));
                else {
                    node = node.with(IP.Config.EMPTY);
                    move(node, Node.State.deprovisioned, Agent.system, Optional.empty());
                }
                removed.add(node);
                return removed;
            }
            else {
                List<Node> removed = List.of(node);
                db.removeNodes(removed);
                return removed;
            }
        }
    }

    /** Forgets a deprovisioned node. This removes all traces of the node in the node repository. */
    public void forget(Node node) {
        if (node.state() != Node.State.deprovisioned)
            throw new IllegalArgumentException(node + " must be deprovisioned before it can be forgotten");
        db.removeNodes(List.of(node));
    }

    private List<Node> removeChildren(Node node, boolean force) {
        List<Node> children = list().childrenOf(node).asList();
        children.forEach(child -> requireRemovable(child, true, force));
        db.removeNodes(children);
        return new ArrayList<>(children);
    }

    /**
     * Throws if the given node cannot be removed. Removal is allowed if:
     *  - Tenant node: node is unallocated
     *  - Host node: iff in state provisioned|failed|parked
     *  - Child node:
     *      If only removing the container node: node in state ready
     *      If also removing the parent node: child is in state provisioned|failed|parked|dirty|ready
     */
    private void requireRemovable(Node node, boolean removingAsChild, boolean force) {
        if (force) return;

        if (node.type() == NodeType.tenant && node.allocation().isPresent())
            illegal(node + " is currently allocated and cannot be removed");

        if (!node.type().isHost() && !removingAsChild) {
            if (node.state() != Node.State.ready)
                illegal(node + " can not be removed as it is not in the state " + Node.State.ready);
        }
        else if (!node.type().isHost()) { // removing a child node
            Set<Node.State> legalStates = EnumSet.of(Node.State.provisioned, Node.State.failed, Node.State.parked, Node.State.dirty, Node.State.ready);
            if ( ! legalStates.contains(node.state()))
                illegal(node + " can not be removed as it is not in the states " + legalStates);
        }
        else { // a host
            Set<Node.State> legalStates = EnumSet.of(Node.State.provisioned, Node.State.failed, Node.State.parked);
            if (! legalStates.contains(node.state()))
                illegal(node + " can not be removed as it is not in the states " + legalStates);
        }
    }

    /**
     * Throws if given node cannot be breakfixed.
     * Breakfix is allowed if the following is true:
     *  - Node is tenant host
     *  - Node is in zone without dynamic provisioning
     *  - Node is in parked or failed state
     */
    private void requireBreakfixable(Node node) {
        if (zone.getCloud().dynamicProvisioning()) {
            illegal("Can not breakfix in zone: " + zone);
        }

        if (node.type() != NodeType.host) {
            illegal(node + " can not be breakfixed as it is not a tenant host");
        }

        Set<Node.State> legalStates = EnumSet.of(Node.State.failed, Node.State.parked);
        if (! legalStates.contains(node.state())) {
            illegal(node + " can not be removed as it is not in the states " + legalStates);
        }
    }

    /**
     * Increases the restart generation of the active nodes matching the filter.
     *
     * @return the nodes in their new state
     */
    public List<Node> restart(NodeFilter filter) {
        return performOn(StateFilter.from(Node.State.active, filter),
                         (node, lock) -> write(node.withRestart(node.allocation().get().restartGeneration().withIncreasedWanted()),
                                               lock));
    }

    /**
     * Increases the reboot generation of the nodes matching the filter.
     *
     * @return the nodes in their new state
     */
    public List<Node> reboot(NodeFilter filter) {
        return performOn(filter, (node, lock) -> write(node.withReboot(node.status().reboot().withIncreasedWanted()), lock));
    }

    /**
     * Set target OS version of all nodes matching given filter.
     *
     * @return the nodes in their new state
     */
    public List<Node> upgradeOs(NodeFilter filter, Optional<Version> version) {
        return performOn(filter, (node, lock) -> {
            var newStatus = node.status().withOsVersion(node.status().osVersion().withWanted(version));
            return write(node.with(newStatus), lock);
        });
    }

    /** Retire nodes matching given filter */
    public List<Node> retire(NodeFilter filter, Agent agent, Instant instant) {
        return performOn(filter, (node, lock) -> write(node.withWantToRetire(true, agent, instant), lock));
    }

    /** Retire and deprovision given host and all of its children */
    public List<Node> deprovision(Node host, Agent agent, Instant instant) {
        if (!host.type().isHost()) throw new IllegalArgumentException("Cannot deprovision non-host " + host);
        Optional<NodeMutex> nodeMutex = lockAndGet(host);
        if (nodeMutex.isEmpty()) return List.of();
        List<Node> result;
        try (NodeMutex lock = nodeMutex.get(); Mutex allocationLock = lockUnallocated()) {
            // This takes allocationLock to prevent any further allocation of nodes on this host
            host = lock.node();
            NodeList children = list(allocationLock).childrenOf(host);
            result = performOn(NodeListFilter.from(children.asList()),
                               (node, nodeLock) -> write(node.withWantToRetire(true, true, agent, instant),
                                                         nodeLock));
            result.add(write(host.withWantToRetire(true, true, agent, instant), lock));
        }
        return result;
    }

    /**
     * Writes this node after it has changed some internal state but NOT changed its state field.
     * This does NOT lock the node repository implicitly, but callers are expected to already hold the lock.
     *
     * @param lock already acquired lock
     * @return the written node for convenience
     */
    public Node write(Node node, Mutex lock) { return write(List.of(node), lock).get(0); }

    /**
     * Writes these nodes after they have changed some internal state but NOT changed their state field.
     * This does NOT lock the node repository implicitly, but callers are expected to already hold the lock.
     *
     * @param lock already acquired lock
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
        for (Node node : db.readNodes()) {
            if ( ! filter.matches(node)) continue;
            if (node.allocation().isPresent())
                allocatedNodes.put(node.allocation().get().owner(), node);
            else
                unallocatedNodes.add(node);
        }

        // perform operation while holding locks
        List<Node> resultingNodes = new ArrayList<>();
        try (Mutex lock = lockUnallocated()) {
            for (Node node : unallocatedNodes) {
                Optional<Node> currentNode = db.readNode(node.hostname()); // Re-read while holding lock
                if (currentNode.isEmpty()) continue;
                resultingNodes.add(action.apply(currentNode.get(), lock));
            }
        }
        for (Map.Entry<ApplicationId, List<Node>> applicationNodes : allocatedNodes.entrySet()) {
            try (Mutex lock = lock(applicationNodes.getKey())) {
                for (Node node : applicationNodes.getValue()) {
                    Optional<Node> currentNode = db.readNode(node.hostname());  // Re-read while holding lock
                    if (currentNode.isEmpty()) continue;
                    resultingNodes.add(action.apply(currentNode.get(), lock));
                }
            }
        }
        return resultingNodes;
    }

    public boolean canAllocateTenantNodeTo(Node host) {
        return canAllocateTenantNodeTo(host, zone.getCloud().dynamicProvisioning());
    }

    public static boolean canAllocateTenantNodeTo(Node host, boolean dynamicProvisioning) {
        if ( ! host.type().canRun(NodeType.tenant)) return false;
        if (host.status().wantToRetire()) return false;
        if (host.allocation().map(alloc -> alloc.membership().retired()).orElse(false)) return false;

        if (dynamicProvisioning)
            return EnumSet.of(Node.State.active, Node.State.ready, Node.State.provisioned).contains(host.state());
        else
            return host.state() == Node.State.active;
    }

    /** Create a lock which provides exclusive rights to making changes to the given application */
    // TODO: Move to Applications
    public Mutex lock(ApplicationId application) {
        return db.lock(application);
    }

    /** Create a lock with a timeout which provides exclusive rights to making changes to the given application */
    public Mutex lock(ApplicationId application, Duration timeout) {
        return db.lock(application, timeout);
    }

    /** Create a lock which provides exclusive rights to modifying unallocated nodes */
    public Mutex lockUnallocated() { return db.lockInactive(); }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    public Optional<NodeMutex> lockAndGet(Node node) {
        Node staleNode = node;

        final int maxRetries = 4;
        for (int i = 0; i < maxRetries; ++i) {
            Mutex lockToClose = lock(staleNode);
            try {
                // As an optimization we first try finding the node in the same state
                Optional<Node> freshNode = node(staleNode.hostname(), staleNode.state());
                if (freshNode.isEmpty()) {
                    freshNode = node(staleNode.hostname());
                    if (freshNode.isEmpty()) {
                        return Optional.empty();
                    }
                }

                if (Objects.equals(freshNode.get().allocation().map(Allocation::owner),
                                   staleNode.allocation().map(Allocation::owner))) {
                    NodeMutex nodeMutex = new NodeMutex(freshNode.get(), lockToClose);
                    lockToClose = null;
                    return Optional.of(nodeMutex);
                }

                // The wrong lock was held when the fresh node was fetched, so try again
                staleNode = freshNode.get();
            } finally {
                if (lockToClose != null) lockToClose.close();
            }
        }

        throw new IllegalStateException("Giving up (after " + maxRetries + " attempts) " +
                                        "fetching an up to date node under lock: " + node.hostname());
    }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    public Optional<NodeMutex> lockAndGet(String hostname) {
        return node(hostname).flatMap(this::lockAndGet);
    }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    public NodeMutex lockAndGetRequired(Node node) {
        return lockAndGet(node).orElseThrow(() -> new IllegalArgumentException("No such node: " + node.hostname()));
    }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    public NodeMutex lockAndGetRequired(String hostname) {
        return lockAndGet(hostname).orElseThrow(() -> new IllegalArgumentException("No such node: " + hostname));
    }

    private Mutex lock(Node node) {
        return node.allocation().isPresent() ? lock(node.allocation().get().owner()) : lockUnallocated();
    }

    private void illegal(String message) {
        throw new IllegalArgumentException(message);
    }

}
