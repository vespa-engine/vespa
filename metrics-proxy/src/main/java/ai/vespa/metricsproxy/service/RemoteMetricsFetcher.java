// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.BufferedInputStream;
import java.io.IOException;

/**
 * Fetch metrics for a given vespa service
 *
 * @author Jo Kristian Bergum
 */
public class RemoteMetricsFetcher extends HttpMetricFetcher {

    final static String METRICS_PATH = STATE_PATH + "metrics";

    RemoteMetricsFetcher(VespaService service, int port) {
        super(service, port, METRICS_PATH);
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    public void getMetrics(MetricsParser.Consumer consumer, int fetchCount) {
        try (CloseableHttpResponse response = getResponse()) {
            HttpEntity entity = response.getEntity();
            try {
                MetricsParser.parse(new BufferedInputStream(entity.getContent(), HttpMetricFetcher.BUFFER_SIZE), consumer);
            } catch (Exception e) {
                handleException(e, entity.getContentType(), fetchCount);
            } finally {
                EntityUtils.consumeQuietly(entity);
            }
        } catch (IOException ignored) {}
    }

    void createMetrics(String data, MetricsParser.Consumer consumer, int fetchCount) throws IOException {
        MetricsParser.parse(data, consumer);
    }
}
