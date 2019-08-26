// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.gatherer;

import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.StatusCode;
import ai.vespa.metricsproxy.metric.model.json.YamasArrayJsonModel;
import ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.inject.Inject;
import com.yahoo.vespa.defaults.Defaults;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class NodeMetricGatherer {

    private static final int COREDUMP_AGE_IN_MINUTES = 12600;
    private static final JSONObject ROUTING_JSON = createRoutingJSON();
    private final VespaServices vespaServices;
    private final ApplicationDimensions applicationDimensions;
    private final NodeDimensions nodeDimensions;
    private final String coreDumpPath;
    private final String hostLifePath;

    private static final Logger logger = Logger.getLogger(NodeMetricGatherer.class.getSimpleName());

    @Inject
    public NodeMetricGatherer(VespaServices vespaServices, ApplicationDimensions applicationDimensions, NodeDimensions nodeDimensions) {
        this(vespaServices, applicationDimensions, nodeDimensions, "var/crash/processing", "/proc/uptime");
    }

    public NodeMetricGatherer(VespaServices vespaServices, ApplicationDimensions applicationDimensions, NodeDimensions nodeDimensions, String coreDumpPath, String hostLifePath) {
        this.vespaServices = vespaServices;
        this.applicationDimensions = applicationDimensions;
        this.nodeDimensions = nodeDimensions;
        this.coreDumpPath = coreDumpPath;
        this.hostLifePath = hostLifePath;
    }

    public YamasArrayJsonModel gatherMetrics() throws JSONException {
        List<MetricsPacket.Builder> metricPacketBuilders = new ArrayList<>();
        metricPacketBuilders.addAll(coredumpMetrics());
        metricPacketBuilders.addAll(serviceHealthMetrics());
        metricPacketBuilders.addAll(hostLifeMetrics());

        List<MetricsPacket> metricPackets = metricPacketBuilders.stream().map(metricPacketBuilder -> {
            metricPacketBuilder.putDimensionsIfAbsent(applicationDimensions.getDimensions());
            metricPacketBuilder.putDimensionsIfAbsent(nodeDimensions.getDimensions());
            return metricPacketBuilder.build();
        }).collect(Collectors.toList());
        return YamasJsonUtil.toYamasArray(metricPackets);
    }

    private List<MetricsPacket.Builder> coredumpMetrics() throws JSONException {

        Path crashPath = Path.of(Defaults.getDefaults().underVespaHome(coreDumpPath));
        long coredumps = getCoredumpsFromLastPeriod(crashPath);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("timestamp", Instant.now().getEpochSecond());
        jsonObject.put("application", "system-coredumps-processing");
        jsonObject.put("status_code", coredumps);
        jsonObject.put("status_message", coredumps == 0 ? "OK" : String.format("Found %d coredumps in past %d minutes", coredumps, COREDUMP_AGE_IN_MINUTES));
        jsonObject.put("routing", ROUTING_JSON);
        return YamasJsonUtil.toMetricsPackets(jsonObject.toString());
    }

    private List<MetricsPacket.Builder> serviceHealthMetrics()  {
        JSONArray jsonArray = new JSONArray();
        vespaServices.getVespaServices()
                .stream()
                .forEach(service -> {
                    try {
                        StatusCode healthStatus = service.getHealth().getStatus();
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("status_code", healthStatus.code);
                        jsonObject.put("status_message", healthStatus.status);
                        jsonObject.put("application", service.getMonitoringName());
                        JSONObject dimensions = new JSONObject();
                        dimensions.put("instance", service.getInstanceName());
                        dimensions.put("metricsType", "health");
                        jsonObject.put("dimensions", dimensions);
                        jsonObject.put("routing", ROUTING_JSON);
                        jsonArray.put(jsonObject);
                    } catch (JSONException e) {

                    }
                });

        return YamasJsonUtil.toMetricsPackets(jsonArray.toString());
    }

    private List<MetricsPacket.Builder> hostLifeMetrics() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        double upTime;
        int statusCode = 0;
        String statusMessage = "OK";
        try {
            upTime = getHostLife(Path.of(hostLifePath)); // ??
        } catch (IOException e) {
            upTime = 0d;
            statusCode = 1;
            statusMessage = e.getMessage();
        }

        jsonObject.put("timestamp", Instant.now().getEpochSecond());
        jsonObject.put("status_message", statusMessage);
        jsonObject.put("status_code", statusCode);
        JSONObject metrics = new JSONObject();
        metrics.put("uptime", upTime);
        metrics.put("alive", 1);
        jsonObject.put("metrics", metrics);
        jsonObject.put("routing", ROUTING_JSON);

        return YamasJsonUtil.toMetricsPackets(jsonObject.toString());
    }

    private long getCoredumpsFromLastPeriod(Path coreDumpPath) {

        try {
            return Files.walk(coreDumpPath)
                    .filter(Files::isRegularFile)
                    .filter(this::isNewFile)
                   .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private double getHostLife(Path uptimePath) throws IOException {
         return Files.readAllLines(uptimePath)
                 .stream()
                 .mapToDouble(line -> Double.valueOf(line.split("\\s")[0]))
                 .findFirst()
                 .orElseThrow();
    }

    private boolean isNewFile(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant()
                    .plus(COREDUMP_AGE_IN_MINUTES, ChronoUnit.MINUTES)
                    .isBefore(Instant.now());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final JSONObject createRoutingJSON() {
        try {
            JSONObject namesspace = new JSONObject();
            namesspace.put("yamas", "Vespa");
            return namesspace;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

}
