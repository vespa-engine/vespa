// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.metrics.set;

import ai.vespa.metrics.ClusterControllerMetrics;
import ai.vespa.metrics.ContainerMetrics;
import ai.vespa.metrics.DistributorMetrics;
import ai.vespa.metrics.Suffix;
import ai.vespa.metrics.VespaMetrics;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static ai.vespa.metrics.Suffix.count;
import static ai.vespa.metrics.Suffix.last;
import static ai.vespa.metrics.Suffix.max;
import static ai.vespa.metrics.Suffix.sum;

/**
 * Service metrics fetched by ClusterDeploymentMetricsRetriever
 *
 * @author arnej
 */
public class ClusterDeploymentSet {

    public static final MetricSet clusterDeploymentMetricSet = createMetricSet();

    private static MetricSet createMetricSet() {
        return new MetricSet("cluster-deployment-metrics",
                             makeMetrics());
    }

    private static Set<Metric> makeMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();
        metrics.addAll(getContainerMetrics());
        metrics.addAll(getDistributorMetrics());
        metrics.addAll(getClusterControllerMetrics());
        return Collections.unmodifiableSet(metrics);
    }

    private static Set<Metric> getContainerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();
        addMetric(metrics, ContainerMetrics.FEED_LATENCY, EnumSet.of(sum, count));
        addMetric(metrics, ContainerMetrics.QUERY_LATENCY, EnumSet.of(sum, count));
        return metrics;
    }

    private static Set<Metric> getClusterControllerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_DISK_LIMIT, EnumSet.of(max, last));
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MAX_DISK_UTILIZATION, EnumSet.of(last, max));
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MAX_MEMORY_UTILIZATION, EnumSet.of(last, max));
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MEMORY_LIMIT, EnumSet.of(max, last));
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_NODES_ABOVE_LIMIT, EnumSet.of(last, max));
        return metrics;
    }

    private static Set<Metric> getDistributorMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_DOCSSTORED.average());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_GETS_LATENCY, EnumSet.of(sum, count));
        return metrics;
    }

    private static void addMetric(Set<Metric> metrics, String nameWithSuffix) {
        metrics.add(new Metric(nameWithSuffix));
    }

    private static void addMetric(Set<Metric> metrics, VespaMetrics metric, EnumSet<Suffix> suffixes) {
        suffixes.forEach(suffix -> addMetric(metrics, metric.baseName() + "." + suffix.suffix()));
    }

}
