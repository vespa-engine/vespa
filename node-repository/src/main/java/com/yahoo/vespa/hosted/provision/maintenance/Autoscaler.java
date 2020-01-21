// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.NodeMetricsDb;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maintainer making automatic scaling decisions
 *
 * @author bratseth
 */
public class Autoscaler extends Maintainer {

    private final NodeMetricsDb metricsDb;

    private

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
        nodesByCluster(applicationNodes).forEach((clusterSpec, clusterNodes) -> autoscale(applicationId, clusterSpec, clusterNodes));
    }

    private void autoscale(ApplicationId applicationId, ClusterSpec cluster, List<Node> clusterNodes) {
        Set.of("cpu", "memory", "disk").forEach(metric -> autoscale(metric, applicationId, cluster, clusterNodes)); // TODO: Full metric names
    }

    private void autoscale(String resourceMetric, ApplicationId applicationId, ClusterSpec cluster, List<Node> clusterNodes) {
        List<Measurement> measurements = metricsDb.getSince(nodeRepository().clock().instant().minus(scalingWindow),
                                                            resourceMetric,
                                                            applicationId,
                                                            cluster);
        measurements = keepNodes(measurements, clusterNodes); // Disregard nodes currently not in cluster
        if (measurements.size() < minimumMeasurements) return;
        // TODO: Returns if there was a change to the allocations in this cluster (or better: The entire app) in the last N seconds

        // TODO: Here we assume that enough measurements == measurements will be across nodes. Is this sufficient?

        if (tooLittle(resourceMetric, measurements))
            log.info("Should increase " + resourceMetric + " in " + cluster + " in " + applicationId);
        else if (tooMuch(resourceMetric, measurements))
            log.info("Should decrease " + resourceMetric + " in " + cluster + " in " + applicationId);
    }

    private Map<ApplicationId, List<Node>> nodesByApplication() {
        return nodeRepository().list().nodeType(NodeType.tenant).state(Node.State.active).asList()
                               .stream().collect(Collectors.groupingBy(n -> n.allocation().get().owner()));
    }

    private Map<ClusterSpec, List<Node>> nodesByCluster(List<Node> applicationNodes) {
        return applicationNodes.stream().collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster()));
    }

}
