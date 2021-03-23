// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private final Load load;
    private final Load activeLoad;
    private final List<ApplicationStats> applicationStats;

    private NodeRepoStats(Load load, Load activeLoad, List<ApplicationStats> applicationStats) {
        this.load = load;
        this.activeLoad = activeLoad;
        this.applicationStats = List.copyOf(applicationStats);
    }

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
        List<NodeTimeseries> allNodeTimeseries = nodeRepository.metricsDb().getNodeTimeseries(Duration.ofHours(1), Set.of());

        Pair<Load, Load> load = computeLoad(allNodes, allNodeTimeseries);
        List<ApplicationStats> applicationStats = computeApplicationStats(allNodes, allNodeTimeseries);
        return new NodeRepoStats(load.getFirst(), load.getSecond(), applicationStats);
    }

    private static Pair<Load, Load> computeLoad(NodeList allNodes, List<NodeTimeseries> allNodeTimeseries) {
        NodeResources totalActiveResources = new NodeResources(0, 0, 0, 0);
        double cpu = 0, memory = 0, disk = 0;
        for (var nodeTimeseries : allNodeTimeseries) {
            Optional<Node> node = allNodes.node(nodeTimeseries.hostname());
            if (node.isEmpty() || node.get().state() != Node.State.active) continue;

            Optional<NodeMetricSnapshot> snapshot = nodeTimeseries.last();
            if (snapshot.isEmpty()) continue;

            cpu += snapshot.get().cpu() * node.get().resources().vcpu();
            memory += snapshot.get().memory() * node.get().resources().memoryGb();
            disk += snapshot.get().disk() * node.get().resources().diskGb();
            totalActiveResources = totalActiveResources.add(node.get().resources().justNumbers());
        }

        NodeResources totalHostResources = new NodeResources(0, 0, 0, 0);
        for (var host : allNodes.hosts()) {
            totalHostResources = totalHostResources.add(host.resources().justNumbers());
        }

        Load load = new Load(divide(cpu, totalHostResources.vcpu()),
                             divide(memory, totalHostResources.memoryGb()),
                             divide(disk, totalHostResources.diskGb()));
        Load activeLoad = new Load(divide(cpu, totalActiveResources.vcpu()),
                                   divide(memory, totalActiveResources.memoryGb()),
                                   divide(disk, totalActiveResources.diskGb()));
        return new Pair<>(load, activeLoad);
    }

    private static List<ApplicationStats> computeApplicationStats(NodeList allNodes,
                                                                  List<NodeTimeseries> allNodeTimeseries) {
        List<ApplicationStats> applicationStats = new ArrayList<>();
        Map<String, NodeMetricSnapshot> snapshotsByHost = byHost(allNodeTimeseries);
        for (var applicationNodes : allNodes.state(Node.State.active)
                                            .nodeType(NodeType.tenant)
                                            .matching(node -> ! node.allocation().get().owner().instance().isTester())
                                            .groupingBy(node -> node.allocation().get().owner()).entrySet()) {

            NodeResources totalResources = new NodeResources(0, 0, 0, 0);
            double utilizedCost = 0;
            double totalCpu = 0, totalMemory = 0, totalDisk = 0;
            for (var node : applicationNodes.getValue()) {
                var snapshot = snapshotsByHost.get(node.hostname());
                if (snapshot == null) continue;
                double cpu = snapshot.cpu() * node.resources().vcpu();
                double memory = snapshot.memory() * node.resources().memoryGb();
                double disk = snapshot.disk() * node.resources().diskGb();
                utilizedCost += new NodeResources(cpu, memory, disk, 0).cost();
                totalCpu += cpu;
                totalMemory += memory;
                totalDisk += disk;
                totalResources = totalResources.add(node.resources().justNumbers());
            }
            Load load = new Load(divide(totalCpu, totalResources.vcpu()),
                                 divide(totalMemory, totalResources.memoryGb()),
                                 divide(totalDisk, totalResources.diskGb()));
            applicationStats.add(new ApplicationStats(applicationNodes.getKey(), load, totalResources.cost(), utilizedCost));
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

    private static double divide(double a, double b) {
        if (a == 0 && b == 0) return 0;
        return a / b;
    }

    public static class ApplicationStats implements Comparable<ApplicationStats> {

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
            return -Double.compare(this.unutilizedCost(), other.unutilizedCost());
        }

    }

}
