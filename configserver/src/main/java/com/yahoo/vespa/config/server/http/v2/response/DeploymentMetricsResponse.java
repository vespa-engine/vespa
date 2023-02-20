// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.metrics.ClusterInfo;
import com.yahoo.vespa.config.server.metrics.DeploymentMetricsAggregator;

import java.util.Map;

/**
 * @author olaa
 */
public class DeploymentMetricsResponse extends SlimeJsonResponse {

    public DeploymentMetricsResponse(ApplicationId applicationId, Map<ClusterInfo, DeploymentMetricsAggregator> aggregatedMetrics) {
        Cursor application = slime.setObject();
        application.setString("applicationId", applicationId.serializedForm());

        Cursor clusters = application.setArray("clusters");

        for (var entry : aggregatedMetrics.entrySet()) {
            Cursor cluster = clusters.addObject();
            cluster.setString("clusterId", entry.getKey().getClusterId());
            cluster.setString("clusterType", entry.getKey().getClusterType());

            DeploymentMetricsAggregator aggregator = entry.getValue();
            Cursor metrics = cluster.setObject("metrics");
            aggregator.aggregateQueryRate().ifPresent(queryRate -> metrics.setDouble("queriesPerSecond", queryRate));
            aggregator.aggregateFeedRate().ifPresent(feedRate -> metrics.setDouble("feedPerSecond", feedRate));
            aggregator.aggregateDocumentCount().ifPresent(documentCount -> metrics.setDouble("documentCount", documentCount));
            aggregator.aggregateQueryLatency().ifPresent(queryLatency -> metrics.setDouble("queryLatency",queryLatency));
            aggregator.aggregateFeedLatency().ifPresent(feedLatency -> metrics.setDouble("feedLatency", feedLatency));

            aggregator.memoryUsage().ifPresent(memory -> {
                metrics.setDouble("memoryUtil", memory.util());
                metrics.setDouble("memoryFeedBlockLimit", memory.feedBlockLimit());
            });
            aggregator.diskUsage().ifPresent(disk -> {
                metrics.setDouble("diskUtil", disk.util());
                metrics.setDouble("diskFeedBlockLimit", disk.feedBlockLimit());
            });
        }
    }
}
