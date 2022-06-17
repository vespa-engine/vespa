package ai.vespa.metricsproxy.opentelemetry;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.http.ValuesFetcher;
import ai.vespa.metricsproxy.service.VespaServices;
import com.yahoo.component.AbstractComponent;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

public class OpenTelemetryMetricExporter extends AbstractComponent {
    static private Resource resource = Resource.builder().put(SERVICE_NAME, "metrics-proxy").build();

    private final ValuesFetcher valuesFetcher;
    private final Duration interval;
    private final ScheduledThreadPoolExecutor executor;
    private final Set<Runnable> shutdown;
    private final MetricExporter metricExporter;
    private final String consumer;

    public OpenTelemetryMetricExporter(MetricsManager metricsManager, VespaServices vespaServices, MetricsConsumers metricsConsumers) {
        shutdown = new HashSet<>();
        valuesFetcher = new ValuesFetcher(metricsManager, vespaServices, metricsConsumers);
        interval = Duration.ofSeconds(60);
        consumer = "default";
        executor = new ScheduledThreadPoolExecutor(1, r -> {
            var t = new Thread(r);
            t.setName("opentelemetry-export");
            return t;
        });
        metricExporter = OtlpGrpcMetricExporter.getDefault();
    }

    public void start() {
        executor.scheduleAtFixedRate(new MetricTask(), 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private class MetricTask implements Runnable {
        private Map<String, Long> firstSeen = new HashMap<>();

        @Override
        public void run() {
            var metrics = valuesFetcher.fetch(consumer);
            var export = new HashSet<MetricData>();

            metrics.stream().forEach(m -> {
                var attributesBuilder = Attributes.builder();
                m.dimensions().forEach((k, v) -> {
                    attributesBuilder.put(k.id, v);
                });
                attributesBuilder.put("application", m.service.id);
                var attributes = attributesBuilder.build();
                m.metrics().forEach((k, v) -> {
                    var id = k.id;
                    var firstTs = firstSeen.computeIfAbsent(uniqueKey(m.service.id, id), (__) -> nanoSinceEpoch(m.timestamp));
                    if (id.length() > 62) { // too long metric name
                        id = "xx" + k.id.substring(id.length()-60);
                    }
                    // TODO: Support counter (and histogram)
                    var md = ImmutableMetricData.createDoubleGauge(resource,
                            InstrumentationScopeInfo.empty(),
                            id, "", "",
                            ImmutableGaugeData.create(
                                    List.of(ImmutableDoublePointData.create(firstTs, nanoSinceEpoch(m.timestamp), attributes, v.doubleValue()))
                            ));
                    export.add(md);
                });
            });
            metricExporter.export(export);
        }

        private long nanoSinceEpoch(long ts) {
            return ts * 1000 * 1000 * 1000;
        }

        private String uniqueKey(String application, String id) {
            return application + ":" + id;
        }
    }

    @Override
    public void deconstruct() {
        metricExporter.close();
    }
}
