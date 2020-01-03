/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil;
import com.yahoo.yolean.Exceptions;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import static ai.vespa.metricsproxy.http.ValuesFetcher.DEFAULT_PUBLIC_CONSUMER_ID;
import static com.yahoo.log.LogLevel.DEBUG;
import static java.util.Collections.emptyList;

/**
 * This class is used to retrieve metrics from a single Vespa node over http.
 * Keeps and serves a snapshot of the node's metrics, with a fixed TTL, to
 * avoid unnecessary load on metrics proxies.
 *
 * @author gjoranv
 */
public class NodeMetricsClient {
    private static final Logger log = Logger.getLogger(NodeMetricsClient.class.getName());

    static final Duration METRICS_TTL = Duration.ofSeconds(30);

    final Node node;
    private final HttpClient httpClient;
    private final Clock clock;

    private List<MetricsPacket.Builder> metrics = emptyList();
    private Instant metricsTimestamp = Instant.EPOCH;
    private long snapshotsRetrieved = 0;

    public NodeMetricsClient(HttpClient httpClient, Node node, Clock clock) {
        this.httpClient = httpClient;
        this.node = node;
        this.clock = clock;
    }

    public List<MetricsPacket.Builder> getMetrics() {
        return getMetrics(DEFAULT_PUBLIC_CONSUMER_ID);
    }

    public List<MetricsPacket.Builder> getMetrics(ConsumerId consumer) {
        if (Instant.now(clock).isAfter(metricsTimestamp.plus(METRICS_TTL))) {
            retrieveMetrics(consumer);
        }
        return metrics;
    }

    private void retrieveMetrics(ConsumerId consumer) {
        String metricsUri = node.metricsUri(consumer).toString();
        log.log(DEBUG, () -> "Retrieving metrics from host " + metricsUri);

        try {
            String metricsJson = httpClient.execute(new HttpGet(metricsUri), new BasicResponseHandler());
            metrics = GenericJsonUtil.toMetricsPackets(metricsJson);
            metricsTimestamp = Instant.now(clock);
            snapshotsRetrieved ++;
            log.log(DEBUG, () -> "Successfully retrieved " + metrics.size() + " metrics packets from " + metricsUri);

        } catch (IOException e) {
            log.warning("Unable to retrieve metrics from " + metricsUri + ": " + Exceptions.toMessageString(e));
            metrics = emptyList();
        }
    }

    long snapshotsRetrieved() {
        return snapshotsRetrieved;
    }

}
