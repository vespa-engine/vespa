// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.google.common.collect.ImmutableMap;
import com.google.common.math.Quantiles;
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
    private final String clusterId;
    private final Map<Integer, Group> groups;
    private final List<Group> orderedGroups;
    private final List<Node> nodes;
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

    public SearchCluster(String clusterId, DispatchConfig dispatchConfig,
                         VipStatus vipStatus, PingFactory pingFactory) {
        this.clusterId = clusterId;
        this.dispatchConfig = dispatchConfig;
        this.vipStatus = vipStatus;
        this.pingFactory = pingFactory;

        this.nodes = toNodes(dispatchConfig);

        // Create groups
        ImmutableMap.Builder<Integer, Group> groupsBuilder = new ImmutableMap.Builder<>();
        for (Map.Entry<Integer, List<Node>> group : nodes.stream().collect(Collectors.groupingBy(Node::group)).entrySet()) {
            Group g = new Group(group.getKey(), group.getValue());
            groupsBuilder.put(group.getKey(), g);
        }
        this.groups = groupsBuilder.build();
        LinkedHashMap<Integer, Group> groupIntroductionOrder = new LinkedHashMap<>();
        nodes.forEach(node -> groupIntroductionOrder.put(node.group(), groups.get(node.group())));
        this.orderedGroups = List.copyOf(groupIntroductionOrder.values());

        hitEstimator = new TopKEstimator(30.0, dispatchConfig.topKProbability(), SKEW_FACTOR);
        this.localCorpusDispatchTarget = findLocalCorpusDispatchTarget(HostName.getLocalhost(), nodes, groups);
    }

    @Override
    public String name() { return clusterId; }

    public void addMonitoring(ClusterMonitor<Node> clusterMonitor) {
        for (var group : orderedGroups()) {
            for (var node : group.nodes())
                clusterMonitor.add(node, true);
        }
    }

    private static Optional<Node> findLocalCorpusDispatchTarget(String selfHostname,
                                                                List<Node> nodes,
                                                                Map<Integer, Group> groups) {
        // A search node in the search cluster in question is configured on the same host as the currently running container.
        // It has all the data <==> No other nodes in the search cluster have the same group id as this node.
        //         That local search node responds.
        // The search cluster to be searched has at least as many nodes as the container cluster we're running in.
        List<Node> localSearchNodes = nodes.stream()
                                           .filter(node -> node.hostname().equals(selfHostname))
                                           .collect(Collectors.toList());
        // Only use direct dispatch if we have exactly 1 search node on the same machine:
        if (localSearchNodes.size() != 1) return Optional.empty();

        Node localSearchNode = localSearchNodes.iterator().next();
        Group localSearchGroup = groups.get(localSearchNode.group());

        // Only use direct dispatch if the local search node has the entire corpus
        if (localSearchGroup.nodes().size() != 1) return Optional.empty();

        return Optional.of(localSearchNode);
    }

    private static List<Node> toNodes(DispatchConfig dispatchConfig) {
        return dispatchConfig.node().stream()
                             .map(n -> new Node(n.key(), n.host(), n.group()))
                             .collect(Collectors.toUnmodifiableList());
    }

    public DispatchConfig dispatchConfig() {
        return dispatchConfig;
    }

    /** Returns an immutable list of all nodes in this. */
    public List<Node> nodes() { return nodes; }

    /** Returns the groups of this cluster as an immutable map indexed by group id */
    public Map<Integer, Group> groups() { return groups; }

    /** Returns the groups of this cluster as an immutable list in introduction order */
    public List<Group> orderedGroups() { return orderedGroups; }

    /** Returns the n'th (zero-indexed) group in the cluster if possible */
    public Optional<Group> group(int n) {
        if (orderedGroups().size() > n) {
            return Optional.of(orderedGroups().get(n));
        } else {
            return Optional.empty();
        }
    }

    public boolean allGroupsHaveSize1() {
        return nodes.size() == groups.size();
    }

    public int groupsWithSufficientCoverage() {
        return (int)groups.values().stream().filter(g -> g.hasSufficientCoverage()).count();
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
            // as that can't be helpful, and leads to a deadlock where this node is never set back in service
            if (nodeIsWorking || nodes.size() > 1)
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
        return nodes.stream().allMatch(node -> node.isWorking() != null);
    }

    private boolean hasWorkingNodes() {
        return nodes.stream().anyMatch(node -> node.isWorking() != Boolean.FALSE );
    }

    private boolean usesLocalCorpusIn(Node node) {
        return localCorpusDispatchTarget.isPresent() && localCorpusDispatchTarget.get().equals(node);
    }

    private boolean usesLocalCorpusIn(Group group) {
        return localCorpusDispatchTarget.isPresent() && localCorpusDispatchTarget.get().group() == group.id();
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
        boolean sufficientCoverage = isGroupCoverageSufficient(group.activeDocuments(), group.activeDocuments());
        trackGroupCoverageChanges(group, sufficientCoverage, group.activeDocuments());
    }

    private void pingIterationCompletedMultipleGroups() {
        orderedGroups().forEach(Group::aggregateNodeValues);
        long medianDocuments = medianDocumentsPerGroup();
        for (Group group : orderedGroups()) {
            boolean sufficientCoverage = isGroupCoverageSufficient(group.activeDocuments(), medianDocuments);
            updateSufficientCoverage(group, sufficientCoverage);
            trackGroupCoverageChanges(group, sufficientCoverage, medianDocuments);
        }
    }

    private long medianDocumentsPerGroup() {
        if (orderedGroups().isEmpty()) return 0;
        var activeDocuments = orderedGroups().stream().map(Group::activeDocuments).collect(Collectors.toList());
        return (long)Quantiles.median().compute(activeDocuments);
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

    private boolean isGroupCoverageSufficient(long activeDocuments, long medianDocuments) {
        double documentCoverage = 100.0 * (double) activeDocuments / medianDocuments;
        if (medianDocuments > 0 && documentCoverage < dispatchConfig.minActivedocsPercentage())
            return false;
        return true;
    }

    /**
     * Calculate whether a subset of nodes in a group has enough coverage
     */
    public boolean isPartialGroupCoverageSufficient(List<Node> nodes) {
        if (orderedGroups().size() == 1)
            return true;
        long activeDocuments = nodes.stream().mapToLong(Node::getActiveDocuments).sum();
        return isGroupCoverageSufficient(activeDocuments, medianDocumentsPerGroup());
    }

    private void trackGroupCoverageChanges(Group group, boolean fullCoverage, long medianDocuments) {
        if ( ! hasInformationAboutAllNodes()) return; // Be silent until we know what we are talking about.
        boolean changed = group.fullCoverageStatusChanged(fullCoverage);
        if (changed || (!fullCoverage && System.currentTimeMillis() > nextLogTime)) {
            nextLogTime = System.currentTimeMillis() + 30 * 1000;
            if (fullCoverage) {
                log.info("Cluster " + clusterId + ": " + group + " has full coverage. " +
                         "Active documents: " + group.activeDocuments() + "/" + medianDocuments + ", " +
                         "Target active documents: " + group.targetActiveDocuments() + ", " +
                         "working nodes: " + group.workingNodes() + "/" + group.nodes().size());
            } else {
                StringBuilder unresponsive = new StringBuilder();
                for (var node : group.nodes()) {
                    if (node.isWorking() != Boolean.TRUE)
                        unresponsive.append('\n').append(node);
                }
                log.warning("Cluster " + clusterId + ": " + group + " has reduced coverage: " +
                            "Active documents: " + group.activeDocuments() + "/" + medianDocuments + ", " +
                            "Target active documents: " + group.targetActiveDocuments() + ", " +
                            "working nodes: " + group.workingNodes() + "/" + group.nodes().size() +
                            ", unresponsive nodes: " + (unresponsive.toString().isEmpty() ? " none" : unresponsive));
            }
        }
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
                    node.setTargetActiveDocuments(pong.targetActiveDocuments().get());
                    node.setBlockingWrites(pong.isBlockingWrites());
                }
                clusterMonitor.responded(node);
            }
        }

    }

}
