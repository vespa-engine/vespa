// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

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
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A model of a search cluster we might want to dispatch queries to.
 *
 * @author bratseth
 */
public class SearchCluster implements NodeManager<Node> {

    private static final Logger log = Logger.getLogger(SearchCluster.class.getName());

    private final DispatchConfig dispatchConfig;
    private final int size;
    private final String clusterId;
    private final ImmutableMap<Integer, Group> groups;
    private final ImmutableMultimap<String, Node> nodesByHost;
    private final ImmutableList<Group> orderedGroups;
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

    public SearchCluster(String clusterId, DispatchConfig dispatchConfig, FS4ResourcePool fs4ResourcePool, int containerClusterSize, VipStatus vipStatus) {
        this.clusterId = clusterId;
        this.dispatchConfig = dispatchConfig;
        this.fs4ResourcePool = fs4ResourcePool;
        this.vipStatus = vipStatus;

        List<Node> nodes = toNodes(dispatchConfig);
        this.size = nodes.size();

        // Create groups
        ImmutableMap.Builder<Integer, Group> groupsBuilder = new ImmutableMap.Builder<>();
        for (Map.Entry<Integer, List<Node>> group : nodes.stream().collect(Collectors.groupingBy(Node::group)).entrySet()) {
            Group g = new Group(group.getKey(), group.getValue());
            groupsBuilder.put(group.getKey(), g);
        }
        this.groups = groupsBuilder.build();
        LinkedHashMap<Integer, Group> groupIntroductionOrder = new LinkedHashMap<>();
        nodes.forEach(node -> groupIntroductionOrder.put(node.group(), groups.get(node.group)));
        this.orderedGroups = ImmutableList.<Group>builder().addAll(groupIntroductionOrder.values()).build();

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
                                                           ImmutableMultimap<String, Node> nodesByHost,
                                                           ImmutableMap<Integer, Group> groups) {
        // A search node in the search cluster in question is configured on the same host as the currently running container.
        // It has all the data <==> No other nodes in the search cluster have the same group id as this node.
        //         That local search node responds.
        // The search cluster to be searched has at least as many nodes as the container cluster we're running in.
        ImmutableCollection<Node> localSearchNodes = nodesByHost.get(selfHostname);
        // Only use direct dispatch if we have exactly 1 search node on the same machine:
        if (localSearchNodes.size() != 1) return Optional.empty();

        Node localSearchNode = localSearchNodes.iterator().next();
        Group localSearchGroup = groups.get(localSearchNode.group());

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
        Predicate<DispatchConfig.Node> filter;
        if (dispatchConfig.useLocalNode()) {
            final String hostName = HostName.getLocalhost();
            filter = node -> node.host().equals(hostName);
        } else {
            filter = node -> true;
        }
        for (DispatchConfig.Node node : dispatchConfig.node()) {
            if (filter.test(node)) {
                nodesBuilder.add(new Node(node.key(), node.host(), node.fs4port(), node.group()));
            }
        }
        return nodesBuilder.build();
    }

    public DispatchConfig dispatchConfig() {
        return dispatchConfig;
    }

    /** Returns the number of nodes in this cluster (across all groups) */
    public int size() { return size; }

    /** Returns the groups of this cluster as an immutable map indexed by group id */
    public ImmutableMap<Integer, Group> groups() { return groups; }

    /** Returns the groups of this cluster as an immutable list in introduction order */
    public ImmutableList<Group> orderedGroups() { return orderedGroups; }

    /** Returns the n'th (zero-indexed) group in the cluster if possible */
    public Optional<Group> group(int n) {
        if (orderedGroups.size() > n) {
            return Optional.of(orderedGroups.get(n));
        } else {
            return Optional.empty();
        }
    }

    /** Returns the number of nodes per group - size()/groups.size() */
    public int groupSize() {
        if (groups.size() == 0) return size();
        return size() / groups.size();
    }

    public int groupsWithSufficientCoverage() {
        int covered = 0;
        for (Group g : orderedGroups) {
            if (g.hasSufficientCoverage()) {
                covered++;
            }
        }
        return covered;
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
        Group localSearchGroup = groups.get(directDispatchTarget.get().group());
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
            vipStatus.addToRotation(clusterId);
    }

    /** Used by the cluster monitor to manage node status */
    @Override
    public void failed(Node node) {
        node.setWorking(false);

        // Take ourselves out if we usually dispatch only to our own host
        if (usesDirectDispatchTo(node))
            vipStatus.removeFromRotation(clusterId);
    }

    private void updateSufficientCoverage(Group group, boolean sufficientCoverage) {
        // update VIP status if we direct dispatch to this group and coverage status changed
        if (usesDirectDispatchTo(group) && sufficientCoverage != group.hasSufficientCoverage()) {
            if (sufficientCoverage) {
                vipStatus.addToRotation(clusterId);
            } else {
                vipStatus.removeFromRotation(clusterId);
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
        Pinger pinger = new Pinger(node, clusterMonitor, fs4ResourcePool);
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
        int numGroups = orderedGroups.size();
        if (numGroups == 1) {
            Group group = groups.values().iterator().next();
            group.aggregateActiveDocuments();
            updateSufficientCoverage(group, true); // by definition
            return;
        }

        // Update active documents per group and use it to decide if the group should be active

        long[] activeDocumentsInGroup = new long[numGroups];
        long sumOfActiveDocuments = 0;
        for(int i = 0; i < numGroups; i++) {
            Group group = orderedGroups.get(i);
            group.aggregateActiveDocuments();
            activeDocumentsInGroup[i] = group.getActiveDocuments();
            sumOfActiveDocuments += activeDocumentsInGroup[i];
        }

        for (int i = 0; i < numGroups; i++) {
            Group group = orderedGroups.get(i);
            long activeDocuments = activeDocumentsInGroup[i];
            long averageDocumentsInOtherGroups = (sumOfActiveDocuments - activeDocuments) / (numGroups - 1);
            boolean sufficientCoverage = isGroupCoverageSufficient(group.workingNodes(), group.nodes().size(), activeDocuments,
                    averageDocumentsInOtherGroups);
            updateSufficientCoverage(group, sufficientCoverage);
        }
    }

    private boolean isGroupCoverageSufficient(int workingNodes, int nodesInGroup, long activeDocuments, long averageDocumentsInOtherGroups) {
        boolean sufficientCoverage = true;

        if (averageDocumentsInOtherGroups > 0) {
            double coverage = 100.0 * (double) activeDocuments / averageDocumentsInOtherGroups;
            sufficientCoverage = coverage >= dispatchConfig.minActivedocsPercentage();
        }
        if (sufficientCoverage) {
            sufficientCoverage = isGroupNodeCoverageSufficient(workingNodes, nodesInGroup);
        }
        return sufficientCoverage;
    }

    private boolean isGroupNodeCoverageSufficient(int workingNodes, int nodesInGroup) {
        int nodesAllowedDown = dispatchConfig.maxNodesDownPerGroup()
                + (int) (((double) nodesInGroup * (100.0 - dispatchConfig.minGroupCoverage())) / 100.0);
        return workingNodes + nodesAllowedDown >= nodesInGroup;
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

    private void logIfInsufficientCoverage(boolean sufficient, OptionalInt groupId, int nodes) {
        if (!sufficient) {
            String group = groupId.isPresent()? Integer.toString(groupId.getAsInt()) : "(unspecified)";
            log.warning(() -> String.format("Coverage of group %s is only %d/%d (requires %d)", group, nodes, groupSize(),
                    groupSize() - dispatchConfig.maxNodesDownPerGroup()));
        }
    }

    /**
     * Calculate whether a subset of nodes in a group has enough coverage
     */
    public boolean isPartialGroupCoverageSufficient(OptionalInt knownGroupId, List<Node> nodes) {
        if (orderedGroups.size() == 1) {
            boolean sufficient = nodes.size() >= groupSize() - dispatchConfig.maxNodesDownPerGroup();
            logIfInsufficientCoverage(sufficient, knownGroupId, nodes.size());
            return sufficient;
        }

        if (knownGroupId.isEmpty()) {
            return false;
        }
        int groupId = knownGroupId.getAsInt();
        Group group = groups.get(groupId);
        if (group == null) {
            return false;
        }
        int nodesInGroup = group.nodes().size();
        long sumOfActiveDocuments = 0;
        int otherGroups = 0;
        for (Group g : orderedGroups) {
            if (g.id() != groupId) {
                sumOfActiveDocuments += g.getActiveDocuments();
                otherGroups++;
            }
        }
        long activeDocuments = 0;
        for (Node n : nodes) {
            activeDocuments += n.getActiveDocuments();
        }
        long averageDocumentsInOtherGroups = sumOfActiveDocuments / otherGroups;
        boolean sufficient = isGroupCoverageSufficient(nodes.size(), nodesInGroup, activeDocuments, averageDocumentsInOtherGroups);
        logIfInsufficientCoverage(sufficient, knownGroupId, nodes.size());
        return sufficient;
    }
}
