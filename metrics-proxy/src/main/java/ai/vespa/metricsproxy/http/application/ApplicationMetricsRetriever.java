// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.util.http.hc5.VespaAsyncHttpClientBuilder;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.SocketException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ai.vespa.metricsproxy.http.ValuesFetcher.defaultMetricsConsumerId;

/**
 * This class retrieves metrics from all nodes in the given config, usually all
 * nodes in a Vespa application.
 *
 * @author gjoranv
 */
public class ApplicationMetricsRetriever extends AbstractComponent implements Runnable {

    private static final Logger log = Logger.getLogger(ApplicationMetricsRetriever.class.getName());

    static final Duration MIN_TIMEOUT = Duration.ofSeconds(60);
    static final Duration MAX_TIMEOUT = Duration.ofSeconds(240);

    private static final int HTTP_CONNECT_TIMEOUT = 5000;
    private static final int HTTP_SOCKET_TIMEOUT = 30000;
    private static final Duration METRICS_TTL = Duration.ofSeconds(30);

    private final CloseableHttpAsyncClient httpClient = createHttpClient();
    private final List<NodeMetricsClient> clients;
    private final Thread pollThread;
    private final Set<ConsumerId> consumerSet;
    private long pollCount = 0;
    private volatile boolean stopped;
    private volatile Duration taskTimeout;

    @Inject
    public ApplicationMetricsRetriever(MetricsNodesConfig nodesConfig) {
        clients = createNodeClients(nodesConfig);
        taskTimeout = timeout(clients.size());
        stopped = false;
        consumerSet = new HashSet<>();
        httpClient.start();
        pollThread = new Thread(this, "metrics-poller");
        pollThread.setDaemon(true);
    }

    @Override
    public void run() {
        while (true) {
            try {
                ConsumerId [] consumers;
                synchronized (pollThread) {
                    consumers = consumerSet.toArray(new ConsumerId[0]);
                }
                for (ConsumerId consumer : consumers) {
                    int numFailed = fetchMetricsAsync(consumer);
                    if (numFailed > 0 ) {
                        log.log(Level.INFO, "Updated metrics for consumer '" + consumer +"' failed for " + numFailed + " services");
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
            } catch (InterruptedException e) {
            } catch (Exception e) {
                log.log(Level.WARNING, "Got unknown exception:", e);
            }
        }

    }

    @Override
    public void deconstruct() {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.warning("Failed closing httpclient: " + e);
        }
        synchronized (pollThread) {
            stopped = true;
            pollThread.notifyAll();
        }
        try {
            pollThread.join(Duration.ofSeconds(3).toMillis());
            if (pollThread.isAlive()) {
                log.log(Level.WARNING, "metrics poller thread still not stopped, using Thread.interrupt():");
                pollThread.interrupt();
                pollThread.join();
            }
        } catch (InterruptedException e) {}
        super.deconstruct();
    }

    Map<Node, List<MetricsPacket>> getMetrics() {
        return getMetrics(defaultMetricsConsumerId);
    }

    public Map<Node, List<MetricsPacket>> getMetrics(ConsumerId consumer) {
        log.log(Level.FINE, () -> "Retrieving metrics from " + clients.size() + " nodes.");
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

    void startPollAndWait() {
        try {
            synchronized (pollThread) {
                if ( ! pollThread.isAlive()) {
                    pollThread.start();
                }
                long before = pollCount;
                pollThread.notifyAll();
                while (pollCount <= before + 1) {
                    pollThread.notifyAll();
                    pollThread.wait();
                }
            }
        } catch (InterruptedException e) {}
    }

    private int fetchMetricsAsync(ConsumerId consumer) {
        Map<Node, Future<?>> futures = new HashMap<>();
        for (NodeMetricsClient client : clients) {
            client.startSnapshotUpdate(consumer, METRICS_TTL).ifPresent(future -> futures.put(client.node, future));
        }
        int numOk = 0;
        int numTried = futures.size();
        for (Map.Entry<Node, Future<?>> entry : futures.entrySet()) {
            if (stopped) break;
            try {
                entry.getValue().get(taskTimeout.toMillis(), TimeUnit.MILLISECONDS);
                numOk++;
            } catch (InterruptedException | ExecutionException | TimeoutException | CancellationException e) {
                Throwable cause = e.getCause();
                if (stopped || e instanceof ExecutionException && ((cause instanceof SocketException) || cause instanceof ConnectTimeoutException)) {
                    log.log(Level.FINE, "Failed retrieving metrics for '" + entry.getKey() + "' : " + cause.getMessage());
                } else {
                    log.log(Level.WARNING, "Failed retrieving metrics for '" + entry.getKey() + "' : ", e);
                }
            }
        }
        log.log(Level.FINE, () -> "Finished retrieving metrics from " + clients.size() + " nodes.");
        return numTried - numOk;
    }

    private List<NodeMetricsClient> createNodeClients(MetricsNodesConfig nodesConfig) {
        return nodesConfig.node().stream()
                .map(Node::new)
                .map(node-> new NodeMetricsClient(httpClient, node, Clock.systemUTC()))
                .toList();
    }

    @SuppressWarnings("deprecation")
    static CloseableHttpAsyncClient createHttpClient() {
        return VespaAsyncHttpClientBuilder.create()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(Timeout.ofMilliseconds(HTTP_SOCKET_TIMEOUT))
                        .setIoThreadCount(2)
                        .build())
                .setUserAgent("application-metrics-retriever")
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(HTTP_CONNECT_TIMEOUT))
                        .setResponseTimeout(Timeout.ofMilliseconds(HTTP_SOCKET_TIMEOUT))
                        .build())
                .build();
    }

    static Duration timeout(int clients) {
        Duration timeout = Duration.ofSeconds(Long.max(MIN_TIMEOUT.toSeconds(), clients));
        return timeout.compareTo(MAX_TIMEOUT) > 0 ? MAX_TIMEOUT : timeout;
    }

    // For testing only!
    void setTaskTimeout(Duration taskTimeout) {
        this.taskTimeout = taskTimeout;
    }

}
