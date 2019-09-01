// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


/**
 * @author olaa
 */
public class HostLifeGatherer {

    private static final Path UPTIME_PATH = Path.of("/proc/uptime");

    protected static MetricsPacket.Builder gatherHostLifeMetrics(FileWrapper fileWrapper) {
        double upTime;
        int statusCode = 0;
        String statusMessage = "OK";

        try {
            upTime = getHostLife(fileWrapper);
        } catch (IOException e) {
            upTime = 0d;
            statusCode = 1;
            statusMessage = e.getMessage();
        }

        return new MetricsPacket.Builder(ServiceId.toServiceId("host_life"))
                .timestamp(Instant.now().getEpochSecond())
                .statusMessage(statusMessage)
                .statusCode(statusCode)
                .putMetric(MetricId.toMetricId("uptime"), upTime)
                .putMetric(MetricId.toMetricId("alive"), 1)
                .addConsumers(Set.of(ConsumerId.toConsumerId("Vespa")));
    }



    private static double getHostLife(FileWrapper fileWrapper) throws IOException {
        return fileWrapper.readAllLines(UPTIME_PATH)
                .stream()
                .mapToDouble(line -> Double.valueOf(line.split("\\s")[0]))
                .findFirst()
                .orElseThrow();
    }
}
