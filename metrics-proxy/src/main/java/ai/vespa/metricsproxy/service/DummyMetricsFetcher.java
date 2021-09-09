// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metrics;

/**
 * Dummy class used for getting health status for a vespa service that has no HTTP service
 * for getting metrics
 *
 * @author hmusum
 */
public class DummyMetricsFetcher extends RemoteMetricsFetcher {

    /**
     * @param service The service to fetch metrics from
     */
    DummyMetricsFetcher(VespaService service) {
        super(service, 0);
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    public Metrics getMetrics(int fetchCount) {
        return new Metrics();
    }
}
