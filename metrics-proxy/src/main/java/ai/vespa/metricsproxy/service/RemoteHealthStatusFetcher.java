// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import com.yahoo.json.Jackson;
import ai.vespa.metricsproxy.metric.HealthMetric;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

/**
 * Fetch health status for a given vespa service
 *
 * @author Jo Kristian Bergum
 */
public class RemoteHealthStatusFetcher extends HttpMetricFetcher {
    private final static Logger log = Logger.getLogger(RemoteHealthStatusFetcher.class.getPackage().getName());

    private final static String HEALTH_PATH = STATE_PATH + "health";

    public RemoteHealthStatusFetcher(VespaService service, int port) {
        super(service, port, HEALTH_PATH);
    }

    /**
     * Connect to remote service over http and fetch health status
     */
    public HealthMetric getHealth(int fetchCount) {
        try (CloseableHttpResponse response = getResponse()) {
            HttpEntity entity = response.getEntity();
            try {
                return parse(new BufferedInputStream(entity.getContent(), HttpMetricFetcher.BUFFER_SIZE));
            } catch (Exception e) {
                handleException(e, entity.getContentType(), fetchCount);
                return HealthMetric.getDown("Failed fetching status page for service");
            } finally {
                EntityUtils.consumeQuietly(entity);
            }
        } catch (IOException e) {
            if (service.isAlive()) {
                logMessageNoResponse(errMsgNoResponse(e), fetchCount);
            }
            return HealthMetric.getUnknown("Failed fetching metrics for service: " + service.getMonitoringName());
        }
    }


    HealthMetric parse(InputStream data) {
        try {
            JsonNode o = Jackson.mapper().readTree(data);
            if (!o.isObject()) {
                throw new IllegalArgumentException("Expected JSON object");
            }

            if (o.isEmpty()) {
                return HealthMetric.getUnknown("Empty metrics response");
            }
            JsonNode status = o.get("status");
            if (status == null || !status.has("code")) {
                return HealthMetric.getUnknown("Missing status or code in response");
            }

            String code = status.get("code").asText();
            String message = status.path("message").asText("");
            return HealthMetric.get(code, message);

        } catch (Exception e) {
            var level = INFO;
            if (e instanceof IOException) {
                level = FINE;
            }
            log.log(level, () -> "Failed to parse json response from metrics page:" + e + ":" + data);
            return HealthMetric.getUnknown("Not able to parse json from status page");
        }
    }
}
