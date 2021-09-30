// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.HealthMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Fetch health status for a given vespa service
 *
 * @author Jo Kristian Bergum
 */
public class RemoteHealthMetricFetcher extends HttpMetricFetcher {
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private final static Logger log = Logger.getLogger(RemoteHealthMetricFetcher.class.getPackage().getName());

    private final static String HEALTH_PATH = STATE_PATH + "health";

    public RemoteHealthMetricFetcher(VespaService service, int port) {
        super(service, port, HEALTH_PATH);
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    public HealthMetric getHealth(int fetchCount) {
        try (InputStream stream = getJson()) {
            return createHealthMetrics(stream, fetchCount);
        } catch (IOException | InterruptedException | ExecutionException e) {
            logMessageNoResponse(errMsgNoResponse(e), fetchCount);
            return HealthMetric.getUnknown("Failed fetching metrics for service: " + service.getMonitoringName());
        }
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    private HealthMetric createHealthMetrics(InputStream data, int fetchCount) throws IOException {
        try {
            return parse(data);
        } catch (Exception e) {
            handleException(e, data, fetchCount);
            while (data.read() != -1) {}
            return HealthMetric.getDown("Failed fetching status page for service");
        }
    }


    private HealthMetric parse(InputStream data) {
        try {
            JsonNode o = jsonMapper.readTree(data);
            JsonNode status = o.get("status");
            String code = status.get("code").asText();
            String message = "";
            if (status.has("message")) {
                message = status.get("message").textValue();
            }
            return HealthMetric.get(code, message);

        } catch (IOException e) {
            log.log(Level.FINE, () -> "Failed to parse json response from metrics page:" + e + ":" + data);
            return HealthMetric.getUnknown("Not able to parse json from status page");
        }
    }
}
