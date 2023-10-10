// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics.docs;

import ai.vespa.metrics.ClusterControllerMetrics;
import ai.vespa.metrics.ConfigServerMetrics;
import ai.vespa.metrics.ContainerMetrics;
import ai.vespa.metrics.DistributorMetrics;
import ai.vespa.metrics.LogdMetrics;
import ai.vespa.metrics.NodeAdminMetrics;
import ai.vespa.metrics.SearchNodeMetrics;
import ai.vespa.metrics.SentinelMetrics;
import ai.vespa.metrics.SlobrokMetrics;
import ai.vespa.metrics.StorageMetrics;
import ai.vespa.metrics.Unit;
import ai.vespa.metrics.VespaMetrics;
import ai.vespa.metrics.set.DefaultMetrics;
import ai.vespa.metrics.set.MetricSet;
import ai.vespa.metrics.set.VespaMetricSet;

import java.util.Map;
import static ai.vespa.metrics.docs.MetricDocumentation.writeMetricDocumentation;
import static ai.vespa.metrics.docs.MetricSetDocumentation.writeMetricSetDocumentation;

/**
 * @author olaa
 *
 * Helper class to generate metric reference documentation for docs.vespa.ai
 */
public class DocumentationGenerator {

    public static void main(String[] args) {

        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: directory to write to");
        }
        var path = args[0];

        var metrics = getMetrics();
        metrics.forEach((metricType, metricArray) -> writeMetricDocumentation(path, metricArray, metricType));

        var metricSets = getMetricSets();
        metricSets.forEach((name, metricSet) -> writeMetricSetDocumentation(path, name, metricSet, metrics));

        UnitDocumentation.writeUnitDocumentation(path, Unit.values());
    }

    private static Map<String, VespaMetrics[]> getMetrics() {
        return Map.of(
                "Container", ContainerMetrics.values(),
                "SearchNode", SearchNodeMetrics.values(),
                "Storage", StorageMetrics.values(),
                "Distributor", DistributorMetrics.values(),
                "ConfigServer", ConfigServerMetrics.values(),
                "Logd", LogdMetrics.values(),
                "NodeAdmin", NodeAdminMetrics.values(),
                "Slobrok", SlobrokMetrics.values(),
                "Sentinel", SentinelMetrics.values(),
                "ClusterController", ClusterControllerMetrics.values()
        );
    }

    private static Map<String, MetricSet> getMetricSets() {
        return Map.of("Vespa", VespaMetricSet.vespaMetricSet,
                "Default", DefaultMetrics.defaultMetricSet);
    }
}
