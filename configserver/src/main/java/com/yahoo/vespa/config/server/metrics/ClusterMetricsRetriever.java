// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.http.v2.MetricsResponse;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * @author olaa
 *
 * Retrieves metrics for given application, grouped by cluster
 */
public class ClusterMetricsRetriever {

    public static MetricsResponse getMetrics(Application application) {
        var clusters = getClustersOfApplication(application);
        var clusterMetrics = new ConcurrentHashMap<ClusterInfo, MetricsAggregator>();

        Runnable retrieveMetricsJob = () ->
            clusters.parallelStream().forEach(cluster -> {
                MetricsAggregator metrics = MetricsRetriever.requestMetricsForCluster(cluster);
                clusterMetrics.put(cluster, metrics);
            });

        ForkJoinPool threadPool = new ForkJoinPool(5);
        threadPool.submit(retrieveMetricsJob);
        threadPool.shutdown();

        try {
            threadPool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return new MetricsResponse(200, application.getId(), clusterMetrics);
    }

    /** Finds the hosts of an application, grouped by cluster name */
    private static Collection<ClusterInfo> getClustersOfApplication(Application application) {
        Map<String, ClusterInfo> clusters = new HashMap<>();

        application.getModel().getHosts().stream()
                .filter(host -> host.getServices().stream().noneMatch(isLogserver()))
                .forEach(hostInfo -> {
                            ClusterInfo clusterInfo = createClusterInfo(hostInfo);
                            URI metricsProxyURI = createMetricsProxyURI(hostInfo.getHostname());
                            clusters.computeIfAbsent(clusterInfo.getClusterId(), c -> clusterInfo).addHost(metricsProxyURI);
                        }
                );
        return clusters.values();

    }

    private static Predicate<ServiceInfo> isLogserver() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver");
    }

    private static URI createMetricsProxyURI(String hostname) {
        return URI.create("http://" + hostname + ":" + MetricsProxyContainer.BASEPORT + "/metrics/v1/values?consumer=Vespa");
    }

    private static ClusterInfo createClusterInfo(HostInfo hostInfo) {
        return hostInfo.getServices().stream()
                .map(ClusterInfo::fromServiceInfo)
                .filter(Optional::isPresent)
                .findFirst().get().orElseThrow();
    }
}
