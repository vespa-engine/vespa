// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metrics;

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
    public Metrics getMetrics(int fetchCount) {
        try {
            return createMetrics(getJson(), fetchCount);
        } catch (IOException e) {
            return new Metrics();
        }
    }

    Metrics createMetrics(String data, int fetchCount) {
        Metrics remoteMetrics = new Metrics();
        try {
            remoteMetrics = MetricsParser.parse(data);
        } catch (Exception e) {
            handleException(e, data, fetchCount);
        }

        return remoteMetrics;
    }
    Metrics createMetrics(byte [] data, int fetchCount) {
        Metrics remoteMetrics = new Metrics();
        try {
            remoteMetrics = MetricsParser.parse(data);
        } catch (Exception e) {
            handleException(e, data, fetchCount);
        }

        return remoteMetrics;
    }
}
