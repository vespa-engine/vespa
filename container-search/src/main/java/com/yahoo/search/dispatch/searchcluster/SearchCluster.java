// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.net.HostName;
import com.yahoo.prelude.Pong;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.cluster.NodeManager;
import com.yahoo.search.dispatch.TopKEstimator;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Executor;
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
    private final VipStatus vipStatus;
    private final PingFactory pingFactory;
    private final TopKEstimator hitEstimator;
    private long nextLogTime = 0;
    private static final double SKEW_FACTOR = 0.05;

    /**
     * A search node on this local machine having the entire corpus, which we therefore
     * should prefer to dispatch directly to, or empty if there is no such local search node.
     * If there is one, we also maintain the VIP status of this container based on the availability
     * of the corpus on this local node (up + has coverage), such that this node is taken out of rotation
     * if it only queries this cluster when the local node cannot be used, to avoid unnecessary
     * cross-node network traffic.
     */
    private final Optional<Node> localCorpusDispatchTarget;

    public SearchCluster(String clusterId, DispatchConfig dispatchConfig, int containerClusterSize,
                         VipStatus vipStatus, PingFactory pingFactory) {
        this.clusterId = clusterId;
        this.dispatchConfig = dispatchConfig;
        this.vipStatus = vipStatus;
        this.pingFactory = pingFactory;

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
        nodes.forEach(node -> groupIntroductionOrder.put(node.group(), groups.get(node.group())));
        this.orderedGroups = ImmutableList.<Group>builder().addAll(groupIntroductionOrder.values()).build();

        // Index nodes by host
        ImmutableMultimap.Builder<String, Node> nodesByHostBuilder = new ImmutableMultimap.Builder<>();
        for (Node node : nodes)
            nodesByHostBuilder.put(node.hostname(), node);
        this.nodesByHost = nodesByHostBuilder.build();
        hitEstimator = new TopKEstimator(30.0, dispatchConfig.topKProbability(), SKEW_FACTOR);

        this.localCorpusDispatchTarget = findLocalCorpusDispatchTarget(HostName.getLocalhost(),
                                                                       size,
                                                                       containerClusterSize,
                                                                       nodesByHost,
                                                                       groups);
    }

    /* Testing only */
    public SearchCluster(String clusterId, DispatchConfig dispatchConfig,
                         VipStatus vipStatus, PingFactory pingFactory) {
        this(clusterId, dispatchConfig, 1, vipStatus, pingFactory);
    }

    public void addMonitoring(ClusterMonitor clusterMonitor) {
        for (var group : orderedGroups()) {
            for (var node : group.nodes())
                clusterMonitor.add(node, true);
        }
    }

    private static Optional<Node> findLocalCorpusDispatchTarget(String selfHostname,
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
        for (DispatchConfig.Node node : dispatchConfig.node())
            nodesBuilder.add(new Node(node.key(), node.host(), node.group()));
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
        if (orderedGroups().size() > n) {
            return Optional.of(orderedGroups().get(n));
        } else {
            return Optional.empty();
        }
    }

    /** Returns the number of nodes per group - size()/groups.size() */
    public int groupSize() {
        if (groups().size() == 0) return size();
        return size() / groups().size();
    }

    public int groupsWithSufficientCoverage() {
        int covered = 0;
        for (Group g : orderedGroups()) {
            if (g.hasSufficientCoverage()) {
                covered++;
            }
        }
        return covered;
    }

    /**
     * Returns the single, local node we should dispatch queries directly to,
     * or empty if we should not dispatch directly.
     */
    public Optional<Node> localCorpusDispatchTarget() {
        if ( localCorpusDispatchTarget.isEmpty()) return Optional.empty();

        // Only use direct dispatch if the local group has sufficient coverage
        Group localSearchGroup = groups().get(localCorpusDispatchTarget.get().group());
        if ( ! localSearchGroup.hasSufficientCoverage()) return Optional.empty();

        // Only use direct dispatch if the local search node is not down
        if ( localCorpusDispatchTarget.get().isWorking() == Boolean.FALSE) return Optional.empty();

        return localCorpusDispatchTarget;
    }

    private void updateWorkingState(Node node, boolean isWorking) {
        node.setWorking(isWorking);
        updateVipStatusOnNodeChange(node, isWorking);
    }

    /** Called by the cluster monitor when node state changes to working */
    @Override
    public void working(Node node) {
        updateWorkingState(node, true);
    }

    /** Called by the cluster monitor when node state changes to failed */
    @Override
    public void failed(Node node) {
        updateWorkingState(node, false);
    }

    private void updateSufficientCoverage(Group group, boolean sufficientCoverage) {
        if (sufficientCoverage == group.hasSufficientCoverage()) return; // no change

        group.setHasSufficientCoverage(sufficientCoverage);
        updateVipStatusOnCoverageChange(group, sufficientCoverage);
    }

    private void updateVipStatusOnNodeChange(Node node, boolean nodeIsWorking) {
        if (localCorpusDispatchTarget.isEmpty()) { // consider entire cluster
            if (hasInformationAboutAllNodes())
                setInRotationOnlyIf(hasWorkingNodes());
        }
        else if (usesLocalCorpusIn(node)) { // follow the status of this node
            // Do not take this out of rotation if we're a combined cluster of size 1,
            // as that can't be helpful, and leads to a deadlock where this node is never taken back in servic e
            if (nodeIsWorking || size() > 1)
                setInRotationOnlyIf(nodeIsWorking);
        }
    }

    private void updateVipStatusOnCoverageChange(Group group, boolean sufficientCoverage) {
        if ( localCorpusDispatchTarget.isEmpty()) { // consider entire cluster
            // VIP status does not depend on coverage
        }
        else if (usesLocalCorpusIn(group)) { // follow the status of this group
            setInRotationOnlyIf(sufficientCoverage);
        }
    }

    private void setInRotationOnlyIf(boolean inRotation) {
        if (inRotation)
            vipStatus.addToRotation(clusterId);
        else
            vipStatus.removeFromRotation(clusterId);
    }

    public int estimateHitsToFetch(int wantedHits, int numPartitions) {
        return hitEstimator.estimateK(wantedHits, numPartitions);
    }
    public int estimateHitsToFetch(int wantedHits, int numPartitions, double topKProbability) {
        return hitEstimator.estimateK(wantedHits, numPartitions, topKProbability);
    }

    public boolean hasInformationAboutAllNodes() {
        return nodesByHost.values().stream().allMatch(node -> node.isWorking() != null);
    }

    private boolean hasWorkingNodes() {
        return nodesByHost.values().stream().anyMatch(node -> node.isWorking() != Boolean.FALSE );
    }

    private boolean usesLocalCorpusIn(Node node) {
        return localCorpusDispatchTarget.isPresent() && localCorpusDispatchTarget.get().equals(node);
    }

    private boolean usesLocalCorpusIn(Group group) {
        return localCorpusDispatchTarget.isPresent() && localCorpusDispatchTarget.get().group() == group.id();
    }

    private static class PongCallback implements PongHandler {

        private final ClusterMonitor<Node> clusterMonitor;
        private final Node node;

        PongCallback(Node node, ClusterMonitor<Node> clusterMonitor) {
            this.node = node;
            this.clusterMonitor = clusterMonitor;
        }

        @Override
        public void handle(Pong pong) {
            if (pong.badResponse()) {
                clusterMonitor.failed(node, pong.error().get());
            } else {
                if (pong.activeDocuments().isPresent()) {
                    node.setActiveDocuments(pong.activeDocuments().get());
                    node.setBlockingWrites(pong.isBlockingWrites());
                }
                clusterMonitor.responded(node);
            }
        }

    }

    /** Used by the cluster monitor to manage node status */
    @Override
    public void ping(ClusterMonitor clusterMonitor, Node node, Executor executor) {
        Pinger pinger = pingFactory.createPinger(node, clusterMonitor, new PongCallback(node, clusterMonitor));
        pinger.ping();
    }

    private void pingIterationCompletedSingleGroup() {
        Group group = groups().values().iterator().next();
        group.aggregateNodeValues();
        // With just one group sufficient coverage may not be the same as full coverage, as the
        // group will always be marked sufficient for use.
        updateSufficientCoverage(group, true);
        boolean fullCoverage = isGroupCoverageSufficient(group.workingNodes(), group.nodes().size(), group.getActiveDocuments(),
                                                         group.getActiveDocuments());
        trackGroupCoverageChanges(0, group, fullCoverage, group.getActiveDocuments());
    }

    private void pingIterationCompletedMultipleGroups() {
        int numGroups = orderedGroups().size();
        // Update active documents per group and use it to decide if the group should be active
        long[] activeDocumentsInGroup = new long[numGroups];
        long sumOfActiveDocuments = 0;
        for(int i = 0; i < numGroups; i++) {
            Group group = orderedGroups().get(i);
            group.aggregateNodeValues();
            activeDocumentsInGroup[i] = group.getActiveDocuments();
            sumOfActiveDocuments += activeDocumentsInGroup[i];
        }

        boolean anyGroupsSufficientCoverage = false;
        for (int i = 0; i < numGroups; i++) {
            Group group = orderedGroups().get(i);
            long activeDocuments = activeDocumentsInGroup[i];
            long averageDocumentsInOtherGroups = (sumOfActiveDocuments - activeDocuments) / (numGroups - 1);
            boolean sufficientCoverage = isGroupCoverageSufficient(group.workingNodes(), group.nodes().size(), activeDocuments, averageDocumentsInOtherGroups);
            anyGroupsSufficientCoverage = anyGroupsSufficientCoverage || sufficientCoverage;
            updateSufficientCoverage(group, sufficientCoverage);
            trackGroupCoverageChanges(i, group, sufficientCoverage, averageDocumentsInOtherGroups);
        }
    }

    /**
     * Update statistics after a round of issuing pings.
     * Note that this doesn't wait for pings to return, so it will typically accumulate data from
     * last rounds pinging, or potentially (although unlikely) some combination of new and old data.
     */
    @Override
    public void pingIterationCompleted() {
        int numGroups = orderedGroups().size();
        if (numGroups == 1) {
            pingIterationCompletedSingleGroup();
        } else {
            pingIterationCompletedMultipleGroups();
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

    public boolean isGroupWellBalanced(OptionalInt groupId) {
        if (groupId.isEmpty()) return false;
        Group group = groups().get(groupId.getAsInt());
        return (group != null) && group.isContentWellBalanced();
    }

    /**
     * Calculate whether a subset of nodes in a group has enough coverage
     */
    public boolean isPartialGroupCoverageSufficient(OptionalInt knownGroupId, List<Node> nodes) {
        if (orderedGroups().size() == 1) {
            boolean sufficient = nodes.size() >= groupSize() - dispatchConfig.maxNodesDownPerGroup();
            return sufficient;
        }

        if (knownGroupId.isEmpty()) {
            return false;
        }
        int groupId = knownGroupId.getAsInt();
        Group group = groups().get(groupId);
        if (group == null) {
            return false;
        }
        int nodesInGroup = group.nodes().size();
        long sumOfActiveDocuments = 0;
        int otherGroups = 0;
        for (Group g : orderedGroups()) {
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
        return isGroupCoverageSufficient(nodes.size(), nodesInGroup, activeDocuments, averageDocumentsInOtherGroups);
    }

    private void trackGroupCoverageChanges(int index, Group group, boolean fullCoverage, long averageDocuments) {
        if ( ! hasInformationAboutAllNodes()) return; // Be silent until we know what we are talking about.
        boolean changed = group.isFullCoverageStatusChanged(fullCoverage);
        if (changed || (!fullCoverage && System.currentTimeMillis() > nextLogTime)) {
            nextLogTime = System.currentTimeMillis() + 30 * 1000;
            int requiredNodes = groupSize() - dispatchConfig.maxNodesDownPerGroup();
            if (fullCoverage) {
                log.info(() -> String.format("Group %d is now good again (%d/%d active docs, coverage %d/%d)",
                                             index, group.getActiveDocuments(), averageDocuments, group.workingNodes(), groupSize()));
            } else {
                StringBuilder missing = new StringBuilder();
                for (var node : group.nodes()) {
                    if (node.isWorking() != Boolean.TRUE) {
                        missing.append('\n').append(node.toString());
                    }
                }
                log.warning(() -> String.format("Coverage of group %d is only %d/%d (requires %d) (%d/%d active docs) Failed nodes are:%s",
                        index, group.workingNodes(), groupSize(), requiredNodes, group.getActiveDocuments(), averageDocuments, missing.toString()));
            }
        }
    }

}
