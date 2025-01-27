package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.test.MockMetric;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MicrometerMetricReporterTest {

    @Test
    void reports_simple_jvm_metrics_on_publish() {
        var metrics = new MockMetric();
        var reporter = new MicrometerMetricReporter(metrics);
        reporter.forcePublish();
        assertFalse(metrics.metrics().isEmpty());
        metrics.metrics().get("jvm.classes.loaded")
                .forEach((context, value) -> assertTrue(value > 0, "Expected some classes reported as loaded, but got " + value));
    }
}