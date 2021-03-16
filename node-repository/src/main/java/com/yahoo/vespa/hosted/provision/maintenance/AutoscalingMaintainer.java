// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Applications;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.AllocatableClusterResources;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;
import com.yahoo.vespa.hosted.provision.autoscale.NodeTimeseries;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Maintainer making automatic scaling decisions
 *
 * @author bratseth
 */
public class AutoscalingMaintainer extends NodeRepositoryMaintainer {

    private final Autoscaler autoscaler;
    private final MetricsDb metricsDb;
    private final Deployer deployer;
    private final Metric metric;

    public AutoscalingMaintainer(NodeRepository nodeRepository,
                                 MetricsDb metricsDb,
                                 Deployer deployer,
                                 Metric metric,
                                 Duration interval) {
        super(nodeRepository, interval, metric);
        this.autoscaler = new Autoscaler(metricsDb, nodeRepository);
        this.metricsDb = metricsDb;
        this.deployer = deployer;
        this.metric = metric;
    }

    @Override
    protected boolean maintain() {
        if ( ! nodeRepository().nodes().isWorking()) return false;

        boolean success = true;
        if ( ! nodeRepository().zone().environment().isProduction()) return success;

        activeNodesByApplication().forEach(this::autoscale);
        return success;
    }

    private void autoscale(ApplicationId application, NodeList applicationNodes) {
        nodesByCluster(applicationNodes).forEach((clusterId, clusterNodes) -> autoscale(application, clusterId, clusterNodes));
    }

    private void autoscale(ApplicationId applicationId, ClusterSpec.Id clusterId, NodeList clusterNodes) {
        Optional<Application> application = nodeRepository().applications().get(applicationId);
        if (application.isEmpty()) return;
        Optional<Cluster> cluster = application.get().cluster(clusterId);
        if (cluster.isEmpty()) return;

        Cluster updatedCluster = updateCompletion(cluster.get(), clusterNodes);
        var advice = autoscaler.autoscale(application.get(), updatedCluster, clusterNodes);

        // Lock and write if there are state updates and/or we should autoscale now
        if (advice.isPresent() && !cluster.get().targetResources().equals(advice.target()) ||
            (updatedCluster != cluster.get() || !advice.reason().equals(cluster.get().autoscalingStatus()))) {
            try (var lock = nodeRepository().nodes().lock(applicationId)) {
                application = nodeRepository().applications().get(applicationId);
                if (application.isEmpty()) return;
                cluster = application.get().cluster(clusterId);
                if (cluster.isEmpty()) return;

                // 1. Update cluster info
                updatedCluster = updateCompletion(cluster.get(), clusterNodes)
                                         .withAutoscalingStatus(advice.reason())
                                         .withTarget(advice.target());
                applications().put(application.get().with(updatedCluster), lock);
                if (advice.isPresent() && advice.target().isPresent() && !cluster.get().targetResources().equals(advice.target())) {
                    // 2. Also autoscale
                    logAutoscaling(advice.target().get(), applicationId, updatedCluster, clusterNodes);
                    try (MaintenanceDeployment deployment = new MaintenanceDeployment(applicationId, deployer, metric, nodeRepository())) {
                        if (deployment.isValid())
                            deployment.activate();
                    }
                }
            }
        }
    }

    private Applications applications() {
        return nodeRepository().applications();
    }

    /** Check if the last scaling event for this cluster has completed and if so record it in the returned instance */
    private Cluster updateCompletion(Cluster cluster, NodeList clusterNodes) {
        if (cluster.lastScalingEvent().isEmpty()) return cluster;
        var event = cluster.lastScalingEvent().get();
        if (event.completion().isPresent()) return cluster;

        // Scaling event is complete if:
        // - 1. no nodes which was retired by this are still present (which also implies data distribution is complete)
        if (clusterNodes.retired().stream()
                        .anyMatch(node -> node.history().hasEventAt(History.Event.Type.retired, event.at())))
            return cluster;
        // - 2. all nodes have switched to the right config generation
        for (NodeTimeseries nodeTimeseries : metricsDb.getNodeTimeseries(Duration.between(event.at(), clock().instant()),
                                                                         clusterNodes)) {
            Optional<NodeMetricSnapshot> firstOnNewGeneration =
                    nodeTimeseries.asList().stream()
                                           .filter(snapshot -> snapshot.generation() >= event.generation()).findFirst();
            if (firstOnNewGeneration.isEmpty()) return cluster; // Not completed
        }


        // Set the completion time to the instant we notice completion.
        Instant completionTime = nodeRepository().clock().instant();
        return cluster.with(event.withCompletion(completionTime));
    }

    private void logAutoscaling(ClusterResources target,
                                ApplicationId application,
                                Cluster cluster,
                                NodeList clusterNodes) {
        ClusterResources current = new AllocatableClusterResources(clusterNodes.asList(), nodeRepository(), cluster.exclusive()).advertisedResources();
        log.info("Autoscaling " + application + " " + clusterNodes.clusterSpec() + ":" +
                 "\nfrom " + toString(current) + "\nto   " + toString(target));
    }

    static String toString(ClusterResources r) {
        return r + " (total: " + r.totalResources() + ")";
    }

    private Map<ClusterSpec.Id, NodeList> nodesByCluster(NodeList applicationNodes) {
        return applicationNodes.groupingBy(n -> n.allocation().get().membership().cluster().id());
    }

}
