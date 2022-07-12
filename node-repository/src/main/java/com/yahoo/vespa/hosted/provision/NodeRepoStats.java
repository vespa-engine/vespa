// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;
import com.yahoo.vespa.hosted.provision.autoscale.NodeTimeseries;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stats about the current state known to this node repo
 *
 * @author bratseth
 */
public class NodeRepoStats {

    private final double totalCost;
    private final double totalAllocatedCost;
    private final Load load;
    private final Load activeLoad;
    private final List<ApplicationStats> applicationStats;

    private NodeRepoStats(double totalCost, double totalAllocatedCost, Load load, Load activeLoad, List<ApplicationStats> applicationStats) {
        this.totalCost = totalCost;
        this.totalAllocatedCost = totalAllocatedCost;
        this.load = load;
        this.activeLoad = activeLoad;
        this.applicationStats = List.copyOf(applicationStats);
    }

    public double totalCost() { return totalCost; }
    public double totalAllocatedCost() { return totalAllocatedCost; }

    /**
     * Returns the current average work-extracting utilization in this node repo over all nodes.
     * Capacity not allocated to active nodes are taken to have 0 utilization as it provides no useful work.
     */
    public Load load() { return load; }

    /** Returns the current average utilization in this node repo over all active nodes. */
    public Load activeLoad() { return activeLoad; }

    /** Returns stats on each application, sorted by decreasing unutilized allocation measured in dollars per hour. */
    public List<ApplicationStats> applicationStats() { return applicationStats; }

    public static NodeRepoStats computeOver(NodeRepository nodeRepository) {
        NodeList allNodes = nodeRepository.nodes().list();
        double totalCost = allNodes.hosts().stream().mapToDouble(host -> host.resources().cost()).sum();
        double totalAllocatedCost = allNodes.not().hosts().stream()
                                            .filter(node -> node.allocation().isPresent())
                                            .mapToDouble(node -> node.resources().cost()).sum();

        List<NodeTimeseries> allNodeTimeseries = nodeRepository.metricsDb().getNodeTimeseries(Duration.ofHours(1), Set.of());
        Pair<Load, Load> load = computeLoad(allNodes, allNodeTimeseries);
        List<ApplicationStats> applicationStats = computeApplicationStats(allNodes, allNodeTimeseries);
        return new NodeRepoStats(totalCost, totalAllocatedCost, load.getFirst(), load.getSecond(), applicationStats);
    }

    private static Pair<Load, Load> computeLoad(NodeList allNodes, List<NodeTimeseries> allNodeTimeseries) {
        NodeResources totalActiveResources = NodeResources.zero().justNumbers();
        Load load = Load.zero();
        for (var nodeTimeseries : allNodeTimeseries) {
            Optional<Node> node = allNodes.node(nodeTimeseries.hostname());
            if (node.isEmpty() || node.get().state() != Node.State.active) continue;

            Optional<NodeMetricSnapshot> snapshot = nodeTimeseries.last();
            if (snapshot.isEmpty()) continue;

            NodeResources resources = node.get().resources();

            load = load.add(snapshot.get().load().multiply(resources));
            totalActiveResources = totalActiveResources.add(resources.justNumbers());
        }

        NodeResources totalHostResources = NodeResources.zero().justNumbers();
        for (var host : allNodes.hosts()) {

            totalHostResources = totalHostResources.add(host.resources().justNumbers());
        }

        return new Pair<>(load.divide(totalHostResources), load.divide(totalActiveResources));
    }

    private static List<ApplicationStats> computeApplicationStats(NodeList allNodes,
                                                                  List<NodeTimeseries> allNodeTimeseries) {
        List<ApplicationStats> applicationStats = new ArrayList<>();
        Map<String, NodeMetricSnapshot> snapshotsByHost = byHost(allNodeTimeseries);
        for (var applicationNodes : allNodes.state(Node.State.active)
                                            .nodeType(NodeType.tenant)
                                            .not().tester()
                                            .groupingBy(node -> node.allocation().get().owner()).entrySet()) {

            NodeResources totalResources = NodeResources.zero().justNumbers();
            NodeResources totalUtilizedResources = NodeResources.zero().justNumbers();
            for (var node : applicationNodes.getValue()) {
                var snapshot = snapshotsByHost.get(node.hostname());
                if (snapshot == null) continue;

                totalResources = totalResources.add(node.resources().justNumbers());
                totalUtilizedResources = totalUtilizedResources.add(snapshot.load().scaled(node.resources().justNumbers()));
            }
            applicationStats.add(new ApplicationStats(applicationNodes.getKey(),
                                                      Load.byDividing(totalUtilizedResources, totalResources),
                                                      totalResources.cost(),
                                                      totalUtilizedResources.cost()));
        }
        Collections.sort(applicationStats);
        return applicationStats;
    }

    private static Map<String, NodeMetricSnapshot> byHost(List<NodeTimeseries> allNodeTimeseries) {
        Map<String, NodeMetricSnapshot> snapshots = new HashMap<>();
        for (var nodeTimeseries : allNodeTimeseries)
            nodeTimeseries.last().ifPresent(last -> snapshots.put(nodeTimeseries.hostname(), last));
        return snapshots;
    }

    public static class ApplicationStats implements Comparable<ApplicationStats> {

        private static final Comparator<ApplicationStats> comparator = Comparator.comparingDouble(ApplicationStats::unutilizedCost).reversed()
                                                                                 .thenComparingDouble(ApplicationStats::cost)
                                                                                 .thenComparing(ApplicationStats::id);

        private final ApplicationId id;
        private final Load load;
        private final double cost;
        private final double utilizedCost;

        public ApplicationStats(ApplicationId id, Load load, double cost, double utilizedCost) {
            this.id = id;
            this.load = load;
            this.cost = cost;
            this.utilizedCost = utilizedCost;
        }

        public ApplicationId id() { return id; }
        public Load load() { return load; }

        /** The standard cost of this application */
        public double cost() { return cost; }

        /** The amount of that cost which is currently utilized */
        public double utilizedCost() { return utilizedCost; }

        /** Cost - utilizedCost */
        public double unutilizedCost() { return cost - utilizedCost; }

        @Override
        public int compareTo(NodeRepoStats.ApplicationStats other) {
            return comparator.compare(this, other);
        }

    }

}
