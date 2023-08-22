// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics;

import ai.vespa.metrics.set.Metric;
import ai.vespa.metrics.set.MetricSet;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author gjoranv
 */
public class MetricSetTest {

    @Test
    void metrics_from_children_are_added() {
        MetricSet child1 = new MetricSet("child1", List.of(new Metric("child1_metric")));
        MetricSet child2 = new MetricSet("child2", List.of(new Metric("child2_metric")));
        MetricSet parent = new MetricSet("parent", emptyList(), List.of(child1, child2));

        Map<String, Metric> parentMetrics = parent.getMetrics();
        assertEquals(2, parentMetrics.size());
        assertNotNull(parentMetrics.get("child1_metric"));
        assertNotNull(parentMetrics.get("child2_metric"));
    }

    @Test
    void adding_the_same_child_set_twice_has_no_effect() {
        MetricSet child = new MetricSet("child", List.of(new Metric("child_metric")));
        MetricSet parent = new MetricSet("parent", emptyList(), List.of(child, child));

        Map<String, Metric> parentMetrics = parent.getMetrics();
        assertEquals(1, parentMetrics.size());
        assertNotNull(parentMetrics.get("child_metric"));
    }

    @Test
    void internal_metrics_take_precedence_over_metrics_from_children() {
        String METRIC_NAME = "metric1";
        String COMMON_DIMENSION_KEY = "commonKey";

        Map<String, String> childDimensions = Map.of(COMMON_DIMENSION_KEY, "childCommonVal", "childKey", "childVal");
        Metric childMetric = new Metric(METRIC_NAME, "child-output-name", "child-description", childDimensions);

        Map<String, String> parentDimensions = Map.of(COMMON_DIMENSION_KEY, "parentCommonVal","parentKey", "parentVal");
        Metric parentMetric = new Metric(METRIC_NAME, "parent-output-name", "parent-description", parentDimensions);

        MetricSet child =  new MetricSet("set1", Sets.newHashSet(childMetric));
        MetricSet parent = new MetricSet("set1", Sets.newHashSet(parentMetric), Sets.newHashSet(child));

        Metric combinedMetric = parent.getMetrics().get(METRIC_NAME);
        assertEquals("parent-output-name", combinedMetric.outputName);
        assertEquals("parent-description", combinedMetric.description);
        assertEquals(3, combinedMetric.dimensions.size());
        assertEquals("parentCommonVal", combinedMetric.dimensions.get(COMMON_DIMENSION_KEY));
    }

    @Test
    void it_can_be_generated_from_builder() {
        MetricSet metricSet = new MetricSet.Builder("test")
                .metric("metric1")
                .metric(TestMetrics.ENUM_METRIC1.last())
                .metric(TestMetrics.ENUM_METRIC2, EnumSet.of(Suffix.sum, Suffix.count))
                .metric(new Metric("metric2"))
                .metrics(List.of(new Metric("metric3")))
                .metricSet(new MetricSet.Builder("child")
                                   .metric("child_metric1")
                                   .metric("child_metric2")
                                   .build())
                .build();

        Map<String, Metric> metrics = metricSet.getMetrics();
        assertEquals(8, metrics.size());
        assertNotNull(metrics.get("metric1"));
        assertNotNull(metrics.get("emum-metric1.last"));
        assertNotNull(metrics.get("emum-metric2.sum"));
        assertNotNull(metrics.get("emum-metric2.count"));
        assertNotNull(metrics.get("metric2"));
        assertNotNull(metrics.get("metric3"));
        assertNotNull(metrics.get("child_metric1"));
        assertNotNull(metrics.get("child_metric1"));
    }

    enum TestMetrics implements VespaMetrics {
        ENUM_METRIC1("emum-metric1"),
        ENUM_METRIC2("emum-metric2");

        private final String name;

        TestMetrics(String name) {
            this.name = name;
        }

        public String baseName() {
            return name;
        }

        public Unit unit() {
            return null;
        }

        public String description() {
            return null;
        }

    }
}
