// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.http.ValuesFetcher.defaultMetricsConsumerId;

/**
 * This class retrieves metrics from all nodes in the given config, usually all
 * nodes in a Vespa application.
 *
 * @author gjoranv
 */
public class ApplicationMetricsRetriever extends AbstractComponent implements Runnable {

    private static final Logger log = Logger.getLogger(ApplicationMetricsRetriever.class.getName());

    static final int MAX_THREADS = 20;
    static final Duration MIN_TIMEOUT = Duration.ofSeconds(60);
    static final Duration MAX_TIMEOUT = Duration.ofSeconds(240);

    private static final int HTTP_CONNECT_TIMEOUT = 5000;
    private static final int HTTP_SOCKET_TIMEOUT = 30000;
    private static final Duration METRICS_TTL = Duration.ofSeconds(30);

    private final HttpClient httpClient = createHttpClient();
    private final List<NodeMetricsClient> clients;
    private final ExecutorService fetchPool;
    private final Thread pollThread;
    private final Set<ConsumerId> consumerSet;
    private long pollCount = 0;
    private boolean stopped;

    // Non-final for testing
    private Duration taskTimeout;

    @Inject
    public ApplicationMetricsRetriever(MetricsNodesConfig nodesConfig) {
        clients = createNodeClients(nodesConfig);
        int numThreads = Math.min(clients.size(), MAX_THREADS);
        taskTimeout = timeout(clients.size(), numThreads);
        fetchPool = Executors.newFixedThreadPool(numThreads, ThreadFactoryFactory.getDaemonThreadFactory("metrics-fetcher"));
        stopped = false;
        consumerSet = new HashSet<>();
        consumerSet.add(defaultMetricsConsumerId);
        pollThread = new Thread(this, "metrics-poller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    @Override
    public void run() {
        try {
            while (true) {
                ConsumerId [] consumers;
                synchronized (pollThread) {
                    consumers = consumerSet.toArray(new ConsumerId[0]);
                }
                for (ConsumerId consumer : consumers) {
                    int numFailed = fetchMetricsAsync(consumer);
                    if (numFailed > 0 ) {
                        log.log(Level.WARNING, "Updated metrics for consumer '" + consumer +"' failed for " + numFailed + " services");
                    } else {
                        log.log(Level.FINE, "Updated metrics for consumer '" + consumer +"'.");
                    }
                }
                Duration timeUntilNextPoll = Duration.ofMillis(1000);
                synchronized (pollThread) {
                    pollCount++;
                    pollThread.notifyAll();
                    pollThread.wait(timeUntilNextPoll.toMillis());
                    if (stopped) return;
                }
            }
        } catch (InterruptedException e) {}
    }

    @Override
    public void deconstruct() {
        synchronized (pollThread) {
            stopped = true;
            pollThread.notifyAll();
        }
        fetchPool.shutdownNow();
        try {
            pollThread.join();
        } catch (InterruptedException e) {}
        super.deconstruct();
    }

    public Map<Node, List<MetricsPacket>> getMetrics() {
        return getMetrics(defaultMetricsConsumerId);
    }

    public Map<Node, List<MetricsPacket>> getMetrics(ConsumerId consumer) {
        log.log(Level.INFO, () -> "Retrieving metrics from " + clients.size() + " nodes.");
        synchronized (pollThread) {
            if (consumerSet.add(consumer)) {
                // Wakeup poll thread first time we see a new consumer
                pollThread.notifyAll();
            }
        }
        Map<Node, List<MetricsPacket>> metrics = new HashMap<>();
        for (NodeMetricsClient client : clients) {
            metrics.put(client.node, client.getMetrics(consumer));
        }
        return metrics;
    }

    void startPollAnwWait() {
        try {
            synchronized (pollThread) {
                if ( ! pollThread.isAlive()) {
                    pollThread.start();
                }
                long before = pollCount;
                pollThread.notifyAll();
                while (pollCount == before) {
                    pollThread.wait();
                }
            }
        } catch (InterruptedException e) {}
    }

    private int fetchMetricsAsync(ConsumerId consumer) {
        Map<Node, Future<Boolean>> futures = new HashMap<>();
        for (NodeMetricsClient client : clients) {
            futures.put(client.node, fetchPool.submit(() -> updateMetrics(client, consumer)));
        }
        int numOk = 0;
        int numTried = futures.size();
        for (Map.Entry<Node, Future<Boolean>> entry : futures.entrySet()) {
            try {
                Boolean result = entry.getValue().get(taskTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (result != null && result) numOk++;
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                // Since the task is a ForkJoinTask, we don't need special handling of InterruptedException
                log.log(Level.WARNING, "Failed retrieving metrics for '" + entry.getKey() +  "' : ", e);
            }
        }
        log.log(Level.INFO, () -> "Finished retrieving metrics from " + clients.size() + " nodes.");
        return numTried - numOk;
    }

    private boolean updateMetrics(NodeMetricsClient client, ConsumerId consumer) {
        try {
            return client.updateSnapshots(consumer, METRICS_TTL);
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not retrieve metrics from " + client.node.metricsUri(consumer), e);
            return false;
        }
    }

    private List<NodeMetricsClient> createNodeClients(MetricsNodesConfig nodesConfig) {
        return nodesConfig.node().stream()
                .map(Node::new)
                .map(node-> new NodeMetricsClient(httpClient, node, Clock.systemUTC()))
                .collect(Collectors.toList());
   }

    private static CloseableHttpClient createHttpClient() {
        return VespaHttpClientBuilder.create()
                .setUserAgent("application-metrics-retriever")
                .setDefaultRequestConfig(RequestConfig.custom()
                                                 .setConnectTimeout(Timeout.ofMilliseconds(HTTP_CONNECT_TIMEOUT))
                                                 .setResponseTimeout(Timeout.ofMilliseconds(HTTP_SOCKET_TIMEOUT))
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
