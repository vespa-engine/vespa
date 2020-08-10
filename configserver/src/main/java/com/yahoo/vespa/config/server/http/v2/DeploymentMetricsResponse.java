// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.metrics.ClusterInfo;
import com.yahoo.vespa.config.server.metrics.DeploymentMetricsAggregator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * @author olaa
 */
public class DeploymentMetricsResponse extends HttpResponse {

    private final Slime slime = new Slime();

    public DeploymentMetricsResponse(int status, ApplicationId applicationId, Map<ClusterInfo, DeploymentMetricsAggregator> aggregatedMetrics) {
        super(status);

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
        }
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        new JsonFormat(false).encode(outputStream, slime);
    }

    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }
}
