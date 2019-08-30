// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.gatherer;

import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.StatusCode;
import ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.inject.Inject;
import com.yahoo.vespa.defaults.Defaults;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author olaa
 */
public class NodeMetricGatherer {

    private static final int COREDUMP_AGE_IN_MINUTES = 12600;
    private static final JSONObject ROUTING_JSON = createRoutingJSON();
    private final VespaServices vespaServices;
    private final ApplicationDimensions applicationDimensions;
    private final NodeDimensions nodeDimensions;
    private final MetricsManager metricsManager;
    private final FileWrapper fileWrapper;

    private static final Logger logger = Logger.getLogger(NodeMetricGatherer.class.getSimpleName());

    @Inject
    public NodeMetricGatherer(MetricsManager metricsManager, VespaServices vespaServices, ApplicationDimensions applicationDimensions, NodeDimensions nodeDimensions) {
        this(metricsManager, vespaServices, applicationDimensions, nodeDimensions, new FileWrapper());
    }

    public NodeMetricGatherer(MetricsManager metricsManager,
                              VespaServices vespaServices,
                              ApplicationDimensions applicationDimensions,
                              NodeDimensions nodeDimensions,
                              FileWrapper fileWrapper) {
        this.metricsManager = metricsManager;
        this.vespaServices = vespaServices;
        this.applicationDimensions = applicationDimensions;
        this.nodeDimensions = nodeDimensions;
        this.fileWrapper = fileWrapper;
    }

    public List<MetricsPacket> gatherMetrics()  {
        List<MetricsPacket.Builder> metricPacketBuilders = new ArrayList<>();
        metricPacketBuilders.addAll(coredumpMetrics());
        metricPacketBuilders.addAll(serviceHealthMetrics());
        metricPacketBuilders.addAll(hostLifeMetrics());

        List<MetricsPacket> metricPackets = metricPacketBuilders.stream().map(metricPacketBuilder -> {
            metricPacketBuilder.putDimensionsIfAbsent(applicationDimensions.getDimensions());
            metricPacketBuilder.putDimensionsIfAbsent(nodeDimensions.getDimensions());
            metricPacketBuilder.putDimensionsIfAbsent(metricsManager.getExtraDimensions());
            return metricPacketBuilder.build();
        }).collect(Collectors.toList());
        return metricPackets;
    }

    private List<MetricsPacket.Builder> coredumpMetrics() {

        Path crashPath = Path.of(Defaults.getDefaults().underVespaHome("var/crash/processing"));
        long coredumps = getCoredumpsFromLastPeriod(crashPath);

        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("application", "Vespa.node");
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

    private List<MetricsPacket.Builder> serviceHealthMetrics()  {
        return vespaServices.getVespaServices()
                .stream()
                .map(service -> {
                    try {
                        StatusCode healthStatus = service.getHealth().getStatus();
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("status_code", healthStatus.code);
                        jsonObject.put("status_message", healthStatus.status);
                        jsonObject.put("application", service.getMonitoringName());
                        JSONObject dimensions = new JSONObject();
                        dimensions.put("instance", service.getInstanceName());
                        dimensions.put("metrictype", "health");
                        jsonObject.put("dimensions", dimensions);
                        jsonObject.put("routing", ROUTING_JSON);
                        return YamasJsonUtil.toMetricsPackets(jsonObject.toString()).get(0);
                    } catch (JSONException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                })
                .collect(Collectors.toList());
    }

    private List<MetricsPacket.Builder> hostLifeMetrics() {
        JSONObject jsonObject = new JSONObject();
        double upTime;
        int statusCode = 0;
        String statusMessage = "OK";
        try {
            upTime = getHostLife(Path.of("/proc/uptime")); // ??
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

    private long getCoredumpsFromLastPeriod(Path coreDumpPath) {
        try {
            return fileWrapper.walkTree(coreDumpPath)
                    .filter(Files::isRegularFile)
                    .filter(this::isNewFile)
                   .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private double getHostLife(Path uptimePath) throws IOException {
         return fileWrapper.readAllLines(uptimePath)
                 .stream()
                 .mapToDouble(line -> Double.valueOf(line.split("\\s")[0]))
                 .findFirst()
                 .orElseThrow();
    }

    private boolean isNewFile(Path file) {
        try {
            return fileWrapper.getLastModifiedTime(file)
                    .plus(COREDUMP_AGE_IN_MINUTES, ChronoUnit.MINUTES)
                    .isBefore(Instant.now());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JSONObject createRoutingJSON() {
        try {
            JSONObject jsonObject = new JSONObject("{\"yamas\":{\"namespaces\":[\"Vespa\"]}}");
            return jsonObject;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    static class FileWrapper {

        List<String> readAllLines(Path path) throws IOException {
            return Files.readAllLines(path);
        }

        Stream<Path> walkTree(Path path) throws IOException {
            return Files.walk(path);
        }

        Instant getLastModifiedTime(Path path) throws IOException {
            return Files.getLastModifiedTime(path).toInstant();
        }
    }
}
