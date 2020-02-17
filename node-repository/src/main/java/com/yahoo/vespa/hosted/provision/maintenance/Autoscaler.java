// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.NodeMetricsDb;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Maintainer making automatic scaling decisions
 *
 * @author bratseth
 */
public class Autoscaler extends Maintainer {

    private static final int minimumMeasurements = 1000;
    private static final double idealRatioLowWatermark = 0.75;
    private static final double idealRatioHighWatermark = 1.25;

    // We only depend on the ratios between these values
    private static final double cpuUnitCost = 12.0;
    private static final double memoryUnitCost = 1.2;
    private static final double diskUnitCost = 0.045;

    // TODO: These should come from the application package
    private int minimumNodesPerCluster = 3;
    private int maximumNodesPerCluster = 1000;

    private final NodeMetricsDb metricsDb;

    public Autoscaler(NodeMetricsDb metricsDb, NodeRepository nodeRepository, Duration interval) {
        super(nodeRepository, interval);
        this.metricsDb = metricsDb;
    }

    @Override
    protected void maintain() {
        if ( ! nodeRepository().zone().environment().isProduction()) return;

        nodesByApplication().forEach((applicationId, nodes) -> autoscale(applicationId, nodes));
    }

    private void autoscale(ApplicationId applicationId, List<Node> applicationNodes) {
        nodesByCluster(applicationNodes).forEach((clusterSpec, clusterNodes) -> {
            Optional<ClusterResources> target = autoscaleTo(applicationId, clusterSpec, clusterNodes);
            target.ifPresent(t -> log.info("Autoscale: Application " + applicationId + " cluster " + clusterSpec +
                                           " from " + applicationNodes.size() + " * " + applicationNodes.get(0).flavor().resources() +
                                           " to " + t.count() + " * " + t.resources()));
        });
    }

    private Optional<ClusterResources> autoscaleTo(ApplicationId applicationId, ClusterSpec cluster, List<Node> clusterNodes) {
        double targetTotalCpu =    targetAllocation(Resource.cpu,    applicationId, cluster, clusterNodes);
        double targetTotalMemory = targetAllocation(Resource.memory, applicationId, cluster, clusterNodes);
        double targetTotalDisk =   targetAllocation(Resource.disk,   applicationId, cluster, clusterNodes);

        NodeResources currentResources = clusterNodes.get(0).flavor().resources();

        Optional<Pair<ClusterResources, Double>> bestTarget = Optional.empty();
        for (int targetCount = minimumNodesPerCluster; targetCount <= maximumNodesPerCluster; targetCount++ ) {
            NodeResources targetResources = targetResources(targetCount, targetTotalCpu, targetTotalMemory, targetTotalDisk, currentResources);
            var target = considerTarget(targetResources, targetCount);
            if (target.isEmpty()) continue;
            if (target.get().getSecond() < bestTarget.get().getSecond()) // second is the waste
                bestTarget = target;
        }
        return bestTarget.map(target -> target.getFirst());
    }

    /**
     * Returns the practical (allocatable) node resources corresponding to the given resources,
     * as well as a measure of the waste incurred by using these resources to satisfy the given target,
     * or empty if this target is illegal
     */
    private Optional<Pair<ClusterResources, Double>> considerTarget(NodeResources targetResources, int targetCount) {

        NodeResources effectiveResources = findEffectiveResources(targetResources);
        int effectiveCount = targetCount + 1; // need one extra node for redundancy

        // Verify invariants - not expected to fail
        if ( ! effectiveResources.satisfies(targetResources)) return Optional.empty();
        if (effectiveCount < minimumNodesPerCluster || effectiveCount > maximumNodesPerCluster) return Optional.empty();


    }

    /** Convert the given resources to resources having the given total values divided by a node count */
    private NodeResources targetResources(int nodeCount,
                                          double targetTotalCpu, double targetTotalMemory, double targetTotalDisk,
                                          NodeResources currentResources) {
        return currentResources.withVcpu(targetTotalCpu / nodeCount)
                               .withMemoryGb(targetTotalMemory / nodeCount)
                               .withDiskGb(targetTotalDisk / nodeCount);
    }


    /** Returns the allocation we should have of this resource over all the nodes in the cluster */
    private double targetAllocation(Resource resource, ApplicationId applicationId, ClusterSpec cluster, List<Node> clusterNodes) {
        double currentAllocation = resource.valueFrom(clusterNodes.get(0).flavor().resources()) * clusterNodes.size();

        List<Measurement> measurements = metricsDb.getSince(nodeRepository().clock().instant().minus(scalingWindow(cluster.type())),
                                                            resource.metric(),
                                                            applicationId,
                                                            cluster);
        if (measurements.size() < minimumMeasurements) return currentAllocation;
        if ( ! nodesIn(measurements).equals(clusterNodes)); // Regulate only when all nodes are measured and no others
        // TODO: Bail out if allocations have changed

        double averageLoad = average(measurements);
        double idealRatio = 1 + (averageLoad - resource.idealAverageLoad() / resource.idealAverageLoad());
        if (idealRatio > idealRatioLowWatermark && idealRatio < idealRatioHighWatermark) return currentAllocation;
        return currentAllocation * idealRatio;
    }

    private Map<ApplicationId, List<Node>> nodesByApplication() {
        return nodeRepository().list().nodeType(NodeType.tenant).state(Node.State.active).asList()
                               .stream().collect(Collectors.groupingBy(n -> n.allocation().get().owner()));
    }

    private Map<ClusterSpec, List<Node>> nodesByCluster(List<Node> applicationNodes) {
        return applicationNodes.stream().collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster()));
    }

    /** The duration of the window we need to consider to make a scaling decision */
    private Duration scalingWindow(ClusterSpec.Type clusterType) {
        if (clusterType.isContent()) return Duration.ofHours(12); // Ideally we should use observed redistribution time
        return Duration.ofMinutes(3); // Ideally we should take node startup time into account
    }

    private enum Resource {

        cpu {
            String metric() { return "cpu"; } // TODO: Full metric name
            double idealAverageLoad() { return 0.2; }
            double valueFrom(NodeResources resources) { return resources.vcpu(); }
        },

        memory {
            String metric() { return "memory"; } // TODO: Full metric name
            double idealAverageLoad() { return 0.7; }
            double valueFrom(NodeResources resources) { return resources.memoryGb(); }
        },

        disk {
            String metric() { return "disk"; } // TODO: Full metric name
            double idealAverageLoad() { return 0.7; }
            double valueFrom(NodeResources resources) { return resources.diskGb(); }
        };

        abstract String metric();
        abstract double idealAverageLoad();
        abstract double valueFrom(NodeResources resources);

    }

    /** A secription of the resources of a cluster */
    private static class ClusterResources {

        /** The node count in the cluster */
        private final int count;

        /** The resources of each node in the cluster */
        private final NodeResources resources;

        public ClusterResources(int count, NodeResources resources) {
            this.count = count;
            this.resources = resources;
        }

        public int count() { return count; }
        public NodeResources resources() { return resources; }

    }

}
