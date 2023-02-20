// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor.applyProcessors;
import static java.util.logging.Level.FINE;

/**
 * Retrieves metrics from a single Vespa node over http. To avoid unnecessary load on metrics
 * proxies, a cached snapshot per consumer is retained and served for a fixed TTL period.
 * Upon failure to retrieve metrics, an empty snapshot is cached.
 * <p>
 * This class assumes that the consumer id is a valid and existing one, which is already
 * ensured by the {@link ApplicationMetricsHandler}.
 *
 * @author gjoranv
 */
public class NodeMetricsClient {

    private static final Logger log = Logger.getLogger(NodeMetricsClient.class.getName());

    final Node node;
    private final CloseableHttpAsyncClient httpClient;
    private final Clock clock;

    private final Map<ConsumerId, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final AtomicLong snapshotsRetrieved = new AtomicLong();

    NodeMetricsClient(CloseableHttpAsyncClient httpClient, Node node, Clock clock) {
        this.httpClient = httpClient;
        this.node = node;
        this.clock = clock;
    }

    List<MetricsPacket> getMetrics(ConsumerId consumer) {
        var snapshot = snapshots.get(consumer);
        return (snapshot != null) ? snapshot.metrics : List.of();
    }

    Optional<Future<?>> startSnapshotUpdate(ConsumerId consumer, Duration ttl) {
        var snapshot = snapshots.get(consumer);
        if ((snapshot != null) && snapshot.isValid(clock.instant(), ttl)) return Optional.empty();

        return Optional.of(retrieveMetrics(consumer));
    }

    private Future<?> retrieveMetrics(ConsumerId consumer) {
        String metricsUri = node.metricsUri(consumer).toString();
        log.log(FINE, () -> "Retrieving metrics from host " + metricsUri);

        CompletableFuture<?> onDone = new CompletableFuture<>();
        httpClient.execute(SimpleRequestBuilder.get(metricsUri).build(),
                new FutureCallback<>() {
                    @Override public void completed(SimpleHttpResponse result) {
                        handleResponse(metricsUri, consumer, result.getBodyText());
                        onDone.complete(null);
                    }
                    @Override public void failed(Exception ex) { onDone.completeExceptionally(ex); }
                    @Override public void cancelled() { onDone.cancel(false);  }
        });
        return onDone;
    }

    void handleResponse(String metricsUri, ConsumerId consumer, String respons) {
        var metrics = processAndBuild(GenericJsonUtil.toMetricsPackets(respons),
                new ServiceIdDimensionProcessor(),
                new ClusterIdDimensionProcessor(),
                new PublicDimensionsProcessor());
        snapshotsRetrieved.incrementAndGet();
        log.log(FINE, () -> "Successfully retrieved " + metrics.size() + " metrics packets from " + metricsUri);
        snapshots.put(consumer, new Snapshot(Instant.now(clock), metrics));
    }

    private static List<MetricsPacket> processAndBuild(List<MetricsPacket.Builder> builders,
                                                       MetricsProcessor... processors) {
        return builders.stream()
                    .map(builder -> applyProcessors(builder, processors))
                    .map(MetricsPacket.Builder::build)
                    .toList();
    }

    long snapshotsRetrieved() {
        return snapshotsRetrieved.get();
    }

    /**
     * Convenience class for storing a metrics snapshot with its timestamp.
     */
    static class Snapshot {

        final Instant timestamp;
        final List<MetricsPacket> metrics;

        Snapshot(Instant timestamp, List<MetricsPacket> metrics) {
            this.timestamp = timestamp;
            this.metrics = metrics;
        }
        boolean isValid(Instant now, Duration ttl) {
            return (metrics != null) && !metrics.isEmpty() && now.isBefore(timestamp.plus(ttl));
        }
    }

}
