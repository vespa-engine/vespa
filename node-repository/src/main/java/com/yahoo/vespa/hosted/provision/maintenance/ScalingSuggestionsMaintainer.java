// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Applications;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Maintainer computing scaling suggestions for all clusters
 *
 * @author bratseth
 */
public class ScalingSuggestionsMaintainer extends NodeRepositoryMaintainer {

    private final Autoscaler autoscaler;

    public ScalingSuggestionsMaintainer(NodeRepository nodeRepository,
                                        MetricsDb metricsDb,
                                        Duration interval,
                                        Metric metric) {
        super(nodeRepository, interval, metric);
        this.autoscaler = new Autoscaler(metricsDb, nodeRepository);
    }

    @Override
    protected boolean maintain() {
        boolean success = true;
        if ( ! nodeRepository().zone().environment().isProduction()) return success;

        activeNodesByApplication().forEach((applicationId, nodes) -> suggest(applicationId, nodes));
        return success;
    }

    private void suggest(ApplicationId application, List<Node> applicationNodes) {
        nodesByCluster(applicationNodes).forEach((clusterId, clusterNodes) ->
                                                         suggest(application, clusterId, clusterNodes));
    }

    private Applications applications() {
        return nodeRepository().applications();
    }

    private void suggest(ApplicationId applicationId,
                         ClusterSpec.Id clusterId,
                         List<Node> clusterNodes) {
        Application application = applications().get(applicationId).orElse(new Application(applicationId));
        Optional<Cluster> cluster = application.cluster(clusterId);
        if (cluster.isEmpty()) return;
        Optional<ClusterResources> suggestion = autoscaler.suggest(cluster.get(), clusterNodes);
        // Wait only a short time for the lock to avoid interfering with change deployments
        try (Mutex lock = nodeRepository().lock(applicationId, Duration.ofSeconds(1))) {
            applications().get(applicationId).ifPresent(a -> storeSuggestion(suggestion, clusterId, a, lock));
        }
    }

    private void storeSuggestion(Optional<ClusterResources> suggestion,
                                 ClusterSpec.Id clusterId,
                                 Application application,
                                 Mutex lock) {
        Optional<Cluster> cluster = application.cluster(clusterId);
        if (cluster.isEmpty()) return;
        applications().put(application.with(cluster.get().withSuggested(suggestion)), lock);
    }

    private Map<ClusterSpec.Id, List<Node>> nodesByCluster(List<Node> applicationNodes) {
        return applicationNodes.stream().collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster().id()));
    }

}
