// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ai.vespa.metricsproxy.node.NodeMetricGatherer.ROUTING_JSON;

/**
 * @author olaa
 */
public class HostLifeGatherer {

    private static final Path UPTIME_PATH = Path.of("/proc/uptime");

    private static final Logger logger = Logger.getLogger(HostLifeGatherer.class.getSimpleName());

    protected static List<MetricsPacket.Builder> gatherHostLifeMetrics(FileWrapper fileWrapper) {
        JSONObject jsonObject = new JSONObject();
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

        try {
            jsonObject.put("application", "host_life");
            jsonObject.put("timestamp", Instant.now().getEpochSecond());
            jsonObject.put("status_message", statusMessage);
            jsonObject.put("status_code", statusCode);
            JSONObject metrics = new JSONObject();
            metrics.put("uptime", upTime);
            metrics.put("alive", 1);
            jsonObject.put("metrics", metrics);
            jsonObject.put("routing", ROUTING_JSON);
            return YamasJsonUtil.toMetricsPackets(jsonObject.toString());
        } catch (JSONException e) {
            logger.log(Level.WARNING, "Error writing JSON", e);
            return Collections.emptyList();
        }


    }



    private static double getHostLife(FileWrapper fileWrapper) throws IOException {
        return fileWrapper.readAllLines(UPTIME_PATH)
                .stream()
                .mapToDouble(line -> Double.valueOf(line.split("\\s")[0]))
                .findFirst()
                .orElseThrow();
    }
}
