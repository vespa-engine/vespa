// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * @author olaa
 */
public class MetricsAggregator {

    private static final Logger logger = Logger.getLogger(MetricsAggregator.class.getName());
    HttpClient httpClient = HttpClientBuilder.create().build();

    public MetricsResponse aggregateAllMetrics(Map<ApplicationId, Map<String, List<URI>>> applicationHosts) {
        Map<ApplicationId, Map<String, Metrics>> aggregatedMetrics = applicationHosts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e-> aggregateMetricsByCluster(e.getValue())));
        return new MetricsResponse(200, aggregatedMetrics);
    }

    private Map<String, Metrics> aggregateMetricsByCluster(Map<String, List<URI>> clusterHosts) {
        return clusterHosts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> aggregateMetrics(e.getValue())
                )
            );
    }

    private Metrics aggregateMetrics(List<URI> hosts) {
        List<Metrics> metrics= hosts.stream()
                .map(host -> getMetrics(host))
                .collect(Collectors.toList());
        Metrics.averagedMetrics(metrics);
        return Metrics.accumulatedMetrics(metrics);
    }

    private Metrics getMetrics(URI hostURI) {
        HttpGet get = new HttpGet(hostURI);
        try {
            HttpResponse response = httpClient.execute(get);

            InputStream is = response.getEntity().getContent();
            Slime slime = SlimeUtils.jsonToSlime(is.readAllBytes());
            is.close();

            Inspector nodeMetrics = slime.get().field("node");

            List<Metrics> metricsList = new ArrayList<>();
            Inspector services = slime.get().field("services");
            services.traverse((ArrayTraverser) (i, servicesInspector) -> {
                String serviceName = servicesInspector.field("name").asString();

                Instant timestamp = Instant.ofEpochSecond(servicesInspector.field("timestamp").asLong());
                Inspector serviceMetrics = servicesInspector.field("metrics");
                serviceMetrics.traverse((ArrayTraverser) (j, metrics) -> {
                    Inspector values = metrics.field("values");
                    double queryCount = values.field("queries.count").asDouble();
                    double queryLatency = values.field("query_latency.sum").asDouble();
                    double documentCount = values.field("document.count").asDouble();
                    double writeCount = values.field("write.count").asDouble();
                    double writeLatency = values.field("write_latency.sum").asDouble();
                    logger.log(Level.WARNING, writeLatency + " write latency");
                    Map<String, Double> map = new HashMap<>();
                    values.traverse((ObjectTraverser) (key, value) -> map.put(key, value.asDouble()));
                    metricsList.add(new Metrics(queryCount, writeCount, documentCount, queryLatency, writeLatency, timestamp));
                });

            });
            return Metrics.accumulatedMetrics(metricsList);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
