// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.config.server.http.v2.MetricsRespone;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * @author olaa
 */
public class MetricsAggregator {

    private static final Logger logger = Logger.getLogger(MetricsAggregator.class.getName());
    HttpClient httpClient = HttpClientBuilder.create().build();

    public MetricsRespone aggregateAllMetrics(Map<ApplicationId, Map<String, List<URI>>> applicationHosts) {
        Map<ApplicationId, Map<String, Metrics>> aggregatedMetrics = applicationHosts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e-> aggregateMetricsByCluster(e.getValue())));
        return new MetricsRespone(200, aggregatedMetrics);
    }

    private Map<String, Metrics> aggregateMetricsByCluster(Map<String, List<URI>> clusterHosts) {
        logger.log(Level.WARNING, clusterHosts.keySet() +" ");
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
        logger.log(Level.WARNING, metrics + "");
        return Metrics.averagedMetrics(metrics);
    }

    private Metrics getMetrics(URI hostURI) {
        HttpGet get = new HttpGet(hostURI);
        try {
            HttpResponse response = httpClient.execute(get);

            InputStream is = response.getEntity().getContent();
            Slime slime = SlimeUtils.jsonToSlime(is.readAllBytes());
            is.close();

            Inspector metrics = slime.get().field("metrics");
            Instant timestamp = Instant.ofEpochSecond(slime.get().field("timestamp").asLong());
            double queriesPerSecond = metrics.field("queriesPerSecond").asDouble();
            double writesPerSecond = metrics.field("writesPerSecond").asDouble();
            double documentCount = metrics.field("documentCount").asDouble();
            double queryLatencyMillis = metrics.field("queryLatencyMillis").asDouble();
            double writeLatencyMills = metrics.field("writeLatencyMills").asDouble();
            return new Metrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis, writeLatencyMills);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
