// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.config.server.http.v2.MetricsResponse;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * @author olaa
 */
public class MetricsRetriever {

    private static final Logger logger = Logger.getLogger(MetricsRetriever.class.getName());
    HttpClient httpClient = HttpClientBuilder.create().build();

    public MetricsResponse retrieveAllMetrics(Map<ApplicationId, Map<String, List<URI>>> applicationHosts) {
        Map<ApplicationId, Map<String, MetricsAggregator>> allMetrics = applicationHosts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> getMetricsByCluster(e.getValue())));
        return new MetricsResponse(200, allMetrics);
    }

    private Map<String, MetricsAggregator> getMetricsByCluster(Map<String, List<URI>> clusterHosts) {
        return clusterHosts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> getClusterMetrics(e.getValue())
                )
            );
    }

    private MetricsAggregator getClusterMetrics(List<URI> hosts) {
        MetricsAggregator clusterMetrics = new MetricsAggregator();
        hosts.stream()
            .forEach(host -> getHostMetrics(host, clusterMetrics));
        return clusterMetrics;
    }

    private void getHostMetrics(URI hostURI, MetricsAggregator metrics) {
            Slime responseBody = doMetricsRequest(hostURI);
            Inspector services = responseBody.get().field("services");
            services.traverse((ArrayTraverser) (i, servicesInspector) -> {
                parseService(servicesInspector, metrics);
            });
    }

    private Slime doMetricsRequest(URI hostURI) {
        HttpGet get = new HttpGet(hostURI);
        try {
            HttpResponse response = httpClient.execute(get);
            InputStream is = response.getEntity().getContent();
            Slime slime = SlimeUtils.jsonToSlime(is.readAllBytes());
            is.close();
            return slime;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void parseService(Inspector service, MetricsAggregator metrics) {
        String serviceName = service.field("name").asString();
        Instant timestamp = Instant.ofEpochSecond(service.field("timestamp").asLong());
        metrics.setTimestamp(timestamp);
        service.field("metrics").traverse((ArrayTraverser) (i, m) -> {
            Inspector values = m.field("values");
            switch (serviceName) {
                case "container":
                    metrics.addContainerQueryLatencyCount(values.field("query_latency.count").asDouble());
                    metrics.addContainerQueryLatencySum(values.field("query_latency.sum").asDouble());
                    metrics.addFeedLatencyCount(values.field("feed_latency.count").asDouble());
                    metrics.addFeedLatencySum(values.field("feed_latency.sum").asDouble());
                    break;
                case "qrserver":
                    metrics.addQrQueryLatencyCount(values.field("query_latency.count").asDouble());
                    metrics.addQrQueryLatencySum(values.field("query_latency.sum").asDouble());
                    break;
                case "distributor":
                    metrics.addDocumentCount(values.field("vds.distributor.docsstored.average").asDouble());
                    break;
            }
        });

    }

}
