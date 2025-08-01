// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import ai.vespa.metrics.ConfigServerMetrics;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Dimension;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Applications;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.AllocatableResources;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling;
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
    private final BooleanFlag enabledFlag;
    private final BooleanFlag enableDetailedLoggingFlag;

    public AutoscalingMaintainer(NodeRepository nodeRepository,
                                 Deployer deployer,
                                 Metric metric,
                                 Duration interval) {
        super(nodeRepository, interval, metric);
        this.autoscaler = new Autoscaler(nodeRepository);
        this.deployer = deployer;
        this.metric = metric;
        this.enabledFlag = PermanentFlags.AUTOSCALING.bindTo(nodeRepository.flagSource());
        this.enableDetailedLoggingFlag = PermanentFlags.AUTOSCALING_DETAILED_LOGGING.bindTo(nodeRepository.flagSource());
    }

    @Override
    protected double maintain() {
        if ( ! nodeRepository().nodes().isWorking()) return 0.0;
        if (nodeRepository().zone().environment().isTest()) return 1.0;

        int attempts = 0;
        int failures = 0;
        outer:
        for (var applicationNodes : activeNodesByApplication().entrySet()) {
            for (var clusterNodes : nodesByCluster(applicationNodes.getValue()).entrySet()) {
                if (shuttingDown()) break outer;
                attempts++;
                if ( ! autoscale(applicationNodes.getKey(), clusterNodes.getKey()))
                    failures++;
            }
        }
        return asSuccessFactorDeviation(attempts, failures);
    }

    /**
     * Autoscales the given cluster.
     *
     * @return true if an autoscaling decision was made or nothing should be done, false if there was an error
     */
    private boolean autoscale(ApplicationId applicationId, ClusterSpec.Id clusterId) {
        boolean redeploy = false;
        boolean enabled = enabledFlag.with(Dimension.INSTANCE_ID, applicationId.serializedForm()).value();
        boolean logDetails = enableDetailedLoggingFlag.with(Dimension.INSTANCE_ID, applicationId.serializedForm()).value();
        try (var lock = nodeRepository().applications().lock(applicationId)) {
            Optional<Application> application = nodeRepository().applications().get(applicationId);
            if (application.isEmpty()) return true;
            if (application.get().cluster(clusterId).isEmpty()) return true;
            Cluster cluster = application.get().cluster(clusterId).get();
            Cluster unchangedCluster = cluster;

            NodeList clusterNodes = nodeRepository().nodes().list(Node.State.active).owner(applicationId).cluster(clusterId);
            if (clusterNodes.isEmpty()) return true; // Cluster was removed since we started
            cluster = updateCompletion(cluster, clusterNodes);

            var current = new AllocatableResources(clusterNodes.not().retired(), nodeRepository()).advertisedResources();

            // Autoscale unless an autoscaling is already in progress
            Autoscaling autoscaling = null;
            if (cluster.target().resources().isEmpty() && !cluster.scalingInProgress()) {
                autoscaling = autoscaler.autoscale(application.get(), cluster, clusterNodes, enabled, logDetails);
                if (autoscaling.isPresent() || cluster.target().isEmpty()) // Ignore empty from recently started servers
                    cluster = cluster.withTarget(autoscaling);
            }

            // Always store any updates
            if (cluster != unchangedCluster)
                applications().put(application.get().with(cluster), lock);

            // Attempt to perform the autoscaling immediately, and log it regardless
            if (autoscaling != null && autoscaling.resources().isPresent() && !current.equals(autoscaling.resources().get())) {
                redeploy = true;
                logAutoscaling(current, autoscaling.resources().get(), applicationId, clusterNodes.not().retired());
                if (logDetails) {
                    log.info("Autoscaling data for " + applicationId.toFullString() + ", clusterId " + clusterId.value() + ":"
                            + "\n\tmetrics().cpuCostPerQuery(): " + autoscaling.metrics().cpuCostPerQuery()
                            + "\n\tmetrics().queryRate(): " + autoscaling.metrics().queryRate()
                            + "\n\tmetrics().growthRateHeadroom(): " + autoscaling.metrics().growthRateHeadroom()
                            + "\n\tpeak(): " + autoscaling.peak().toString()
                            + "\n\tideal(): " + autoscaling.ideal().toString());
                }
            }
        }
        catch (ApplicationLockException e) {
            return false;
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Illegal arguments for " + applicationId + " cluster " + clusterId, e);
        }
        if (redeploy) {
            try (MaintenanceDeployment deployment = new MaintenanceDeployment(applicationId, deployer, metric, nodeRepository())) {
                if (deployment.isValid())
                    deployment.activate();
            }
        }
        return true;
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
        log.info("Autoscaling " + application + " " + clusterNodes.clusterSpec() + ":" +
                 "\nfrom " + toString(from) + "\nto   " + toString(to));
        metric.add(ConfigServerMetrics.CLUSTER_AUTOSCALED.baseName(), 1,
                   metric.createContext(dimensions(application, clusterNodes.clusterSpec())));
    }

    private static Map<String, String> dimensions(ApplicationId application, ClusterSpec clusterSpec) {
        return Map.of("tenantName", application.tenant().value(),
                      "applicationId", application.serializedForm().replace(':', '.'),
                      "app", application.application().value() + "." + application.instance().value(),
                      "clusterid", clusterSpec.id().value(),
                      "clustertype", clusterSpec.type().name());
    }

    static String toString(ClusterResources r) {
        return r + " (total: " + r.totalResources() + ")";
    }

    private Map<ClusterSpec.Id, NodeList> nodesByCluster(NodeList applicationNodes) {
        return applicationNodes.groupingBy(n -> n.allocation().get().membership().cluster().id());
    }

}
