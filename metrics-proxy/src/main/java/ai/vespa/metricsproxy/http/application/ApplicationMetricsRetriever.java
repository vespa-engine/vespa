/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.util.http.VespaHttpClientBuilder;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.time.Clock;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;

/**
 * This class retrieves metrics from all nodes in the given config, usually all
 * nodes in a Vespa application.
 *
 * @author gjoranv
 */
public class ApplicationMetricsRetriever extends AbstractComponent {
    private static final Logger log = Logger.getLogger(ApplicationMetricsRetriever.class.getName());

    private static final int PARALLELISM = 20;
    static final Duration MIN_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(240);

    private static final int HTTP_CONNECT_TIMEOUT = 5000;
    private static final int HTTP_SOCKET_TIMEOUT = 30000;

    private final HttpClient httpClient = createHttpClient();
    private final List<NodeMetricsClient> clients;

    // Non-final for testing
    private Duration taskTimeout;

    private ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM);

    @Inject
    public ApplicationMetricsRetriever(VespaNodesConfig nodesConfig) {
        clients = createNodeClients(nodesConfig);
        taskTimeout = timeout(clients.size());
    }

    @Override
    public void deconstruct() {
        forkJoinPool.shutdownNow();
        super.deconstruct();
    }

    public Map<Node, List<MetricsPacket.Builder>> getMetrics() {
        log.info(() -> "Retrieving metrics from " + clients.size() + " nodes.");
        var forkJoinTask = forkJoinPool.submit(() -> clients.parallelStream()
                .map(this::getNodeMetrics)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        try {
            var metricsByNode = forkJoinTask.get(taskTimeout.toMillis(), TimeUnit.MILLISECONDS);

            log.info(() -> "Successfully retrieved metrics from " + clients.size() + " nodes.");
            return metricsByNode;

        } catch (Exception e) {
            // Since the task is a ForkJoinTask, we don't need special handling of InterruptedException
            forkJoinPool.shutdownNow();
            forkJoinPool = new ForkJoinPool(PARALLELISM);
            throw new ApplicationMetricsException("Failed retrieving metrics.", e);
        }
    }

    private Map.Entry<Node, List<MetricsPacket.Builder>> getNodeMetrics(NodeMetricsClient client) {
        try {
            return new AbstractMap.SimpleEntry<>(client.node, client.getMetrics());
        } catch (Exception e) {
            log.warning("Could not retrieve metrics from " + client.node.metricsUri);
        }
        return new AbstractMap.SimpleEntry<>(client.node, emptyList());
    }

    private List<NodeMetricsClient> createNodeClients(VespaNodesConfig nodesConfig) {
        var clients = new ArrayList<NodeMetricsClient>();
        for (var nc : nodesConfig.node()) {
            var node = new Node(nc.configId(), nc.hostname(), nc.port(), nc.path());
            var client = new NodeMetricsClient(httpClient, node, Clock.systemUTC());
            clients.add(client);
        }
        return clients;
    }

    private static CloseableHttpClient createHttpClient() {
        return VespaHttpClientBuilder.create(PoolingHttpClientConnectionManager::new)
                .setUserAgent("application-metrics-retriever")
                .setDefaultRequestConfig(RequestConfig.custom()
                                                 .setConnectTimeout(HTTP_CONNECT_TIMEOUT)
                                                 .setSocketTimeout(HTTP_SOCKET_TIMEOUT)
                                                 .build())
                .build();
    }

    private static Duration timeout(int clients) {
        Duration timeout = Duration.ofSeconds(Long.max(MIN_TIMEOUT.toSeconds(), 20 * clients / PARALLELISM));
        return timeout.compareTo(MAX_TIMEOUT) > 0 ? MAX_TIMEOUT : timeout;
    }

    // For testing only!
    void setTaskTimeout(Duration taskTimeout) {
        this.taskTimeout = taskTimeout;
    }

}
