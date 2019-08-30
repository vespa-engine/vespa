// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import org.junit.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class CoredumpGathererTest {

    @Test
    public void finds_one_coredump() {
    List<MetricsPacket> actualPackets = CoredumpMetricGatherer.gatherCoredumpMetrics(new MockFileWrapper())
            .stream()
            .map(MetricsPacket.Builder::build)
            .collect(Collectors.toList());

    assertEquals(1, actualPackets.size());

    MetricsPacket packet = actualPackets.get(0);

    assertEquals("system-coredumps-processing", packet.service.id);
    assertEquals(1, packet.statusCode);
    }

    static class MockFileWrapper extends FileWrapper {


        @Override
        Stream<Path> walkTree(Path path)  {
            return Stream.of(Path.of("dummy-path"));
        }

        @Override
        Instant getLastModifiedTime(Path path)  {
            return Instant.now();
        }

        @Override
        boolean isRegularFile(Path path) {
            return true;
        }
    }

}
