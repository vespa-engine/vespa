// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import org.junit.Test;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class CoredumpGathererTest {

    @Test
    public void finds_one_coredump() {
    MetricsPacket packet = CoredumpMetricGatherer.gatherCoredumpMetrics(new MockFileWrapper()).build();

    assertEquals("system-coredumps-processing", packet.service.id);
    assertEquals(1, packet.statusCode);
    }

    static class MockFileWrapper extends FileWrapper {


        @Override
        Stream<Path> walkTree(Path path)  {
            return Stream.of(Path.of("dummy-path"));
        }

        @Override
        boolean isRegularFile(Path path) {
            return true;
        }
    }

}
