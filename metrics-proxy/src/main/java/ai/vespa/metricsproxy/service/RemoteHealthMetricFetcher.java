// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.HealthMetric;
import com.yahoo.log.LogLevel;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Fetch health status for a given vespa service
 *
 * @author Jo Kristian Bergum
 */
public class RemoteHealthMetricFetcher extends HttpMetricFetcher {
    private final static Logger log = Logger.getLogger(RemoteHealthMetricFetcher.class.getPackage().getName());

    private final static String HEALTH_PATH = STATE_PATH + "health";

    public RemoteHealthMetricFetcher(VespaService service, int port) {
        super(service, port, HEALTH_PATH);
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    public HealthMetric getHealth(int fetchCount) {
        String data = "{}";
        try {
            data = getJson();
        } catch (IOException e) {
            logMessageNoResponse(errMsgNoResponse(e), fetchCount);
        }
        return createHealthMetrics(data, fetchCount);
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    private HealthMetric createHealthMetrics(String data, int fetchCount) {
        HealthMetric healthMetric = HealthMetric.getDown("Failed fetching status page for service");
        try {
            healthMetric = parse(data);
        } catch (Exception e) {
            handleException(e, data, fetchCount);
        }
        return healthMetric;
    }

    private HealthMetric parse(String data) {
        if (data == null || data.isEmpty()) {
            return HealthMetric.getUnknown("Empty response from status page");
        }
        try {
            JSONObject o = new JSONObject(data);
            JSONObject status = o.getJSONObject("status");
            String code = status.getString("code");
            String message = "";
            if (status.has("message")) {
                message = status.getString("message");
            }
            return HealthMetric.get(code, message);

        } catch (JSONException e) {
            log.log(LogLevel.DEBUG, "Failed to parse json response from metrics page:" + e + ":" + data);
            return HealthMetric.getUnknown("Not able to parse json from status page");
        }
    }
}
