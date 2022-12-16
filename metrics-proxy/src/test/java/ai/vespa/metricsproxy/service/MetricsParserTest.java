package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.core.VespaMetrics;
import ai.vespa.metricsproxy.metric.Metric;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static ai.vespa.metricsproxy.service.MetricsParser.dimensionsHashCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author gjoranv
 */
public class MetricsParserTest {

    private static class MetricsConsumer implements MetricsParser.Consumer {
        List<Metric> metrics = new ArrayList<>();

        @Override
        public void consume(Metric metric) {
            metrics.add(metric);
        }
    }

    @Test
    public void dimensions_hashcode_is_different_for_distinct_but_duplicate_dimension_values() {
        var dimensions1 = List.of(
                new MetricsParser.Dimension("cluster", "CLUSTER-1"),
                new MetricsParser.Dimension("clusterid", "CLUSTER-1"));

        var dimensions2 = List.of(
                new MetricsParser.Dimension("cluster", "CLUSTER-2"),
                new MetricsParser.Dimension("clusterid", "CLUSTER-2"));

        System.out.println(dimensionsHashCode(dimensions1));
        System.out.println(dimensionsHashCode(dimensions2));
        assertNotEquals(dimensionsHashCode(dimensions1), dimensionsHashCode(dimensions2));
    }

    @Test
    public void different_dimension_values_are_not_treated_as_equal() throws Exception {
        var collector = new MetricsConsumer();
        MetricsParser.parse(metricsJson(), collector);
        assertEquals(2, collector.metrics.size());
        assertNotEquals("Dimensions should not be equal",
                        collector.metrics.get(0).getDimensions(),
                        collector.metrics.get(1).getDimensions());
    }

    // The duplicate dimension values for 'cluster' and 'clusterid' exposed a bug in a previously used hashing algo for dimensions.
    private String metricsJson() {
        return """
                {
                  "time": 1671035366573,
                  "status": {
                    "code": "up"
                  },
                  "metrics": {
                    "snapshot": {
                      "from": 1671035306.562,
                      "to": 1671035366.562
                    },
                    "values": [
                      {
                        "name": "cluster-controller.resource_usage.nodes_above_limit",
                        "values": {
                          "last": 1.0
                        },
                        "dimensions": {
                          "controller-index": "0",
                          "cluster": "CLUSTER-1",
                          "clusterid": "CLUSTER-1"
                        }
                      },
                      {
                        "name": "cluster-controller.resource_usage.nodes_above_limit",
                        "values": {
                          "last": 2.0
                        },
                        "dimensions": {
                          "controller-index": "0",
                          "cluster": "CLUSTER-2",
                          "clusterid": "CLUSTER-2"
                        }
                      }
                    ]
                  }
                }
                """;
    }

}
