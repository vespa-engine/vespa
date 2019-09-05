// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import com.yahoo.vespa.defaults.Defaults;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Logger;


/**
 * @author olaa
 */
public class CoredumpMetricGatherer {

    private static final Path COREDUMP_PATH = Path.of(Defaults.getDefaults().underVespaHome("var/crash/processing"));

    private static final Logger logger = Logger.getLogger(CoredumpMetricGatherer.class.getSimpleName());


    protected static MetricsPacket.Builder gatherCoredumpMetrics(FileWrapper fileWrapper) {
        int coredumps = getNumberOfCoredumps(fileWrapper);
        return new MetricsPacket.Builder(ServiceId.toServiceId("system-coredumps-processing"))
        .timestamp(Instant.now().getEpochSecond())
        .statusCode(coredumps)
        .statusMessage(coredumps == 0 ? "OK" : String.format("Found %d coredumps", coredumps))
        .addConsumers(Set.of(ConsumerId.toConsumerId("Vespa")));
    }

    private static int getNumberOfCoredumps(FileWrapper fileWrapper) {
        try {
            return (int) fileWrapper.walkTree(COREDUMP_PATH)
                    .filter(fileWrapper::isRegularFile)
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
