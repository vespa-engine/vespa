// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;


/**
 * @author olaa
 */
public class HostLifeGatherer {

    private static final Path UPTIME_PATH = Path.of("/proc");

    protected static MetricsPacket.Builder gatherHostLifeMetrics(FileWrapper fileWrapper) {
        long upTime;
        int statusCode = 0;
        String statusMessage = "OK";

        try {
            upTime = fileWrapper.getFileAgeInSeconds(UPTIME_PATH);
        } catch (IOException e) {
            upTime = 0;
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

}
