// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.metrics.SearchNodeMetricsAggregator;
import java.util.Map;

/**
 * @author akvalsvik
 */
public class SearchNodeMetricsResponse extends SlimeJsonResponse {

    public SearchNodeMetricsResponse(ApplicationId applicationId, Map<String, SearchNodeMetricsAggregator> aggregatedMetrics) {
        Cursor application = slime.setObject();
        application.setString("applicationId", applicationId.serializedForm());

        Cursor clusters = application.setArray("clusters");

        for (var entry : aggregatedMetrics.entrySet()) {
            Cursor cluster = clusters.addObject();
            cluster.setString("clusterId", entry.getKey());

            SearchNodeMetricsAggregator aggregator = entry.getValue();
            Cursor metrics = cluster.setObject("metrics");
            metrics.setDouble("documentsActiveCount", aggregator.aggregateDocumentActiveCount());
            metrics.setDouble("documentsReadyCount", aggregator.aggregateDocumentReadyCount());
            metrics.setDouble("documentsTotalCount", aggregator.aggregateDocumentTotalCount());
            metrics.setDouble("documentDiskUsage", aggregator.aggregateDocumentDiskUsage());
            metrics.setDouble("resourceDiskUsageAverage", aggregator.aggregateResourceDiskUsageAverage());
            metrics.setDouble("resourceMemoryUsageAverage", aggregator.aggregateResourceMemoryUsageAverage());
        }
    }
}
