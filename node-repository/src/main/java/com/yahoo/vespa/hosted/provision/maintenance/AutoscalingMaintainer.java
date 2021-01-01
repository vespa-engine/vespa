// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Applications;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.AllocatableClusterResources;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.MetricSnapshot;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.autoscale.NodeTimeseries;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.orchestrator.status.ApplicationLock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        if ( ! nodeRepository().isWorking()) return false;

        boolean success = true;
        if ( ! nodeRepository().zone().environment().isProduction()) return success;

        activeNodesByApplication().forEach((applicationId, nodes) -> autoscale(applicationId, nodes));
        return success;
    }

    private void autoscale(ApplicationId application, List<Node> applicationNodes) {
        try (MaintenanceDeployment deployment = new MaintenanceDeployment(application, deployer, metric, nodeRepository())) {
            if ( ! deployment.isValid()) return;
            nodesByCluster(applicationNodes).forEach((clusterId, clusterNodes) -> autoscale(application, clusterId, NodeList.copyOf(clusterNodes), deployment));
        }
    }

    private void autoscale(ApplicationId applicationId,
                           ClusterSpec.Id clusterId,
                           NodeList clusterNodes,
                           MaintenanceDeployment deployment) {
        Application application = nodeRepository().applications().get(applicationId).orElse(new Application(applicationId));
        if (application.cluster(clusterId).isEmpty()) return;
        Cluster cluster = application.cluster(clusterId).get();
        cluster = updateCompletion(cluster, clusterNodes);
        var advice = autoscaler.autoscale(cluster, clusterNodes);
        cluster = cluster.withAutoscalingStatus(advice.reason());

        if (advice.isPresent() && !cluster.targetResources().equals(advice.target())) { // autoscale
            cluster = cluster.withTarget(advice.target());
            applications().put(application.with(cluster), deployment.applicationLock().get());
            if (advice.target().isPresent()) {
                logAutoscaling(advice.target().get(), applicationId, cluster, clusterNodes);
                deployment.activate();
            }
        }
        else { // store cluster update
            applications().put(application.with(cluster), deployment.applicationLock().get());
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
        for (NodeTimeseries nodeTimeseries : metricsDb.getNodeTimeseries(event.at(), clusterNodes)) {
            Optional<MetricSnapshot> firstOnNewGeneration =
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
        ClusterResources current = new AllocatableClusterResources(clusterNodes.asList(), nodeRepository(), cluster.exclusive()).toAdvertisedClusterResources();
        log.info("Autoscaling " + application + " " + clusterNodes.clusterSpec() + ":" +
                 "\nfrom " + toString(current) + "\nto   " + toString(target));
    }

    static String toString(ClusterResources r) {
        return r + " (total: " + r.totalResources() + ")";
    }

    private Map<ClusterSpec.Id, List<Node>> nodesByCluster(List<Node> applicationNodes) {
        return applicationNodes.stream().collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster().id()));
    }

}
