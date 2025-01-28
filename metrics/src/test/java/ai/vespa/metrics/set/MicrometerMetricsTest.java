package ai.vespa.metrics.set;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MicrometerMetricsTest {

    @Test
    void verify_metric_set_contains_all_metrics_defined() {
        var metricNames = Arrays.stream(MicrometerMetrics.values())
                .map(MicrometerMetrics::baseName)
                .sorted()
                .toList();
        var metricSetNames = MicrometerMetrics.asMetricSet().getMetrics().keySet().stream()
                .map(m -> m.substring(0, m.lastIndexOf('.')))
                .distinct()
                .sorted()
                .toList();
        assertFalse(metricNames.isEmpty());
        assertFalse(metricSetNames.isEmpty());
        assertIterableEquals(metricNames, metricSetNames);
    }
}