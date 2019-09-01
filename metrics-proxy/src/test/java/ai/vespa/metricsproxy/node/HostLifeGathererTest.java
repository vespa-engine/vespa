// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class HostLifeGathererTest {

    @Test
    public void host_is_alive() {
        MetricsPacket packet = HostLifeGatherer.gatherHostLifeMetrics(new MockFileWrapper()).build();

        Map<MetricId, Number> expectedMetrics = Map.of(MetricId.toMetricId("uptime"), 123d, MetricId.toMetricId("alive"), 1);
        assertEquals("host_life", packet.service.id);
        assertEquals(0, packet.statusCode);
        assertEquals(expectedMetrics, packet.metrics());

    }

    static class MockFileWrapper extends FileWrapper {
        @Override
        List<String> readAllLines(Path path) {
            return List.of("123 432");
        }
    }
}
