// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.HealthMetric;

/**
 * Dummy class used for getting health status for a vespa service that has no HTTP service
 * for getting health status
 *
 * @author hmusum
 */
public class DummyHealthStatusFetcher extends RemoteHealthStatusFetcher {

    /**
     * @param service The service to fetch health status from
     */
    DummyHealthStatusFetcher(VespaService service) {
        super(service, 0);
    }

    /**
     * Connect to remote service over http and fetch health status
     */
    @Override
    public HealthMetric getHealth(int fetchCount) {
        return service.isAlive()
                ? HealthMetric.getOk("Service is running - pid check only")
                : HealthMetric.getDown("Service is not running - pid check only");
    }
}
