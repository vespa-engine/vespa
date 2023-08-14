// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.time.TimeBudget;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.NoSuchNodeException;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.applications.Applications;
import com.yahoo.vespa.hosted.provision.maintenance.NodeFailer;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDb;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.collections.Iterables.reversed;
import static com.yahoo.vespa.hosted.provision.restapi.NodePatcher.DROP_DOCUMENTS_REPORT;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

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

    private static final Logger log = Logger.getLogger(Nodes.class.getName());

    private final CuratorDb db;
    private final Zone zone;
    private final Clock clock;
    private final Orchestrator orchestrator;
    private final Applications applications;

    public Nodes(CuratorDb db, Zone zone, Clock clock, Orchestrator orchestrator, Applications applications) {
        this.zone = zone;
        this.clock = clock;
        this.db = db;
        this.orchestrator = orchestrator;
        this.applications = applications;
    }

    /** Read and write all nodes to make sure they are stored in the latest version of the serialized format */
    public void rewrite() {
        Instant start = clock.instant();
        int nodesWritten = performOn(list(), this::write).size();
        Instant end = clock.instant();
        log.log(Level.INFO, String.format("Rewrote %d nodes in %s", nodesWritten, Duration.between(start, end)));
    }

    // ---------------- Query API ----------------------------------------------------------------

    /** Finds and returns the node with given hostname, or empty if not found */
    public Optional<Node> node(String hostname) {
        return db.readNode(hostname);
    }

    /**
     * Returns an unsorted list of all nodes in this repository, in any of the given states
     *
     * @param inState the states to return nodes from. If no states are given, all nodes are returned
     */
    public NodeList list(Node.State... inState) {
        NodeList allNodes = NodeList.copyOf(db.readNodes());
        NodeList nodes = inState.length == 0 ? allNodes : allNodes.state(Set.of(inState));
        nodes = NodeList.copyOf(nodes.stream().map(node -> specifyFully(node, allNodes)).toList());
        return nodes;
    }

    // Repair underspecified node resources. TODO: Remove this after June 2023
    private Node specifyFully(Node node, NodeList allNodes) {
        if (node.resources().isUnspecified()) return node;

        if (node.resources().bandwidthGbpsIsUnspecified())
            node = node.with(new Flavor(node.resources().withBandwidthGbps(0.3)), Agent.system, clock.instant());
        if ( node.resources().architecture() == NodeResources.Architecture.any) {
            Optional<Node> parent = allNodes.parentOf(node);
            if (parent.isPresent())
                node = node.with(new Flavor(node.resources().with(parent.get().resources().architecture())), Agent.system, clock.instant());
        }
        return node;
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
        if (activeNodes.size() < 20) return true; // Not enough data to decide
        NodeList downNodes = activeNodes.down();
        return ! ( (double)downNodes.size() / (double)activeNodes.size() > 0.2 );
    }

    // ----------------- Node lifecycle -----------------------------------------------------------

    /** Adds a list of newly created reserved nodes to the node repository */
    public List<Node> addReservedNodes(LockedNodeList nodes) {
        for (Node node : nodes) {
            if (node.flavor().getType() != Flavor.Type.DOCKER_CONTAINER)
                illegal("Cannot add " + node + ": This is not a child node");
            if (node.allocation().isEmpty())
                illegal("Cannot add " + node + ": Child nodes need to be allocated");
            Optional<Node> existing = node(node.hostname());
            if (existing.isPresent())
                throw new IllegalStateException("Cannot add " + node + ": A node with this name already exists");
        }
        return db.addNodesInState(nodes, Node.State.reserved, Agent.system);
    }

    /**
     * Adds a list of (newly created) nodes to the node repository as provisioned nodes.
     * If any of the nodes already exists in the deprovisioned state, the new node will be merged
     * with the history of that node.
     */
    public List<Node> addNodes(List<Node> nodes, Agent agent) {
        try (Mutex allocationLock = lockUnallocated()) {
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
                        throw new IllegalStateException("Cannot add " + node + ": A node with this name already exists");
                    node = node.with(existing.get().history());
                    node = node.with(existing.get().reports());
                    node = node.with(node.status().withFailCount(existing.get().status().failCount()));
                    if (existing.get().status().firmwareVerifiedAt().isPresent())
                        node = node.with(node.status().withFirmwareVerifiedAt(existing.get().status().firmwareVerifiedAt().get()));
                    // Preserve wantToRebuild/wantToRetire when rebuilding as the fields shouldn't be cleared until the
                    // host is readied (i.e. we know it is up and rebuild completed)
                    boolean rebuilding = existing.get().status().wantToRebuild();
                    if (rebuilding) {
                        node = node.with(node.status().withWantToRetire(existing.get().status().wantToRetire(),
                                                                        false,
                                                                        rebuilding,
                                                                        existing.get().status().wantToUpgradeFlavor()));
                    }
                    nodesToRemove.add(existing.get());
                }

                nodesToAdd.add(node);
            }
            NestedTransaction transaction = new NestedTransaction();
            db.removeNodes(nodesToRemove, transaction);
            List<Node> resultingNodes = db.addNodesInState(IP.Config.verify(nodesToAdd, list(allocationLock), zone), Node.State.provisioned, agent, transaction);
            transaction.commit();
            return resultingNodes;
        }
    }

    /** Sets a node to ready and returns the node in the ready state */
    public Node setReady(NodeMutex nodeMutex, Agent agent, String reason) {
        Node node = nodeMutex.node();
        if (node.state() != Node.State.provisioned && node.state() != Node.State.dirty)
            illegal("Can not set " + node + " ready. It is not provisioned or dirty.");
        if (node.status().wantToDeprovision() || node.status().wantToRebuild())
            return park(node.hostname(), false, agent, reason);

        node = node.withWantToRetire(false, false, false, false, agent, clock.instant());
        return db.writeTo(Node.State.ready, node, agent, Optional.of(reason));
    }

    /** Reserve nodes. This method does <b>not</b> lock the node repository. */
    public List<Node> reserve(List<Node> nodes) {
        return db.writeTo(Node.State.reserved, nodes, Agent.application, Optional.empty());
    }

    /** Activate nodes. This method does <b>not</b> lock the node repository. */
    public List<Node> activate(List<Node> nodes, ApplicationTransaction transaction) {
        return db.writeTo(Node.State.active, nodes, Agent.application, Optional.empty(), transaction);
    }

    /**
     * Sets a list of nodes to have their allocation removable (active to inactive) in the node repository.
     *
     * @param nodes the nodes to make removable. These nodes MUST be in the active state
     * @param reusable move the node directly to {@link Node.State#dirty} after removal
     */
    public void setRemovable(NodeList nodes, boolean reusable) {
        performOn(nodes, (node, mutex) -> write(node.with(node.allocation().get().removable(true, reusable)), mutex));
    }

    /**
     * Deactivates these nodes in a transaction and returns the nodes in the new state which will hold if the
     * transaction commits.
     */
    public List<Node> deactivate(List<Node> nodes, ApplicationTransaction transaction) {
        if ( ! zone.environment().isProduction() || zone.system().isCd())
            return deallocate(nodes, Agent.application, "Deactivated by application", transaction);

        NodeList nodeList = NodeList.copyOf(nodes);
        NodeList stateless = nodeList.stateless();
        NodeList stateful  = nodeList.stateful();
        NodeList statefulToInactive  = stateful.not().reusable();
        NodeList statefulToDirty = stateful.reusable();
        List<Node> written = new ArrayList<>();
        written.addAll(deallocate(stateless.asList(), Agent.application, "Deactivated by application", transaction));
        written.addAll(deallocate(statefulToDirty.asList(), Agent.application, "Deactivated by application (recycled)", transaction));
        written.addAll(db.writeTo(Node.State.inactive, statefulToInactive.asList(), Agent.application, Optional.empty(), transaction));
        return written;
    }

    /**
     * Fails these nodes in a transaction and returns the nodes in the new state which will hold if the
     * transaction commits.
     */
    public List<Node> fail(List<Node> nodes, ApplicationTransaction transaction) {
        return db.writeTo(Node.State.failed,
                          nodes.stream().map(n -> n.withWantToFail(false, Agent.application, clock.instant())).toList(),
                          Agent.application, Optional.of("Failed by application"), transaction);
    }

    /** Move nodes to the dirty state */
    public List<Node> deallocate(List<Node> nodes, Agent agent, String reason) {
        return performOn(NodeList.copyOf(nodes), (node, lock) -> deallocate(node, agent, reason));
    }

    public List<Node> deallocateRecursively(String hostname, Agent agent, String reason) {
        Node nodeToDirty = node(hostname).orElseThrow(() -> new NoSuchNodeException("Could not deallocate " + hostname + ": Node not found"));
        List<Node> nodesToDirty = new ArrayList<>();
        try (RecursiveNodeMutexes locked = lockAndGetRecursively(hostname, Optional.empty())) {
            for (NodeMutex child : locked.children())
                if (child.node().state() != Node.State.dirty)
                    nodesToDirty.add(child.node());

            if (locked.parent().node().state() != State.dirty)
                nodesToDirty.add(locked.parent().node());

            List<String> hostnamesNotAllowedToDirty = nodesToDirty.stream()
                                                                  .filter(node -> node.state() != Node.State.provisioned)
                                                                  .filter(node -> node.state() != Node.State.failed)
                                                                  .filter(node -> node.state() != Node.State.parked)
                                                                  .filter(node -> node.state() != Node.State.breakfixed)
                                                                  .map(Node::hostname).toList();
            if ( ! hostnamesNotAllowedToDirty.isEmpty())
                illegal("Could not deallocate " + nodeToDirty + ": " +
                        hostnamesNotAllowedToDirty + " are not in states [provisioned, failed, parked, breakfixed]");

            return nodesToDirty.stream().map(node -> deallocate(node, agent, reason)).toList();
        }
    }

    /**
     * Set a node dirty or parked, allowed if it is in the provisioned, inactive, failed or parked state.
     * Use this to clean newly provisioned nodes or to recycle failed nodes which have been repaired or put on hold.
     */
    public Node deallocate(Node node, Agent agent, String reason) {
        try (NodeMutex locked = lockAndGetRequired(node)) {
            NestedTransaction transaction = new NestedTransaction();
            Node deallocated = deallocate(locked.node(), agent, reason, transaction);
            transaction.commit();
            return deallocated;
        }
    }

    public List<Node> deallocate(List<Node> nodes, Agent agent, String reason, ApplicationTransaction transaction) {
        return nodes.stream().map(node -> deallocate(node, agent, reason, transaction.nested())).toList();
    }

    // Be sure to hold the right lock!
    private Node deallocate(Node node, Agent agent, String reason, NestedTransaction transaction) {
        if (parkOnDeallocationOf(node, agent)) {
            return park(node.hostname(), false, agent, reason, transaction);
        } else {
            Node.State toState = Node.State.dirty;
            if (node.state() == Node.State.parked && node.type().isHost()) {
                if (node.status().wantToDeprovision()) illegal("Cannot move " + node + " to " + toState + ": It's being deprovisioned");
                if (node.status().wantToRebuild()) illegal("Cannot move " + node + " to " + toState + ": It's being rebuilt");
            }
            return db.writeTo(toState, List.of(node), agent, Optional.of(reason), transaction).get(0);
        }
    }

    /**
     * Fails this node and returns it in its new state.
     *
     * @return the node in its new state
     * @throws NoSuchNodeException if the node is not found
     */
    public Node fail(String hostname, Agent agent, String reason) {
        return fail(hostname, false, agent, reason);
    }

    public Node fail(String hostname, boolean forceDeprovision, Agent agent, String reason) {
        try (NodeMutex lock = lockAndGetRequired(hostname)) {
            return move(hostname, Node.State.failed, agent, forceDeprovision, Optional.of(reason), lock);
        }
    }

    /**
     * Fails all the nodes that are children of hostname before finally failing the hostname itself.
     * Non-active nodes are failed immediately, while active nodes are marked as wantToFail.
     * The host is failed if it has no active nodes and marked wantToFail if it has.
     *
     * @return all the nodes that were changed by this request
     */
    public List<Node> failOrMarkRecursively(String hostname, Agent agent, String reason) {
        List<Node> changed = new ArrayList<>();
        try (RecursiveNodeMutexes nodes = lockAndGetRecursively(hostname, Optional.empty())) {
            for (NodeMutex child : nodes.children())
                changed.add(failOrMark(child.node(), agent, reason, child));

            if (changed.stream().noneMatch(child -> child.state() == Node.State.active))
                changed.add(move(hostname, Node.State.failed, agent, false, Optional.of(reason), nodes.parent()));
            else
                changed.add(failOrMark(nodes.parent().node(), agent, reason, nodes.parent()));
        }
        return changed;
    }

    private Node failOrMark(Node node, Agent agent, String reason, Mutex lock) {
        if (node.state() == Node.State.active) {
            node = node.withWantToFail(true, agent, clock.instant());
            write(node, lock);
            return node;
        } else {
            return move(node.hostname(), Node.State.failed, agent, false, Optional.of(reason), lock);
        }
    }

    /**
     * Parks this node and returns it in its new state.
     *
     * @return the node in its new state
     * @throws NoSuchNodeException if the node is not found
     */
    public Node park(String hostname, boolean forceDeprovision, Agent agent, String reason) {
        try (NodeMutex locked = lockAndGetRequired(hostname)) {
            NestedTransaction transaction = new NestedTransaction();
            Node parked = park(hostname, forceDeprovision, agent, reason, transaction);
            transaction.commit();
            return parked;
        }
    }

    private Node park(String hostname, boolean forceDeprovision, Agent agent, String reason, NestedTransaction transaction) {
        return move(hostname, Node.State.parked, agent, forceDeprovision, Optional.of(reason), transaction);
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
        try (NodeMutex lock = lockAndGetRequired(hostname)) {
            return move(hostname, Node.State.active, agent, false, Optional.of(reason), lock);
        }
    }

    /**
     * Moves a host to breakfixed state, removing any children.
     */
    public List<Node> breakfixRecursively(String hostname, Agent agent, String reason) {
        try (RecursiveNodeMutexes locked = lockAndGetRecursively(hostname, Optional.empty())) {
            requireBreakfixable(locked.parent().node());
            NestedTransaction transaction = new NestedTransaction();
            removeChildren(locked, false, transaction);
            move(hostname, Node.State.breakfixed, agent, false, Optional.of(reason), transaction);
            transaction.commit();
            return locked.nodes().nodes().stream().map(NodeMutex::node).toList();
        }
    }

    private List<Node> moveRecursively(String hostname, Node.State toState, Agent agent, Optional<String> reason) {
        try (RecursiveNodeMutexes locked = lockAndGetRecursively(hostname, Optional.empty())) {
            List<Node> moved = new ArrayList<>();
            NestedTransaction transaction = new NestedTransaction();
            for (NodeMutex node : locked.nodes().nodes())
                moved.add(move(node.node().hostname(), toState, agent, false, reason, transaction));
            transaction.commit();
            return moved;
        }
    }

    /** Move a node to given state */
    private Node move(String hostname, Node.State toState, Agent agent, boolean forceDeprovision, Optional<String> reason, Mutex lock) {
        NestedTransaction transaction = new NestedTransaction();
        Node moved = move(hostname, toState, agent, forceDeprovision, reason, transaction);
        transaction.commit();
        return moved;
    }

    /** Move a node to given state as part of a transaction */
    private Node move(String hostname, Node.State toState, Agent agent, boolean forceDeprovision, Optional<String> reason, NestedTransaction transaction) {
        // TODO: This lock is already held here, but we still need to read the node. Perhaps change to requireNode(hostname) later.
        try (NodeMutex lock = lockAndGetRequired(hostname)) {
            Node node = lock.node();
            if (toState == Node.State.active) {
                if (node.allocation().isEmpty()) illegal("Could not set " + node + " active: It has no allocation");
                for (Node currentActive : list(Node.State.active).owner(node.allocation().get().owner())) {
                    if (node.allocation().get().membership().cluster().equals(currentActive.allocation().get().membership().cluster())
                        && node.allocation().get().membership().index() == currentActive.allocation().get().membership().index())
                        illegal("Could not set " + node + " active: Same cluster and index as " + currentActive);
                }
            }
            if (node.state() == Node.State.deprovisioned) {
               illegal(node + " cannot be moved");
            }
            // Clear all retirement flags when parked by operator
            Instant now = clock.instant();
            if (toState == Node.State.parked && agent == Agent.operator) {
                if (forceDeprovision) illegal("Cannot force deprovisioning when agent is " + Agent.operator);
                node = node.withWantToRetire(false, false, false, false, agent, now)
                           .withPreferToRetire(false, agent, now);
            }
            if (forceDeprovision)
                node = node.withWantToRetire(true, true, false, false, agent, now);
            if (toState == Node.State.deprovisioned) {
                node = node.with(IP.Config.EMPTY);
            }
            return db.writeTo(toState, List.of(node), agent, reason, transaction).get(0);
        }
    }

    /*
     * This method is used by the REST API to handle readying nodes for new allocations. For Linux
     * containers this will remove the node from node repository, otherwise the node will be moved to state ready.
     */
    public Node markNodeAvailableForNewAllocation(String hostname, Agent agent, String reason) {
        try (NodeMutex nodeMutex = lockAndGetRequired(hostname)) {
            Node node = nodeMutex.node();
            if (node.type() == NodeType.tenant) {
                if (node.state() != Node.State.dirty)
                    illegal("Cannot make " + node + " available for new allocation as it is not in state [dirty]");

                NestedTransaction transaction = new NestedTransaction();
                db.removeNodes(List.of(node), transaction);
                transaction.commit();
                return node;
            }

            if (node.state() == Node.State.ready) return node;

            Node parentHost = node.parentHostname().flatMap(this::node).orElse(node);
            List<String> failureReasons = NodeFailer.reasonsToFailHost(parentHost);
            if (!failureReasons.isEmpty())
                illegal(node + " cannot be readied because it has hard failures: " + failureReasons);

            return setReady(nodeMutex, agent, reason);
        }
    }

    /**
     * Removes all the nodes that are children of hostname before finally removing the hostname itself.
     *
     * @return a List of all the nodes that have been removed or (for hosts) deprovisioned
     */
    public List<Node> removeRecursively(String hostname) {
        Node node = requireNode(hostname);
        return removeRecursively(node, false);
    }

    public List<Node> removeRecursively(Node node, boolean force) {
        try (RecursiveNodeMutexes locked = lockAndGetRecursively(node.hostname(), Optional.empty())) {
            requireRemovable(locked.parent().node(), false, force);
            NestedTransaction transaction = new NestedTransaction();
            List<Node> removed;
            if ( ! node.type().isHost()) {
                removed = List.of(node);
                db.removeNodes(removed, transaction);
            }
            else {
                removeChildren(locked, force, transaction);
                move(node.hostname(), Node.State.deprovisioned, Agent.system, false, Optional.empty(), transaction);
                removed = locked.nodes().nodes().stream().map(NodeMutex::node).toList();
            }
            transaction.commit();
            return removed;
        }
    }

    /** Forgets a deprovisioned node. This removes all traces of the node in the node repository. */
    public void forget(Node node) {
        try (NodeMutex locked = lockAndGetRequired(node.hostname())) {
            if (node.state() != Node.State.deprovisioned)
                throw new IllegalArgumentException(node + " must be deprovisioned before it can be forgotten");
            if (node.status().wantToRebuild())
                throw new IllegalArgumentException(node + " is rebuilding and cannot be forgotten");
            NestedTransaction transaction = new NestedTransaction();
            db.removeNodes(List.of(node), transaction);
            transaction.commit();
        }
    }

    private void removeChildren(RecursiveNodeMutexes nodes, boolean force, NestedTransaction transaction) {
        if (nodes.children().isEmpty()) return;
        List<Node> children = nodes.children().stream().map(NodeMutex::node).toList();
        children.forEach(child -> requireRemovable(child, true, force));
        db.removeNodes(children, transaction);
    }

    /**
     * Throws if the given node cannot be removed. Removal is allowed if:
     *  - Tenant node:
     *    - non-recursively: node is unallocated
     *    - recursively: node is unallocated or node is in failed|parked
     *  - Host node: iff in state provisioned|failed|parked
     *  - Child node:
     *    - non-recursively: node in state ready
     *    - recursively: child is in state provisioned|failed|parked|dirty|ready
     */
    private void requireRemovable(Node node, boolean removingRecursively, boolean force) {
        if (force) return;

        if (node.type() == NodeType.tenant && node.allocation().isPresent()) {
            EnumSet<Node.State> removableStates = EnumSet.of(Node.State.failed, Node.State.parked);
            if (!removingRecursively || !removableStates.contains(node.state()))
                illegal(node + " is currently allocated and cannot be removed while in " + node.state());
        }

        final Set<Node.State> removableStates;
        if (node.type().isHost()) {
            removableStates = EnumSet.of(Node.State.provisioned, Node.State.failed, Node.State.parked);
        } else {
            removableStates = removingRecursively
                    ? EnumSet.of(Node.State.provisioned, Node.State.failed, Node.State.parked, Node.State.dirty, Node.State.ready)
                    // When not removing recursively, we can only remove children in state ready
                    : EnumSet.of(Node.State.ready);
        }
        if (!removableStates.contains(node.state()))
            illegal(node + " can not be removed while in " + node.state());
    }

    /**
     * Throws if given node cannot be breakfixed.
     * Breakfix is allowed if the following is true:
     *  - Node is tenant host
     *  - Node is in zone without dynamic provisioning
     *  - Node is in parked or failed state
     */
    private void requireBreakfixable(Node node) {
        if (zone.cloud().dynamicProvisioning()) {
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
     * Increases the restart generation of the active nodes matching given filter.
     *
     * @return the nodes in their new state
     */
    public List<Node> restartActive(Predicate<Node> filter) {
        return restart(NodeFilter.in(Set.of(Node.State.active)).and(filter));
    }

    /**
     * Increases the restart generation of the any nodes matching given filter.
     *
     * @return the nodes in their new state
     */
    public List<Node> restart(Predicate<Node> filter) {
        return performOn(filter, (node, lock) -> write(node.withRestart(node.allocation().get().restartGeneration().withIncreasedWanted()),
                                                       lock));
    }

    /**
     * Increases the reboot generation of the nodes matching the filter.
     *
     * @return the nodes in their new state
     */
    public List<Node> reboot(Predicate<Node> filter) {
        return performOn(filter, (node, lock) -> write(node.withReboot(node.status().reboot().withIncreasedWanted()), lock));
    }

    /**
     * Set target OS version of all nodes matching given filter.
     *
     * @return the nodes in their new state
     */
    public List<Node> upgradeOs(Predicate<Node> filter, Optional<Version> version) {
        return performOn(filter, (node, lock) -> write(node.withWantedOsVersion(version), lock));
    }

    /** Retire nodes matching given filter */
    public List<Node> retire(Predicate<Node> filter, Agent agent, Instant instant) {
        return performOn(filter, (node, lock) -> write(node.withWantToRetire(true, agent, instant), lock));
    }

    /** Retire and deprovision given host and all of its children */
    public List<Node> deprovision(String hostname, Agent agent, Instant instant) {
        return decommission(hostname, HostOperation.deprovision, agent, instant);
    }

    /** Rebuild given host */
    public List<Node> rebuild(String hostname, boolean soft, Agent agent, Instant instant) {
        return decommission(hostname, soft ? HostOperation.softRebuild : HostOperation.rebuild, agent, instant);
    }

    /** Upgrade flavor for given host */
    public List<Node> upgradeFlavor(String hostname, Agent agent, Instant instant, boolean upgrade) {
        return decommission(hostname, upgrade ? HostOperation.upgradeFlavor : HostOperation.cancel, agent, instant);
    }

    private List<Node> decommission(String hostname, HostOperation op, Agent agent, Instant instant) {
        Optional<NodeMutex> nodeMutex = lockAndGet(hostname);
        if (nodeMutex.isEmpty()) return List.of();
        List<Node> result = new ArrayList<>();
        boolean wantToDeprovision = op == HostOperation.deprovision;
        boolean wantToRebuild = op == HostOperation.rebuild || op == HostOperation.softRebuild;
        boolean wantToRetire = op.needsRetirement();
        boolean wantToUpgradeFlavor = op == HostOperation.upgradeFlavor;
        Node host = nodeMutex.get().node();
        try (NodeMutex lock = nodeMutex.get()) {
            if ( ! host.type().isHost()) throw new IllegalArgumentException("Cannot " + op + " non-host " + host);
            try (Mutex allocationLock = lockUnallocated()) {
                // Modify parent with wantToRetire while holding the allocationLock to prevent
                // any further allocation of nodes on this host
                Node newHost = lock.node().withWantToRetire(wantToRetire, wantToDeprovision, wantToRebuild, wantToUpgradeFlavor, agent, instant);
                result.add(write(newHost, lock));
            }
        }
        if (wantToRetire || op == HostOperation.cancel) { // Apply recursively if we're retiring, or cancelling
            List<Node> updatedNodes = performOn(list().childrenOf(host), (node, nodeLock) -> {
                Node newNode = node.withWantToRetire(wantToRetire, wantToDeprovision, false, false, agent, instant);
                return write(newNode, nodeLock);
            });
            result.addAll(updatedNodes);
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

    public List<Node> performOn(Predicate<Node> filter, BiFunction<Node, Mutex, Node> action) {
        return performOn(list(), filter, action);
    }

    /**
     * Performs an operation requiring locking on all given nodes.
     *
     * @param action the action to perform
     * @return the set of nodes on which the action was performed, as they became as a result of the operation
     */
    public List<Node> performOn(NodeList nodes, BiFunction<Node, Mutex, Node> action) {
        return performOn(nodes, __ -> true, action);
    }

    public List<Node> performOn(NodeList nodes, Predicate<Node> filter, BiFunction<Node, Mutex, Node> action) {
        List<Node> resultingNodes = new ArrayList<>();
        nodes.matching(filter).stream().collect(groupingBy(Nodes::applicationIdForLock))
             .forEach((applicationId, nodeList) -> { // Grouped only to reduce number of lock acquire/release cycles.
                 try (NodeMutexes locked = lockAndGetAll(nodeList, Optional.empty())) {
                     for (NodeMutex node : locked.nodes())
                         if (filter.test(node.node()))
                             resultingNodes.add(action.apply(node.node(), node));
                 }
             });
        return resultingNodes;
    }

    public List<Node> performOnRecursively(NodeList parents, Predicate<RecursiveNodeMutexes> filter, Function<RecursiveNodeMutexes, List<Node>> action) {
        for (Node node : parents)
            if (node.parentHostname().isPresent())
                throw new IllegalArgumentException(node + " is not a parent host");

        List<Node> resultingNodes = new ArrayList<>();
        for (Node parent : parents) {
            try (RecursiveNodeMutexes locked = lockAndGetRecursively(parent.hostname(), Optional.empty())) {
                if (filter.test(locked))
                    resultingNodes.addAll(action.apply(locked));
            }
        }
        return resultingNodes;
    }

    public List<Node> dropDocuments(ApplicationId applicationId, Optional<ClusterSpec.Id> clusterId) {
        try (Mutex lock = applications.lock(applicationId)) {
            Instant now = clock.instant();
            List<Node> nodes = list(Node.State.active, Node.State.reserved)
                    .owner(applicationId)
                    .matching(node -> {
                        ClusterSpec cluster = node.allocation().get().membership().cluster();
                        if (!cluster.type().isContent()) return false;
                        return clusterId.isEmpty() || clusterId.get().equals(cluster.id());
                    })
                    .mapToList(node -> node.with(node.reports().withReport(Report.basicReport(DROP_DOCUMENTS_REPORT, Report.Type.UNSPECIFIED, now, ""))));
            if (nodes.isEmpty())
                throw new NoSuchNodeException("No content nodes found for " + applicationId + clusterId.map(id -> " and cluster " + id).orElse(""));
            return db.writeTo(nodes, Agent.operator, Optional.empty());
        }
    }

    public boolean canAllocateTenantNodeTo(Node host) {
        return canAllocateTenantNodeTo(host, zone.cloud().dynamicProvisioning());
    }

    public boolean canAllocateTenantNodeTo(Node host, boolean dynamicProvisioning) {
        if ( ! host.type().canRun(NodeType.tenant)) return false;
        if (host.status().wantToRetire()) return false;
        if (host.allocation().map(alloc -> alloc.membership().retired()).orElse(false)) return false;

        if (dynamicProvisioning)
            return EnumSet.of(Node.State.active, Node.State.ready, Node.State.provisioned).contains(host.state());
        else
            return host.state() == Node.State.active;
    }

    public boolean suspended(Node node) {
        try {
            return orchestrator.getNodeStatus(new HostName(node.hostname())).isSuspended();
        } catch (HostNameNotFoundException e) {
            // Treat it as not suspended
            return false;
        }
    }

    /** Create a lock which provides exclusive rights to modifying unallocated nodes */
    public Mutex lockUnallocated() { return db.lockInactive(); }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    private Optional<NodeMutex> lockAndGet(Node node, Optional<Duration> timeout) {
        Node staleNode = node;

        final int maxRetries = 4;
        for (int i = 0; i < maxRetries; ++i) {
            Mutex lockToClose = lock(staleNode, timeout);
            try {
                Optional<Node> freshNode = node(staleNode.hostname());
                if (freshNode.isEmpty()) {
                    return Optional.empty();
                }

                if (applicationIdForLock(freshNode.get()).equals(applicationIdForLock(staleNode))) {
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
    public Optional<NodeMutex> lockAndGet(String hostname, Duration timeout) {
        return node(hostname).flatMap(node -> lockAndGet(node, Optional.of(timeout)));
    }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    public Optional<NodeMutex> lockAndGet(Node node) { return lockAndGet(node, Optional.empty()); }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    public Optional<NodeMutex> lockAndGet(Node node, Duration timeout) { return lockAndGet(node, Optional.of(timeout)); }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    public NodeMutex lockAndGetRequired(Node node) {
        return lockAndGet(node).orElseThrow(() -> new NoSuchNodeException("No node with hostname '" + node.hostname() + "'"));
    }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    public NodeMutex lockAndGetRequired(String hostname) {
        return lockAndGet(hostname).orElseThrow(() -> new NoSuchNodeException("No node with hostname '" + hostname + "'"));
    }

    /** Returns the unallocated/application lock, and the node acquired under that lock. */
    public NodeMutex lockAndGetRequired(String hostname, Duration timeout) {
        return lockAndGet(hostname, timeout).orElseThrow(() -> new NoSuchNodeException("No node with hostname '" + hostname + "'"));
    }

    private Mutex lock(Node node, Optional<Duration> timeout) {
        Optional<ApplicationId> application = applicationIdForLock(node);
        if (application.isPresent())
            return timeout.map(t -> applications.lock(application.get(), t))
                          .orElseGet(() -> applications.lock(application.get()));
        else
            return timeout.map(db::lockInactive).orElseGet(db::lockInactive);
    }

    private Node requireNode(String hostname) {
        return node(hostname).orElseThrow(() -> new NoSuchNodeException("No node with hostname '" + hostname + "'"));
    }

    /**
     * Locks the children of the given node, the node itself, and finally takes the unallocated lock.
     * <br>
     * When taking multiple locks, it's crucial that we always take them in the same order, to avoid deadlocks.
     * We want to take the most contended locks last, so that we don't block other operations for longer than necessary.
     * This method does that, by first taking the locks for any children the given node may have, and then the node itself.
     * (This is enforced by taking host locks after tenant node locks, in {@link #lockAndGetAll(Collection, Optional)}.)
     * Finally, the allocation lock is taken, to ensure no new children are added while we hold this snapshot.
     * Unfortunately, since that lock is taken last, we may detect new nodes after taking it, and then we have to retry.
     * Closing the returned {@link RecursiveNodeMutexes} will release all the locks, and the locks should not be closed elsewhere.
     */
    public RecursiveNodeMutexes lockAndGetRecursively(String hostname, Optional<Duration> timeout) {
        TimeBudget budget = TimeBudget.fromNow(clock, timeout.orElse(Duration.ofMinutes(2)));
        Set<Node> children = new HashSet<>(list().childrenOf(hostname).asList());
        Optional<Node> node = node(hostname);

        int attempts = 5; // We'll retry locking the whole list of children this many times, in case new children appear.
        for (int attempt = 0; attempt < attempts; attempt++) {
            NodeMutexes mutexes = null;
            Mutex unallocatedLock = null;
            try {
                // First, we lock all the children, and the host; then we take the allocation lock to ensure our snapshot is valid.
                List<Node> nodes = new ArrayList<>(children.size() + 1);
                nodes.addAll(children);
                node.ifPresent(nodes::add);
                mutexes = lockAndGetAll(nodes, budget.timeLeftOrThrow());
                unallocatedLock = db.lockInactive(budget.timeLeftOrThrow().get());
                RecursiveNodeMutexes recursive = new RecursiveNodeMutexes(hostname, mutexes, unallocatedLock);
                Set<Node> freshChildren = list().childrenOf(hostname).asSet();
                Optional<Node> freshNode = recursive.parent.map(NodeMutex::node);
                if (children.equals(freshChildren) && node.equals(freshNode)) {
                    // No new nodes have appeared, and none will now, so we have a consistent snapshot.
                    if (node.isEmpty() && ! children.isEmpty())
                        throw new IllegalStateException("node '" + hostname + "' was not found, but it has children: " + children);

                    mutexes = null;
                    unallocatedLock = null;
                    return recursive;
                }
                else {
                    // New nodes have appeared, so we need to let go of the locks and try again with the new set of nodes.
                    children = freshChildren;
                    node = freshNode;
                }
            }
            finally {
                if (unallocatedLock != null) unallocatedLock.close();
                if (mutexes != null) mutexes.close();
            }
        }
        throw new IllegalStateException("giving up (after " + attempts + " attempts) fetching an up to " +
                                        "date recursive node set under lock for node " + hostname);
    }

    /** Locks all nodes in the given list, in a universal order, and returns the locks and nodes required. */
    public NodeMutexes lockAndRequireAll(Collection<Node> nodes, Optional<Duration> timeout) {
        return lockAndGetAll(nodes, timeout, true);
    }

    /** Locks all nodes in the given list, in a universal order, and returns the locks and nodes acquired. */
    public NodeMutexes lockAndGetAll(Collection<Node> nodes, Optional<Duration> timeout) {
        return lockAndGetAll(nodes, timeout, false);
    }

    /** Locks all nodes in the given list, in a universal order, and returns the locks and nodes. */
    private NodeMutexes lockAndGetAll(Collection<Node> nodes, Optional<Duration> timeout, boolean required) {
        TimeBudget budget = TimeBudget.fromNow(clock, timeout.orElse(Duration.ofMinutes(2)));
        Comparator<Node> universalOrder = (a, b) -> {
            Optional<ApplicationId> idA = applicationIdForLock(a);
            Optional<ApplicationId> idB = applicationIdForLock(b);
            if (idA.isPresent() != idB.isPresent()) return idA.isPresent() ? -1 : 1;    // Allocated nodes first.
            if (a.type() != b.type()) return a.type().compareTo(b.type());              // Tenant nodes first among those.
            if ( ! idA.equals(idB)) return idA.get().compareTo(idB.get());              // Sort primarily by tenant owner id.
            return a.hostname().compareTo(b.hostname());                                // Sort secondarily by hostname.
        };
        NavigableSet<NodeMutex> locked = new TreeSet<>(comparing(NodeMutex::node, universalOrder));
        NavigableSet<Node> unlocked = new TreeSet<>(universalOrder);
        unlocked.addAll(nodes);
        try {
            int attempts = 10; // We'll accept getting the wrong lock at most this many times before giving up.
            for (int attempt = 0; attempt < attempts; ) {
                if (unlocked.isEmpty()) {
                    NodeMutexes mutexes = new NodeMutexes(List.copyOf(locked));
                    locked.clear();
                    return mutexes;
                }

                // If the first node is now earlier in lock order than some other locks we have, we need to close those and re-acquire them.
                Node next = unlocked.pollFirst();
                Set<NodeMutex> outOfOrder = locked.tailSet(new NodeMutex(next, () -> { }), false);
                NodeMutexes.close(outOfOrder);
                for (NodeMutex node : outOfOrder) unlocked.add(node.node());
                outOfOrder.clear();

                boolean nextLockSameAsPrevious = ! locked.isEmpty() && applicationIdForLock(locked.last().node()).equals(applicationIdForLock(next));
                Mutex lock = nextLockSameAsPrevious ? () -> { } : lock(next, budget.timeLeftOrThrow());
                try {
                    Optional<Node> fresh = node(next.hostname());
                    if (fresh.isEmpty()) {
                        if (required) throw new NoSuchNodeException("No node with hostname '" + next.hostname() + "'");
                        continue; // Node is gone; skip to close lock.
                    }

                    if (applicationIdForLock(fresh.get()).equals(applicationIdForLock(next))) {
                        // We held the right lock, so this node is ours now.
                        locked.add(new NodeMutex(fresh.get(), lock));
                        lock = null;
                    }
                    else {
                        // We held the wrong lock, and need to try again.
                        ++attempt;
                        unlocked.add(fresh.get());
                    }
                }
                finally {
                    // If we didn't hold the right lock, we must close the wrong one before we continue.
                    if (lock != null) lock.close();
                }
            }
            throw new IllegalStateException("giving up (after " + attempts + " extra attempts) to lock nodes: " +
                                            nodes.stream().map(Node::hostname).collect(joining(", ")));
        }
        finally {
            // If we didn't manage to lock all nodes, we must close the ones we did lock before we throw.
            NodeMutexes.close(locked);
        }
    }

    /** A node with their locks, acquired in a universal order. */
    public record NodeMutexes(List<NodeMutex> nodes) implements AutoCloseable {
        @Override public void close() { close(nodes); }
        private static void close(Collection<NodeMutex> nodes) {
            RuntimeException thrown = null;
            for (NodeMutex node : reversed(List.copyOf(nodes))) {
                try {
                    node.close();
                }
                catch (RuntimeException e) {
                    if (thrown == null) thrown = e;
                    else thrown.addSuppressed(e);
                }
            }
            if (thrown != null) throw thrown;
        }
    }

    /** A parent node, all its children, their locks acquired in a universal order, and then the unallocated lock. */
    public static class RecursiveNodeMutexes implements AutoCloseable {

        private final String hostname;
        private final NodeMutexes nodes;
        private final Mutex unallocatedLock;
        private final List<NodeMutex> children;
        private final Optional<NodeMutex> parent;

        public RecursiveNodeMutexes(String hostname, NodeMutexes nodes, Mutex unallocatedLock) {
            this.hostname = hostname;
            this.nodes = nodes;
            this.unallocatedLock = unallocatedLock;
            this.children = nodes.nodes().stream().filter(node -> ! node.node().hostname().equals(hostname)).toList();
            this.parent = nodes.nodes().stream().filter(node -> node.node().hostname().equals(hostname)).findFirst();
        }

        /** Any children of the node. */
        public List<NodeMutex> children() { return children; }
        /** The node itself, or throws if the node was not found. */
        public NodeMutex parent() { return parent.orElseThrow(() -> new NoSuchNodeException("No node with hostname '" + hostname + "'")); }
        /** Empty if the node was not found, or the node, and any children. */
        public NodeMutexes nodes() { return nodes; }
        /** Closes the allocation lock, and all the node locks. */
        @Override public void close() { try (nodes; unallocatedLock) { } }
    }

    /** Returns the application ID that should be used for locking when modifying this node */
    private static Optional<ApplicationId> applicationIdForLock(Node node) {
        return switch (node.type()) {
            case tenant -> node.allocation().map(Allocation::owner);
            case host -> Optional.of(InfrastructureApplication.TENANT_HOST.id());
            case config -> Optional.of(InfrastructureApplication.CONFIG_SERVER.id());
            case confighost -> Optional.of(InfrastructureApplication.CONFIG_SERVER_HOST.id());
            case controller -> Optional.of(InfrastructureApplication.CONTROLLER.id());
            case controllerhost -> Optional.of(InfrastructureApplication.CONTROLLER_HOST.id());
            case proxy -> Optional.of(InfrastructureApplication.PROXY.id());
            case proxyhost -> Optional.of(InfrastructureApplication.PROXY_HOST.id());
        };
    }

    private static void illegal(String message) {
        throw new IllegalArgumentException(message);
    }

    /** Returns whether node should be parked when deallocated by given agent */
    private static boolean parkOnDeallocationOf(Node node, Agent agent) {
        return  agent != Agent.operator &&
               !node.status().wantToDeprovision() &&
                node.status().wantToRetire() &&
                node.history().event(History.Event.Type.wantToRetire)
                              .map(History.Event::agent)
                              .map(a -> a == Agent.operator)
                              .orElse(false);
    }

    private enum HostOperation {

        /** Host is deprovisioned and data is destroyed */
        deprovision(true),

        /** Host is deprovisioned, the same host is later re-provisioned and data is destroyed */
        rebuild(true),

        /** Host is stopped and re-bootstrapped, data is preserved */
        softRebuild(false),

        /** Host flavor should be upgraded, data is destroyed */
        upgradeFlavor(true),

        /** Attempt to cancel any ongoing operations. If the current operation has progressed too far, cancelling won't have any effect */
        cancel(false);

        private final boolean needsRetirement;

        HostOperation(boolean needsRetirement) {
            this.needsRetirement = needsRetirement;
        }

        /** Returns whether this operation requires the host and its children to be retired */
        public boolean needsRetirement() {
            return needsRetirement;
        }

    }

}
