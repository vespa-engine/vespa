// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.util.http.hc4.VespaHttpClientBuilder;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import java.util.logging.Level;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.time.Clock;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.http.ValuesFetcher.defaultMetricsConsumerId;
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

    static final int MAX_THREADS = 20;
    static final Duration MIN_TIMEOUT = Duration.ofSeconds(60);
    static final Duration MAX_TIMEOUT = Duration.ofSeconds(240);

    private static final int HTTP_CONNECT_TIMEOUT = 5000;
    private static final int HTTP_SOCKET_TIMEOUT = 30000;

    private final HttpClient httpClient = createHttpClient();
    private final List<NodeMetricsClient> clients;
    private final ForkJoinPool forkJoinPool;

    // Non-final for testing
    private Duration taskTimeout;

    @Inject
    public ApplicationMetricsRetriever(MetricsNodesConfig nodesConfig) {
        clients = createNodeClients(nodesConfig);
        int numThreads = Math.min(clients.size(), MAX_THREADS);
        taskTimeout = timeout(clients.size(), numThreads);
        forkJoinPool = new ForkJoinPool(numThreads);
    }

    @Override
    public void deconstruct() {
        forkJoinPool.shutdownNow();
        super.deconstruct();
    }

    public Map<Node, List<MetricsPacket>> getMetrics() {
        return getMetrics(defaultMetricsConsumerId);
    }

    public Map<Node, List<MetricsPacket>> getMetrics(ConsumerId consumer) {
        log.log(Level.FINE, () -> "Retrieving metrics from " + clients.size() + " nodes.");
        var forkJoinTask = forkJoinPool.submit(() -> clients.parallelStream()
                .map(client -> getNodeMetrics(client, consumer))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        try {
            var metricsByNode = forkJoinTask.get(taskTimeout.toMillis(), TimeUnit.MILLISECONDS);

            log.log(Level.FINE, () -> "Finished retrieving metrics from " + clients.size() + " nodes.");
            return metricsByNode;

        } catch (Exception e) {
            // Since the task is a ForkJoinTask, we don't need special handling of InterruptedException
            throw new ApplicationMetricsException("Failed retrieving metrics.", e);
        }
    }

    private Map.Entry<Node, List<MetricsPacket>> getNodeMetrics(NodeMetricsClient client, ConsumerId consumer) {
        try {
            return new AbstractMap.SimpleEntry<>(client.node, client.getMetrics(consumer));
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not retrieve metrics from " + client.node.metricsUri(consumer), e);
        }
        return new AbstractMap.SimpleEntry<>(client.node, emptyList());
    }

    private List<NodeMetricsClient> createNodeClients(MetricsNodesConfig nodesConfig) {
        return nodesConfig.node().stream()
                .map(Node::new)
                .map(node-> new NodeMetricsClient(httpClient, node, Clock.systemUTC()))
                .collect(Collectors.toList());
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

    static Duration timeout(int clients, int numThreads) {
        Duration timeout = Duration.ofSeconds(Long.max(MIN_TIMEOUT.toSeconds(), 20 * clients / numThreads));
        return timeout.compareTo(MAX_TIMEOUT) > 0 ? MAX_TIMEOUT : timeout;
    }

    // For testing only!
    void setTaskTimeout(Duration taskTimeout) {
        this.taskTimeout = taskTimeout;
    }

}
