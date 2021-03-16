// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Applications;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

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
        if ( ! nodeRepository().zone().environment().isProduction()) return true;

        int successes = 0;
        for (var application : activeNodesByApplication().entrySet())
            successes += suggest(application.getKey(), application.getValue());
        return successes > 0;
    }

    private int suggest(ApplicationId application, NodeList applicationNodes) {
        int successes = 0;
        for (var cluster : nodesByCluster(applicationNodes).entrySet())
            successes += suggest(application, cluster.getKey(), cluster.getValue()) ? 1 : 0;
        return successes;
    }

    private Applications applications() {
        return nodeRepository().applications();
    }

    private boolean suggest(ApplicationId applicationId,
                            ClusterSpec.Id clusterId,
                            NodeList clusterNodes) {
        Application application = applications().get(applicationId).orElse(Application.empty(applicationId));
        Optional<Cluster> cluster = application.cluster(clusterId);
        if (cluster.isEmpty()) return true;
        var suggestion = autoscaler.suggest(application, cluster.get(), clusterNodes);
        if (suggestion.isEmpty()) return true;
        // Wait only a short time for the lock to avoid interfering with change deployments
        try (Mutex lock = nodeRepository().nodes().lock(applicationId, Duration.ofSeconds(1))) {
            // empty suggested resources == keep the current allocation, so we record that
            var suggestedResources = suggestion.target().orElse(clusterNodes.not().retired().toResources());
            applications().get(applicationId).ifPresent(a -> updateSuggestion(suggestedResources, clusterId, a, lock));
            return true;
        }
        catch (ApplicationLockException e) {
            return false;
        }
    }

    private void updateSuggestion(ClusterResources suggestion,
                                  ClusterSpec.Id clusterId,
                                  Application application,
                                  Mutex lock) {
        Optional<Cluster> cluster = application.cluster(clusterId);
        if (cluster.isEmpty()) return;
        var at = nodeRepository().clock().instant();
        var currentSuggestion = cluster.get().suggestedResources();
        if (currentSuggestion.isEmpty()
            || currentSuggestion.get().at().isBefore(at.minus(Duration.ofDays(7)))
            || isHigher(suggestion, currentSuggestion.get().resources()))
            applications().put(application.with(cluster.get().withSuggested(Optional.of(new Cluster.Suggestion(suggestion,  at)))), lock);
    }

    private boolean isHigher(ClusterResources r1, ClusterResources r2) {
        // Use cost as a measure of "highness" over multiple dimensions
        return r1.totalResources().cost() > r2.totalResources().cost();
    }

    private Map<ClusterSpec.Id, NodeList> nodesByCluster(NodeList applicationNodes) {
        return applicationNodes.groupingBy(n -> n.allocation().get().membership().cluster().id());
    }

}
