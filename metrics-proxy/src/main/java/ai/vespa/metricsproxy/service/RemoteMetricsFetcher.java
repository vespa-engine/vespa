// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

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
        try (InputStream stream = getJson()) {
            createMetrics(stream, consumer, fetchCount);
        } catch (IOException | InterruptedException | ExecutionException e) {
        }
    }

    void createMetrics(String data, MetricsParser.Consumer consumer, int fetchCount) {
        try {
            MetricsParser.parse(data, consumer);
        } catch (Exception e) {
            handleException(e, data, fetchCount);
        }
    }
    private void createMetrics(InputStream data, MetricsParser.Consumer consumer, int fetchCount) throws IOException {
        try {
            MetricsParser.parse(data, consumer);
        } catch (Exception e) {
            handleException(e, data, fetchCount);
            while (data.read() != -1) {}
        }
    }
}
