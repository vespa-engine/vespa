package com.yahoo.container.jdisc.metric;

import ai.vespa.metrics.set.MicrometerMetrics;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Metric;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Publishes JVM metrics from the Micrometer metrics library to JDisc's metric system.
 *
 * @author bjorncs
 */
public class MicrometerMetricReporter extends AbstractComponent {

    private static final Duration PUBLISH_FREQUENCY = Duration.ofSeconds(1);
    private static final Set<String> ALLOWED_METERS = Arrays.stream(MicrometerMetrics.values())
            .map(MicrometerMetrics::baseName).collect(Collectors.toSet());

    private static final Logger log = Logger.getLogger(MicrometerMetricReporter.class.getName());

    private final VespaMicrometerRegistry registry;
    private final Set<AutoCloseable> closeables;

    @Inject
    public MicrometerMetricReporter(Metric metric) {
        registry = new VespaMicrometerRegistry(metric);
        var closeables = new HashSet<AutoCloseable>();
        registerMeter(registry, closeables, ClassLoaderMetrics::new);
        registerMeter(registry, closeables, JvmGcMetrics::new);
        registerMeter(registry, closeables, JvmHeapPressureMetrics::new);
        registerMeter(registry, closeables, JvmMemoryMetrics::new);
        registerMeter(registry, closeables, JvmThreadMetrics::new);
        this.closeables = Set.copyOf(closeables);
    }

    void forcePublish() { registry.publish(); }

    @Override
    public void deconstruct() {
        registry.close();
        for (AutoCloseable c : closeables) {
            try {
                c.close();
            } catch (Exception e) {
                log.warning(() -> "Failed to close instance of %s: %s".formatted(c.getClass().getName(), e));
            }
        }
    }

    private static void registerMeter(
            VespaMicrometerRegistry registry, Set<AutoCloseable> closeables, Supplier<? extends MeterBinder> provider) {
        var binder = provider.get();
        if (binder instanceof AutoCloseable c) closeables.add(c);
        binder.bindTo(registry);
    }

    private static class VespaMicrometerRegistry extends StepMeterRegistry implements AutoCloseable {
        private final Metric jdiscMetric;

        VespaMicrometerRegistry(Metric jdiscMetric) {
            super(new RegistryConfig(), Clock.SYSTEM);
            this.jdiscMetric = jdiscMetric;
            start(new NamedThreadFactory("micrometer-metrics-publisher"));
        }

        @Override
        protected void publish() {
            for (var meter : getMeters()) {
                var name = meter.getId().getName();
                if (!ALLOWED_METERS.contains(name)) {
                    log.fine(() -> "Ignoring meter with id '%s'".formatted(meter.getId()));
                    continue;
                }
                var dimensions = new HashMap<String, Object>();
                meter.getId().getTags().forEach(tag -> dimensions.put(tag.getKey(), tag.getValue()));
                var context = jdiscMetric.createContext(dimensions);
                switch (meter.getId().getType()) {
                    case COUNTER -> {
                        if (meter instanceof FunctionCounter fc) {
                            jdiscMetric.set(name, fc.count(), context);
                        } else if (meter instanceof Counter c) {
                            jdiscMetric.set(name, c.count(), context);
                        } else {
                            throw new IllegalArgumentException("Unsupported counter type: " + meter.getClass().getName());
                        }
                    }
                    case GAUGE -> {
                        var gauge = (Gauge) meter;
                        jdiscMetric.set(name, gauge.value(), context);
                    }
                    case TIMER -> {
                        var timer = (Timer) meter;
                        jdiscMetric.set(name, timer.max(TimeUnit.SECONDS), context);
                    }
                    default -> throw new IllegalArgumentException("Unsupported meter type: " + meter.getId().getType());
                }
            }
        }

        @Override protected TimeUnit getBaseTimeUnit() { return TimeUnit.SECONDS; }

        private static class RegistryConfig implements StepRegistryConfig {
            @Override public Duration step() { return PUBLISH_FREQUENCY; }
            @Override public String prefix() { return ""; }
            @Override public String get(String key) { return null; }
        }
    }
}
