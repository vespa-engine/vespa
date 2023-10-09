// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.embedding;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.metrics.simple.Gauge;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Point;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author bjorncs
 */
public class EmbedderRuntime implements Embedder.Runtime {

    private final Gauge embedLatency;
    private final Gauge sequenceLength;
    private final Map<MetricDimensions, Point> metricPointCache = new ConcurrentHashMap<>();

    @Inject
    public EmbedderRuntime(MetricReceiver metrics) {
        embedLatency = metrics.declareGauge(ContainerMetrics.EMBEDDER_LATENCY.baseName());
        sequenceLength = metrics.declareGauge(ContainerMetrics.EMBEDDER_SEQUENCE_LENGTH.baseName());
    }

    @Override
    public void sampleEmbeddingLatency(double millis, Embedder.Context ctx) {
        embedLatency.sample(millis, metricPoint(ctx));
    }

    @Override
    public void sampleSequenceLength(long length, Embedder.Context ctx) {
        sequenceLength.sample(length, metricPoint(ctx));
    }

    private Point metricPoint(Embedder.Context ctx) {
        var dimensions = new MetricDimensions(ctx.getEmbedderId(), ctx.getLanguage(), ctx.getDestination());
        return metricPointCache.computeIfAbsent(
                dimensions, d -> new Point(Map.of("embedder", d.embedderId(),
                                                  "language", d.language().languageCode(),
                                                  "destination", d.destination())));
    }

    private record MetricDimensions(String embedderId, Language language, String destination) {}

}
