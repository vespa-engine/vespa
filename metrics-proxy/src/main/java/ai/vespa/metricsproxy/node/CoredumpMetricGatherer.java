// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil;
import com.yahoo.vespa.defaults.Defaults;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ai.vespa.metricsproxy.node.NodeMetricGatherer.ROUTING_JSON;

/**
 * @author olaa
 */
public class CoredumpMetricGatherer {

    private static final int COREDUMP_AGE_IN_MINUTES = 12600;
    private static final Path COREDUMP_PATH = Path.of(Defaults.getDefaults().underVespaHome("var/crash/processing"));

    private static final Logger logger = Logger.getLogger(CoredumpMetricGatherer.class.getSimpleName());


    protected static List<MetricsPacket.Builder> gatherCoredumpMetrics(FileWrapper fileWrapper) {
        long coredumps = getCoredumpsFromLastPeriod(fileWrapper);
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("timestamp", Instant.now().getEpochSecond());
            jsonObject.put("application", "system-coredumps-processing");
            jsonObject.put("status_code", coredumps);
            jsonObject.put("status_message", coredumps == 0 ? "OK" : String.format("Found %d coredumps in past %d minutes", coredumps, COREDUMP_AGE_IN_MINUTES));
            jsonObject.put("routing", ROUTING_JSON);
            return YamasJsonUtil.toMetricsPackets(jsonObject.toString());
        } catch (JSONException e) {
            logger.log(Level.WARNING, "Error writing JSON", e);
            return Collections.emptyList();
        }
    }

    private static long getCoredumpsFromLastPeriod(FileWrapper fileWrapper) {
        try {
            return fileWrapper.walkTree(COREDUMP_PATH)
                    .filter(file -> fileWrapper.isRegularFile(file))
                    .filter(file -> isNewFile(fileWrapper, file))
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isNewFile(FileWrapper filewrapper, Path file) {
        try {
            return filewrapper.getLastModifiedTime(file)
                    .plus(COREDUMP_AGE_IN_MINUTES, ChronoUnit.MINUTES)
                    .isAfter(Instant.now());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
