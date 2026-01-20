// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.metrics.ClusterInfo;
import com.yahoo.vespa.config.server.metrics.DeploymentMetricsAggregator;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author olaa
 */
public class DeploymentMetricsResponseTest {

    @Test
    public void testMetricsResponseWithFeedBlocked() throws IOException {
        ApplicationId applicationId = ApplicationId.fromSerializedForm("tenant:app:instance");
        ClusterInfo contentCluster = new ClusterInfo("content_cluster", "content");

        DeploymentMetricsAggregator aggregator = new DeploymentMetricsAggregator()
                .addDocumentCount(1000.0)
                .addReadLatency(100.0, 10.0)
                .setFeedBlockedNodes(1);

        Map<ClusterInfo, DeploymentMetricsAggregator> metrics = Map.of(contentCluster, aggregator);
        DeploymentMetricsResponse response = new DeploymentMetricsResponse(applicationId, metrics);

        Inspector inspector = SlimeUtils.jsonToSlime(getRenderedString(response)).get();
        assertEquals("tenant:app:instance", inspector.field("applicationId").asString());

        Inspector clusters = inspector.field("clusters");
        assertEquals(1, clusters.entries());

        Inspector cluster = clusters.entry(0);
        assertEquals("content_cluster", cluster.field("clusterId").asString());
        assertEquals("content", cluster.field("clusterType").asString());

        Inspector clusterMetrics = cluster.field("metrics");
        assertEquals(1, clusterMetrics.field("feedBlockedNodes").asLong());
        assertEquals(1000.0, clusterMetrics.field("documentCount").asDouble(), 0.001);
    }

    @Test
    public void testMetricsResponseWithFeedNotBlocked() throws IOException {
        ApplicationId applicationId = ApplicationId.fromSerializedForm("tenant:app:instance");
        ClusterInfo contentCluster = new ClusterInfo("content_cluster", "content");

        DeploymentMetricsAggregator aggregator = new DeploymentMetricsAggregator()
                .addDocumentCount(1000.0)
                .setFeedBlockedNodes(0);

        Map<ClusterInfo, DeploymentMetricsAggregator> metrics = Map.of(contentCluster, aggregator);
        DeploymentMetricsResponse response = new DeploymentMetricsResponse(applicationId, metrics);

        Inspector inspector = SlimeUtils.jsonToSlime(getRenderedString(response)).get();
        Inspector clusterMetrics = inspector.field("clusters").entry(0).field("metrics");
        assertEquals(0, clusterMetrics.field("feedBlockedNodes").asLong());
    }

    private static String getRenderedString(DeploymentMetricsResponse response) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        return baos.toString(UTF_8);
    }
}
