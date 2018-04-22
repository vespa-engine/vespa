// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.net.HostName;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.cluster.NodeManager;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.vespa.config.search.DispatchConfig;

// Only needed until query requests are moved to rpc
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.yolean.Exceptions;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A model of a search cluster we might want to dispatch queries to.
 *
 * @author bratseth
 */
@Beta
public class SearchCluster implements NodeManager<SearchCluster.Node> {

    private static final Logger log = Logger.getLogger(SearchCluster.class.getName());

    /** The min active docs a group must have to be considered up, as a % of the average active docs of the other groups */
    private double minActivedocsCoveragePercentage;
    private final int size;
    private final ImmutableMap<Integer, Group> groups;
    private final ImmutableMultimap<String, Node> nodesByHost;
    private final ClusterMonitor<Node> clusterMonitor;
    private final VipStatus vipStatus;

    /**
     * A search node on this local machine having the entire corpus, which we therefore
     * should prefer to dispatch directly to, or empty if there is no such local search node.
     * If there is one, we also maintain the VIP status of this container based on the availability
     * of the corpus on this local node (up + has coverage), such that this node is taken out of rotation
     * if it only queries this cluster when the local node cannot be used, to avoid unnecessary
     * cross-node network traffic.
     */
    private final Optional<Node> directDispatchTarget;

    // Only needed until query requests are moved to rpc
    private final FS4ResourcePool fs4ResourcePool;

    public SearchCluster(DispatchConfig dispatchConfig, FS4ResourcePool fs4ResourcePool,
                         int containerClusterSize, VipStatus vipStatus) {
        this(dispatchConfig.minActivedocsPercentage(), toNodes(dispatchConfig), fs4ResourcePool,
             containerClusterSize, vipStatus);
    }

    public SearchCluster(double minActivedocsCoverage, List<Node> nodes, FS4ResourcePool fs4ResourcePool,
                         int containerClusterSize, VipStatus vipStatus) {
        this.minActivedocsCoveragePercentage = minActivedocsCoverage;
        this.size = nodes.size();
        this.fs4ResourcePool = fs4ResourcePool;
        this.vipStatus = vipStatus;

        // Create groups
        ImmutableMap.Builder<Integer, Group> groupsBuilder = new ImmutableMap.Builder<>();
        for (Map.Entry<Integer, List<Node>> group : nodes.stream().collect(Collectors.groupingBy(Node::group)).entrySet())
            groupsBuilder.put(group.getKey(), new Group(group.getKey(), group.getValue()));
        this.groups = groupsBuilder.build();

        // Index nodes by host
        ImmutableMultimap.Builder<String, Node> nodesByHostBuilder = new ImmutableMultimap.Builder<>();
        for (Node node : nodes)
            nodesByHostBuilder.put(node.hostname(), node);
        this.nodesByHost = nodesByHostBuilder.build();

        this.directDispatchTarget = findDirectDispatchTarget(HostName.getLocalhost(), size, containerClusterSize,
                                                             nodesByHost, groups);

        // Set up monitoring of the fs4 interface of the nodes
        // We can switch to monitoring the rpc interface instead when we move the query phase to rpc
        this.clusterMonitor = new ClusterMonitor<>(this);
        for (Node node : nodes) {
            // cluster monitor will only call working() when the
            // node transitions from down to up, so we need to
            // register the initial (working) state here:
            working(node);
            clusterMonitor.add(node, true);
        }
    }

    private static Optional<Node> findDirectDispatchTarget(String selfHostname,
                                                           int searchClusterSize,
                                                           int containerClusterSize,
                                                           ImmutableMultimap<String, Node>nodesByHost,
                                                           ImmutableMap<Integer, Group> groups) {
        // A search node in the search cluster in question is configured on the same host as the currently running container.
        // It has all the data <==> No other nodes in the search cluster have the same group id as this node.
        //         That local search node responds.
        // The search cluster to be searched has at least as many nodes as the container cluster we're running in.
        ImmutableCollection<Node> localSearchNodes = nodesByHost.get(selfHostname);
        // Only use direct dispatch if we have exactly 1 search node on the same machine:
        if (localSearchNodes.size() != 1) return Optional.empty();

        SearchCluster.Node localSearchNode = localSearchNodes.iterator().next();
        SearchCluster.Group localSearchGroup = groups.get(localSearchNode.group());

        // Only use direct dispatch if the local search node has the entire corpus
        if (localSearchGroup.nodes().size() != 1) return Optional.empty();

        // Only use direct dispatch if this container cluster has at least as many nodes as the search cluster
        // to avoid load skew/preserve fanout in the case where a subset of the search nodes are also containers.
        // This disregards the case where the search and container clusters are partially overlapping.
        // Such configurations produce skewed load in any case.
        if (containerClusterSize < searchClusterSize) return Optional.empty();

        return Optional.of(localSearchNode);
    }

    private static ImmutableList<Node> toNodes(DispatchConfig dispatchConfig) {
        ImmutableList.Builder<Node> nodesBuilder = new ImmutableList.Builder<>();
        for (DispatchConfig.Node node : dispatchConfig.node())
            nodesBuilder.add(new Node(node.host(), node.fs4port(), node.group()));
        return nodesBuilder.build();
    }

    /** Returns the number of nodes in this cluster (across all groups) */
    public int size() { return size; }

    /** Returns the groups of this cluster as an immutable map indexed by group id */
    public ImmutableMap<Integer, Group> groups() { return groups; }

    /** Returns the number of nodes per group - size()/groups.size() */
    public int groupSize() { 
        if (groups.size() == 0) return size();
        return size() / groups.size(); 
    }
    
    /**
     * Returns the nodes of this cluster as an immutable map indexed by host.
     * One host may contain multiple nodes (on different ports), so this is a multi-map.
     */
    public ImmutableMultimap<String, Node> nodesByHost() { return nodesByHost; }

    /**
     * Returns the recipient we should dispatch queries directly to (bypassing fdispatch),
     * or empty if we should not dispatch directly.
     */
    public Optional<Node> directDispatchTarget() {
        if ( ! directDispatchTarget.isPresent()) return Optional.empty();

        // Only use direct dispatch if the local group has sufficient coverage
        SearchCluster.Group localSearchGroup = groups.get(directDispatchTarget.get().group());
        if ( ! localSearchGroup.hasSufficientCoverage()) return Optional.empty();

        // Only use direct dispatch if the local search node is up
        if ( ! directDispatchTarget.get().isWorking()) return Optional.empty();

        return directDispatchTarget;
    }

    /** Used by the cluster monitor to manage node status */
    @Override
    public void working(Node node) {
        node.setWorking(true);

        if (usesDirectDispatchTo(node))
            vipStatus.addToRotation(this);
    }

    /** Used by the cluster monitor to manage node status */
    @Override
    public void failed(Node node) {
        node.setWorking(false);

        // Take ourselves out if we usually dispatch only to our own host
        if (usesDirectDispatchTo(node))
            vipStatus.removeFromRotation(this);
    }

    private void updateSufficientCoverage(Group group, boolean sufficientCoverage) {
        // update VIP status if we direct dispatch to this group and coverage status changed
        if (usesDirectDispatchTo(group) && sufficientCoverage != group.hasSufficientCoverage()) {
            if (sufficientCoverage) {
                vipStatus.addToRotation(this);
            } else {
                vipStatus.removeFromRotation(this);
            }
        }
        group.setHasSufficientCoverage(sufficientCoverage);
    }

    private boolean usesDirectDispatchTo(Node node) {
        if ( ! directDispatchTarget.isPresent()) return false;
        return directDispatchTarget.get().equals(node);
    }

    private boolean usesDirectDispatchTo(Group group) {
        if ( ! directDispatchTarget.isPresent()) return false;
        return directDispatchTarget.get().group() == group.id();
    }

    /** Used by the cluster monitor to manage node status */
    @Override
    public void ping(Node node, Executor executor) {
        Pinger pinger = new Pinger(node);
        FutureTask<Pong> futurePong = new FutureTask<>(pinger);
        executor.execute(futurePong);
        Pong pong = getPong(futurePong, node);
        futurePong.cancel(true);

        if (pong.badResponse())
            clusterMonitor.failed(node, pong.getError(0));
        else
            clusterMonitor.responded(node);
    }

    /**
     * Update statistics after a round of issuing pings.
     * Note that this doesn't wait for pings to return, so it will typically accumulate data from
     * last rounds pinging, or potentially (although unlikely) some combination of new and old data.
     */
    @Override
    public void pingIterationCompleted() {
        // Update active documents per group and use it to decide if the group should be active
        for (Group group : groups.values())
            group.aggregateActiveDocuments();
        if (groups.size() == 1) {
            updateSufficientCoverage(groups.values().iterator().next(), true); // by definition
        } else {
            for (Group currentGroup : groups.values()) {
                long sumOfAactiveDocumentsInOtherGroups = 0;
                for (Group otherGroup : groups.values())
                    if (otherGroup != currentGroup)
                        sumOfAactiveDocumentsInOtherGroups += otherGroup.getActiveDocuments();
                long averageDocumentsInOtherGroups = sumOfAactiveDocumentsInOtherGroups / (groups.size() - 1);
                if (averageDocumentsInOtherGroups == 0)
                    updateSufficientCoverage(currentGroup, true); // no information about any group; assume coverage
                else
                    updateSufficientCoverage(currentGroup,
                                             100 * (double) currentGroup.getActiveDocuments() / averageDocumentsInOtherGroups > minActivedocsCoveragePercentage);
            }
        }
    }

    private Pong getPong(FutureTask<Pong> futurePong, Node node) {
        try {
            return futurePong.get(clusterMonitor.getConfiguration().getFailLimit(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Exception pinging " + node, e);
            return new Pong(ErrorMessage.createUnspecifiedError("Ping was interrupted: " + node));
        } catch (ExecutionException e) {
            log.log(Level.WARNING, "Exception pinging " + node, e);
            return new Pong(ErrorMessage.createUnspecifiedError("Execution was interrupted: " + node));
        } catch (TimeoutException e) {
            return new Pong(ErrorMessage.createNoAnswerWhenPingingNode("Ping thread timed out"));
        }
    }

    private class Pinger implements Callable<Pong> {

        private final Node node;

        public Pinger(Node node) {
            this.node = node;
        }

        public Pong call() {
            try {
                Pong pong = FastSearcher.ping(new Ping(clusterMonitor.getConfiguration().getRequestTimeout()),
                                              fs4ResourcePool.getBackend(node.hostname(), node.fs4port()), node.toString());
                if (pong.activeDocuments().isPresent())
                    node.setActiveDocuments(pong.activeDocuments().get());
                return pong;
            } catch (RuntimeException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Exception when pinging " + node + ": "
                                + Exceptions.toMessageString(e)));
            }
        }

    }

    /** A group in a search cluster. This class is multithread safe. */
    public static class Group {

        private final int id;
        private final ImmutableList<Node> nodes;

        private final AtomicBoolean hasSufficientCoverage = new AtomicBoolean(true);
        private final AtomicLong activeDocuments = new AtomicLong(0);

        public Group(int id, List<Node> nodes) {
            this.id = id;
            this.nodes = ImmutableList.copyOf(nodes);
        }

        /** Returns the unique identity of this group */
        public int id() { return id; }

        /** Returns the nodes in this group as an immutable list */
        public ImmutableList<Node> nodes() { return nodes; }

        /**
         * Returns whether this group has sufficient active documents
         * (compared to other groups) that is should receive traffic
         */
        public boolean hasSufficientCoverage() {
            return hasSufficientCoverage.get();
        }

        void setHasSufficientCoverage(boolean sufficientCoverage) {
            hasSufficientCoverage.lazySet(sufficientCoverage);
        }

        void aggregateActiveDocuments() {
            long activeDocumentsInGroup = 0;
            for (Node node : nodes)
                activeDocumentsInGroup += node.getActiveDocuments();
            activeDocuments.set(activeDocumentsInGroup);

        }

        /** Returns the active documents on this node. If unknown, 0 is returned. */
        long getActiveDocuments() {
            return this.activeDocuments.get();
        }

        @Override
        public String toString() { return "search group " + id; }

        @Override
        public int hashCode() { return id; }

        @Override
        public boolean equals(Object other) {
            if (other == this) return true;
            if (!(other instanceof Group)) return false;
            return ((Group) other).id == this.id;
        }

    }

    /** A node in a search cluster. This class is multithread safe. */
    public static class Node {

        private final String hostname;
        private final int fs4port;
        private final int group;

        private final AtomicBoolean working = new AtomicBoolean(true);
        private final AtomicLong activeDocuments = new AtomicLong(0);

        public Node(String hostname, int fs4port, int group) {
            this.hostname = hostname;
            this.fs4port = fs4port;
            this.group = group;
        }

        public String hostname() { return hostname; }

        public int fs4port() { return fs4port; }

        /** Returns the id of this group this node belongs to */
        public int group() { return group; }

        void setWorking(boolean working) {
            this.working.lazySet(working);
        }

        /** Returns whether this node is currently responding to requests */
        public boolean isWorking() { return working.get(); }

        /** Updates the active documents on this node */
        void setActiveDocuments(long activeDocuments) {
            this.activeDocuments.set(activeDocuments);
        }

        /** Returns the active documents on this node. If unknown, 0 is returned. */
        public long getActiveDocuments() {
            return this.activeDocuments.get();
        }

        @Override
        public int hashCode() { return Objects.hash(hostname, fs4port); }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Node)) return false;
            Node other = (Node)o;
            if ( ! Objects.equals(this.hostname, other.hostname)) return false;
            if ( ! Objects.equals(this.fs4port, other.fs4port)) return false;
            return true;
        }

        @Override
        public String toString() { return "search node " + hostname + ":" + fs4port + " in group " + group; }

    }

}
