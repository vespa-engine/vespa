// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;

import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.zip.GZIPInputStream;

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
        var request = SimpleRequestBuilder.get(metricsUri).build();
        // The client does not decompress (see ApplicationMetricsRetriever.createHttpClient), so the body is
        // held compressed until all of it has arrived, and inflated while it is parsed
        request.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");
        httpClient.execute(SimpleRequestProducer.create(request),
                new BasicResponseConsumer<>(new ChunkedBodyConsumer()),
                new FutureCallback<>() {
                    @Override public void completed(Message<HttpResponse, InputStream> result) {
                        InputStream body = result.getBody() != null ? result.getBody() : InputStream.nullInputStream();
                        try {
                            handleResponse(node, metricsUri, consumer, decoded(result.getHead(), body));
                            onDone.complete(null);
                        } catch (RuntimeException | IOException e) { // Fail the fetch now, rather than leaving it to time out
                            onDone.completeExceptionally(e);
                        }
                    }
                    @Override public void failed(Exception ex) { onDone.completeExceptionally(ex); }
                    @Override public void cancelled() { onDone.cancel(false);  }
        });
        return onDone;
    }

    private static InputStream decoded(HttpResponse response, InputStream body) throws IOException {
        Header encoding = response.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
        if (encoding == null) return body;
        if (encoding.getValue().equalsIgnoreCase("gzip")) return new GZIPInputStream(body, 1 << 16);
        throw new IllegalStateException("Unexpected Content-Encoding '" + encoding.getValue() + "'");
    }

    void handleResponse(Node node, String metricsUri, ConsumerId consumer, InputStream response) {
        var metrics = processAndBuild(GenericJsonUtil.toMetricsPackets(response),
                new ServiceIdDimensionProcessor(),
                new ClusterIdDimensionProcessor(),
                new PublicDimensionsProcessor(),
                new HostnameDimensionProcessor(node.hostname));
        snapshotsRetrieved.incrementAndGet();
        log.log(FINE, () -> "Successfully retrieved " + metrics.size() + " metrics packets from " + metricsUri);
        snapshots.put(consumer, new Snapshot(Instant.now(clock), metrics));
    }

    private static List<MetricsPacket> processAndBuild(List<MetricsPacket.Builder> builders,
                                                       MetricsProcessor... processors) {
        var sharing = new MetricsPacket.Sharing();
        return builders.stream()
                    .map(builder -> applyProcessors(builder, processors))
                    .map(builder -> builder.build(sharing))
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
