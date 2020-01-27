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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maintainer making automatic scaling decisions
 *
 * @author bratseth
 */
public class Autoscaler extends Maintainer {

    private static final double idealRatioLowWatermark = 0.75;
    private static final double idealRatioHighWatermark = 1.25;

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
        if (nodeRepository().zone().environment().isTest()) return;

        nodesByApplication().forEach((applicationId, nodes) -> autoscale(applicationId, nodes));
    }

    private void autoscale(ApplicationId applicationId, List<Node> applicationNodes) {
        nodesByCluster(applicationNodes).forEach((clusterSpec, clusterNodes) -> {
            Optional<Pair<Integer, NodeResources>> target = autoscaleTo(applicationId, clusterSpec, clusterNodes);
            if (target.isPresent())
                log.info("Autoscale: Application " + applicationId + " cluster " + clusterSpec +
                         " from " + applicationNodes.size() + " * " + applicationNodes.get(0).flavor().resources() +
                         " to " +   target.get().getFirst() + " * " + target.get().getSecond());
        });
    }

    private Optional<Pair<Integer, NodeResources>> autoscaleTo(ApplicationId applicationId, ClusterSpec cluster, List<Node> clusterNodes) {
        double targetTotalCpu =    targetAllocation(Resource.cpu,    applicationId, cluster, clusterNodes);
        double targetTotalMemory = targetAllocation(Resource.memory, applicationId, cluster, clusterNodes);
        double targetTotalDisk =   targetAllocation(Resource.disk,   applicationId, cluster, clusterNodes);

        int count = clusterNodes.size();
        NodeResources currentResources = clusterNodes.get(0).flavor().resources();

        NodeResources targetResourcesAtCount =
                targetResources(count, targetTotalCpu, targetTotalMemory, targetTotalDisk, currentResources);
        if (targetResourcesAtCount.equals(currentResources)) return Optional.empty();

        // Consider 3 options: Maintain current node count, increase by 1, decrease by 1
        NodeResources targetResourcesAtCountPlus1 =
                targetResources(count + 1, targetTotalCpu, targetTotalMemory, targetTotalDisk, currentResources);
        NodeResources targetResourcesAtCountMinus1 =
                targetResources(count + 1, targetTotalCpu, targetTotalMemory, targetTotalDisk, currentResources);



        NodeResources practicalTargetResourcesAtCount =       findPracticalTargetNodeResources(targetResourcesAtCount, count);
        NodeResources practicalTargetResourcesAtCountPlus1 =  findPracticalTargetNodeResources(targetResourcesAtCountPlus1, count + 1);
        NodeResources practicalTargetResourcesAtCountMinus1 = findPracticalTargetNodeResources(targetResourcesAtCountMinus1, count - 1);
    }

    /**
     * Returns the practical (allocatable) node resources corresponding to the given resources,
     * as well as a measure of the waste incurred by using these resources to satisfy the given target,
     * or empty if this target is illegal
     */
    private Optional<Pair<NodeResources, Double>> considerTarget(NodeResources resources, int targetCount) {
        if (targetCount < minimumNodesPerCluster || targetCount > maximumNodesPerCluster) return Optional.empty();

        NodeResources practicalResources = findPracticalResources(resources);
        if ( ! practicalResources.satisfies(resources)) return Optional.empty();


    }

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

        // TODO: Scaling window is cluster type dependent
        List<Measurement> measurements = metricsDb.getSince(nodeRepository().clock().instant().minus(scalingWindow),
                                                            resource.metric(),
                                                            applicationId,
                                                            cluster);
        measurements = keepFromNodes(clusterNodes, measurements); // Disregard nodes currently not in cluster
        if (measurements.size() < minimumMeasurements) return currentAllocation;
        // TODO: Return here if there was a change to the allocations in this cluster (or better: The entire app) in the last N seconds
        // TODO: Here we assume that enough measurements --> measurements will be across nodes. Is this sufficient?

        double averageLoad = average(measurements);
        double idealRatio = 1 + (averageLoad - resource.idealAverageLoad() / resource.idealAverageLoad());
        if (idealRatio > idealRatioLowWatermark && idealRatio < idealRatioHighWatermark) currentAllocation;
        return currentAllocation * idealRatio;
    }

    private Map<ApplicationId, List<Node>> nodesByApplication() {
        return nodeRepository().list().nodeType(NodeType.tenant).state(Node.State.active).asList()
                               .stream().collect(Collectors.groupingBy(n -> n.allocation().get().owner()));
    }

    private Map<ClusterSpec, List<Node>> nodesByCluster(List<Node> applicationNodes) {
        return applicationNodes.stream().collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster()));
    }

    private enum Resource {

        cpu {
            String metric() { return "cpu"; } // TODO: Full metric name
            double idealAverageLoad() { return 0.2; }
        },

        memory {
            String metric() { return "memory"; } // TODO: Full metric name
            double idealAverageLoad() { return 0.7; }
        },

        disk {
            String metric() { return "disk"; } // TODO: Full metric name
            double idealAverageLoad() { return 0.7; }
        };

        abstract String metric();
        abstract double idealAverageLoad();

    }

}
