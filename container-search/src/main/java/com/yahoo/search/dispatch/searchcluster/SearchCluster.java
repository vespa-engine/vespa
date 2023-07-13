// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.container.handler.VipStatus;
import com.yahoo.net.HostName;
import com.yahoo.prelude.Pong;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.cluster.NodeManager;
import com.yahoo.yolean.UncheckedInterruptedException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.stream.Collectors.groupingBy;

/**
 * A model of a search cluster we might want to dispatch queries to.
 *
 * @author bratseth
 */
public class SearchCluster implements NodeManager<Node> {

    private static final Logger log = Logger.getLogger(SearchCluster.class.getName());

    private final String clusterId;
    private final VipStatus vipStatus;
    private final PingFactory pingFactory;
    private volatile SearchGroupsImpl groups;
    private volatile long nextLogTime = 0;

    /**
     * A search node on this local machine having the entire corpus, which we therefore
     * should prefer to dispatch directly to, or empty if there is no such local search node.
     * If there is one, we also maintain the VIP status of this container based on the availability
     * of the corpus on this local node (up + has coverage), such that this node is taken out of rotation
     * if it only queries this cluster when the local node cannot be used, to avoid unnecessary
     * cross-node network traffic.
     */
    private final Node localCorpusDispatchTarget;

    public SearchCluster(String clusterId, double minActivedocsPercentage, Collection<Node> nodes,
                         VipStatus vipStatus, PingFactory pingFactory) {
        this(clusterId, toGroups(nodes, minActivedocsPercentage), vipStatus, pingFactory);
    }

    public SearchCluster(String clusterId, SearchGroupsImpl groups, VipStatus vipStatus, PingFactory pingFactory) {
        this.clusterId = clusterId;
        this.vipStatus = vipStatus;
        this.pingFactory = pingFactory;
        this.groups = groups;
        this.localCorpusDispatchTarget = findLocalCorpusDispatchTarget(HostName.getLocalhost(), groups);
    }

    @Override
    public String name() { return clusterId; }

    /** Sets the new nodes to monitor to be the new nodes, but keep any existing node instances which equal the new ones. */
    public ClusterMonitor<Node> updateNodes(Collection<Node> newNodes, double minActivedocsPercentage) {
        Collection<Node> retainedNodes = groups.nodes();
        Collection<Node> currentNodes = new HashSet<>(newNodes);
        retainedNodes.retainAll(currentNodes);          // Throw away all old nodes which are not in the new set.
        currentNodes.removeIf(retainedNodes::contains); // Throw away all new nodes for which we have more information in an old object.
        Collection<Node> addedNodes = List.copyOf(currentNodes);
        currentNodes.addAll(retainedNodes);             // Keep the old nodes that were replaced in the new set.
        SearchGroupsImpl groups = toGroups(currentNodes, minActivedocsPercentage);
        ClusterMonitor<Node> monitor = new ClusterMonitor<>(this, false);
        for (Node node : groups.nodes()) monitor.add(node, true);
        monitor.start();
        try { while (addedNodes.stream().anyMatch(node -> node.isWorking() == null)) { Thread.sleep(1); } }
        catch (InterruptedException e) { throw new UncheckedInterruptedException(e, true); }
        pingIterationCompleted(groups);
        this.groups = groups;
        return monitor;
    }

    public void addMonitoring(ClusterMonitor<Node> clusterMonitor) {
        for (Node node : groups.nodes()) clusterMonitor.add(node, true);
    }

    private static Node findLocalCorpusDispatchTarget(String selfHostname, SearchGroups groups) {
        // A search node in the search cluster in question is configured on the same host as the currently running container.
        // It has all the data <==> No other nodes in the search cluster have the same group id as this node.
        //         That local search node responds.
        // The search cluster to be searched has at least as many nodes as the container cluster we're running in.
        List<Node> localSearchNodes = groups.groups().stream().flatMap(g -> g.nodes().stream())
                                           .filter(node -> node.hostname().equals(selfHostname))
                                           .toList();
        // Only use direct dispatch if we have exactly 1 search node on the same machine:
        if (localSearchNodes.size() != 1) return null;

        Node localSearchNode = localSearchNodes.iterator().next();
        Group localSearchGroup = groups.get(localSearchNode.group());

        // Only use direct dispatch if the local search node has the entire corpus
        if (localSearchGroup.nodes().size() != 1) return null;

        return localSearchNode;
    }

    private static SearchGroupsImpl toGroups(Collection<Node> nodes, double minActivedocsPercentage) {
        Map<Integer, Group> groups = new HashMap<>();
        nodes.stream().collect(groupingBy(Node::group)).forEach((groupId, groupNodes) -> {
            groups.put(groupId, new Group(groupId, groupNodes));
        });
        return new SearchGroupsImpl(Map.copyOf(groups), minActivedocsPercentage);
    }

    public SearchGroups groupList() { return groups; }

    public Group group(int id) { return groups.get(id); }

    private Collection<Group> groups() { return groups.groups(); }

    public int groupsWithSufficientCoverage() {
        return (int) groups().stream().filter(Group::hasSufficientCoverage).count();
    }

    /**
     * Returns the single, local node we should dispatch queries directly to,
     * or empty if we should not dispatch directly.
     */
    public Optional<Node> localCorpusDispatchTarget() {
        if (localCorpusDispatchTarget == null) return Optional.empty();

        // Only use direct dispatch if the local group has sufficient coverage
        Group localSearchGroup = groups.get(localCorpusDispatchTarget.group());
        if ( ! localSearchGroup.hasSufficientCoverage()) return Optional.empty();

        // Only use direct dispatch if the local search node is not down
        if (localCorpusDispatchTarget.isWorking() == Boolean.FALSE) return Optional.empty();

        return Optional.of(localCorpusDispatchTarget);
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
        if (localCorpusDispatchTarget == null) { // consider entire cluster
            if (hasInformationAboutAllNodes())
                setInRotationOnlyIf(hasWorkingNodes());
        }
        else if (usesLocalCorpusIn(node)) { // follow the status of this node
            // Do not take this out of rotation if we're a combined cluster of size 1,
            // as that can't be helpful, and leads to a deadlock where this node is never set back in service
            if (nodeIsWorking || groups().stream().map(Group::nodes).count() > 1)
                setInRotationOnlyIf(nodeIsWorking);
        }
    }

    private void updateVipStatusOnCoverageChange(Group group, boolean sufficientCoverage) {
        if ( localCorpusDispatchTarget == null) { // consider entire cluster
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

    public boolean hasInformationAboutAllNodes() {
        return groups().stream().allMatch(group -> group.nodes().stream().allMatch(node -> node.isWorking() != null));
    }

    long nonWorkingNodeCount() {
        return groups().stream().flatMap(group -> group.nodes().stream()).filter(node -> node.isWorking() == Boolean.FALSE).count();
    }

    private boolean hasWorkingNodes() {
        return groups().stream().anyMatch(group -> group.nodes().stream().anyMatch(node -> node.isWorking() != Boolean.FALSE));
    }

    private boolean usesLocalCorpusIn(Node node) {
        return node.equals(localCorpusDispatchTarget);
    }

    private boolean usesLocalCorpusIn(Group group) {
        return (localCorpusDispatchTarget != null) && localCorpusDispatchTarget.group() == group.id();
    }

    /** Used by the cluster monitor to manage node status */
    @Override
    public void ping(ClusterMonitor<Node> clusterMonitor, Node node, Executor executor) {
        Pinger pinger = pingFactory.createPinger(node, clusterMonitor, new PongCallback(node, clusterMonitor));
        pinger.ping();
    }

    private void pingIterationCompletedSingleGroup(SearchGroupsImpl groups) {
        Group group = groups.groups().iterator().next();
        group.aggregateNodeValues();
        // With just one group sufficient coverage may not be the same as full coverage, as the
        // group will always be marked sufficient for use.
        updateSufficientCoverage(group, true);
        boolean sufficientCoverage = groups.isGroupCoverageSufficient(group.activeDocuments(), group.activeDocuments());
        trackGroupCoverageChanges(group, sufficientCoverage, group.activeDocuments());
    }

    private void pingIterationCompletedMultipleGroups(SearchGroupsImpl groups) {
        groups.groups().forEach(Group::aggregateNodeValues);
        long medianDocuments = groups.medianDocumentsPerGroup();
        for (Group group : groups.groups()) {
            boolean sufficientCoverage = groups.isGroupCoverageSufficient(group.activeDocuments(), medianDocuments);
            updateSufficientCoverage(group, sufficientCoverage);
            trackGroupCoverageChanges(group, sufficientCoverage, medianDocuments);
        }
    }

    /**
     * Update statistics after a round of issuing pings.
     * Note that this doesn't wait for pings to return, so it will typically accumulate data from
     * last rounds pinging, or potentially (although unlikely) some combination of new and old data.
     */
    @Override
    public void pingIterationCompleted() {
        pingIterationCompleted(groups);
    }

    private void pingIterationCompleted(SearchGroupsImpl groups) {
        if (groups.size() == 1) {
            pingIterationCompletedSingleGroup(groups);
        } else {
            pingIterationCompletedMultipleGroups(groups);
        }
    }

    /**
     * Calculate whether a subset of nodes in a group has enough coverage
     */
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
                String message = "Cluster " + clusterId + ": " + group + " has reduced coverage: " +
                                 "Active documents: " + group.activeDocuments() + "/" + medianDocuments + ", " +
                                 "Target active documents: " + group.targetActiveDocuments() + ", " +
                                 "working nodes: " + group.workingNodes() + "/" + group.nodes().size() +
                                 ", unresponsive nodes: " + (unresponsive.toString().isEmpty() ? " none" : unresponsive);
                if (nonWorkingNodeCount() == 1) // That is normal
                    log.info(message);
                else
                    log.warning(message);
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
