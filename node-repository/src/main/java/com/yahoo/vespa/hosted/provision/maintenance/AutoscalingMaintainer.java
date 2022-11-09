// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Environment;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Applications;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.AllocatableClusterResources;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;
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
    private final Deployer deployer;
    private final Metric metric;

    public AutoscalingMaintainer(NodeRepository nodeRepository,
                                 Deployer deployer,
                                 Metric metric,
                                 Duration interval) {
        super(nodeRepository, interval, metric);
        this.autoscaler = new Autoscaler(nodeRepository);
        this.deployer = deployer;
        this.metric = metric;
    }

    @Override
    protected double maintain() {
        if ( ! nodeRepository().nodes().isWorking()) return 0.0;

        if ( ! nodeRepository().zone().environment().isAnyOf(Environment.dev, Environment.perf, Environment.prod)) return 1.0;

        activeNodesByApplication().forEach(this::autoscale);
        return 1.0;
    }

    private void autoscale(ApplicationId application, NodeList applicationNodes) {
        try {
            nodesByCluster(applicationNodes).forEach((clusterId, clusterNodes) -> autoscale(application, clusterId, clusterNodes));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Illegal arguments for " + application, e);
        }
    }

    private void autoscale(ApplicationId applicationId, ClusterSpec.Id clusterId, NodeList clusterNodes) {
        Optional<Application> application = nodeRepository().applications().get(applicationId);
        if (application.isEmpty()) return;
        Optional<Cluster> cluster = application.get().cluster(clusterId);
        if (cluster.isEmpty()) return;

        Cluster updatedCluster = updateCompletion(cluster.get(), clusterNodes);
        var advice = autoscaler.autoscale(application.get(), updatedCluster, clusterNodes);

        if ( ! anyChanges(advice, cluster.get(), updatedCluster, clusterNodes)) return;

        try (var lock = nodeRepository().applications().lock(applicationId)) {
            application = nodeRepository().applications().get(applicationId);
            if (application.isEmpty()) return;
            cluster = application.get().cluster(clusterId);
            if (cluster.isEmpty()) return;
            clusterNodes = nodeRepository().nodes().list(Node.State.active).owner(applicationId).cluster(clusterId);

            // 1. Update cluster info
            updatedCluster = updateCompletion(cluster.get(), clusterNodes)
                                     .with(advice.reason())
                                     .withTarget(advice.target());
            applications().put(application.get().with(updatedCluster), lock);

            var current = new AllocatableClusterResources(clusterNodes, nodeRepository()).advertisedResources();
            if (advice.isPresent() && advice.target().isPresent() && !current.equals(advice.target().get())) {
                // 2. Also autoscale
                try (MaintenanceDeployment deployment = new MaintenanceDeployment(applicationId, deployer, metric, nodeRepository())) {
                    if (deployment.isValid()) {
                        deployment.activate();
                        logAutoscaling(current, advice.target().get(), applicationId, clusterNodes);
                    }
                }
            }
        }
    }

    private boolean anyChanges(Autoscaler.Advice advice, Cluster cluster, Cluster updatedCluster, NodeList clusterNodes) {
        if (advice.isPresent() && !cluster.targetResources().equals(advice.target())) return true;
        if (updatedCluster != cluster) return true;
        if ( ! advice.reason().equals(cluster.autoscalingStatus())) return true;
        if (advice.target().isPresent() &&
            !advice.target().get().equals(new AllocatableClusterResources(clusterNodes, nodeRepository()).advertisedResources())) return true;
        return false;
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
        // - 2. all nodes have switched to the right config generation (currently only measured on containers)
        for (var nodeTimeseries : nodeRepository().metricsDb().getNodeTimeseries(Duration.between(event.at(), clock().instant()),
                                                                                 clusterNodes)) {
            Optional<NodeMetricSnapshot> onNewGeneration =
                    nodeTimeseries.asList().stream()
                                  .filter(snapshot -> snapshot.generation() >= event.generation()).findAny();
            if (onNewGeneration.isEmpty()) return cluster; // Not completed
        }

        // Set the completion time to the instant we notice completion.
        Instant completionTime = nodeRepository().clock().instant();
        return cluster.with(event.withCompletion(completionTime));
    }

    private void logAutoscaling(ClusterResources from, ClusterResources to, ApplicationId application, NodeList clusterNodes) {
        log.info("Autoscaled " + application + " " + clusterNodes.clusterSpec() + ":" +
                 "\nfrom " + toString(from) + "\nto   " + toString(to));
    }

    static String toString(ClusterResources r) {
        return r + " (total: " + r.totalResources() + ")";
    }

    private Map<ClusterSpec.Id, NodeList> nodesByCluster(NodeList applicationNodes) {
        return applicationNodes.groupingBy(n -> n.allocation().get().membership().cluster().id());
    }

}
