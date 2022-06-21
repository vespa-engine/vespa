// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.HealthMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
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
