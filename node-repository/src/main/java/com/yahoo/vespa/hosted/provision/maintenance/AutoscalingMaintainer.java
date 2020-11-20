// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Applications;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.AllocatableClusterResources;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
    private final Deployer deployer;
    private final Metric metric;

    public AutoscalingMaintainer(NodeRepository nodeRepository,
                                 MetricsDb metricsDb,
                                 Deployer deployer,
                                 Metric metric,
                                 Duration interval) {
        super(nodeRepository, interval, metric);
        this.autoscaler = new Autoscaler(metricsDb, nodeRepository);
        this.metric = metric;
        this.deployer = deployer;
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
            nodesByCluster(applicationNodes).forEach((clusterId, clusterNodes) -> autoscale(application, clusterId, clusterNodes, deployment));
        }
    }

    private void autoscale(ApplicationId applicationId,
                           ClusterSpec.Id clusterId,
                           List<Node> clusterNodes,
                           MaintenanceDeployment deployment) {
        Application application = nodeRepository().applications().get(applicationId).orElse(new Application(applicationId));
        Optional<Cluster> cluster = application.cluster(clusterId);
        if (cluster.isEmpty()) return;

        log.fine(() -> "Autoscale " + application.toString());

        var advice = autoscaler.autoscale(cluster.get(), clusterNodes);

        if (advice.isEmpty()) return;

        if ( ! cluster.get().targetResources().equals(advice.target())) {
            applications().put(application.with(cluster.get().withTarget(advice.target())), deployment.applicationLock().get());
            if (advice.target().isPresent()) {
                logAutoscaling(advice.target().get(), applicationId, cluster.get(), clusterNodes);
                deployment.activate();
            }
        }
    }

    private Applications applications() {
        return nodeRepository().applications();
    }

    private void logAutoscaling(ClusterResources target,
                                ApplicationId application,
                                Cluster cluster,
                                List<Node> clusterNodes) {
        ClusterResources current = new AllocatableClusterResources(clusterNodes, nodeRepository(), cluster.exclusive()).toAdvertisedClusterResources();
        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();
        log.info("Autoscaling " + application + " " + clusterType + " " + cluster.id() + ":" +
                 "\nfrom " + toString(current) + "\nto   " + toString(target));
    }

    static String toString(ClusterResources r) {
        return r + " (total: " + r.totalResources() + ")";
    }

    private Map<ClusterSpec.Id, List<Node>> nodesByCluster(List<Node> applicationNodes) {
        return applicationNodes.stream().collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster().id()));
    }

}
