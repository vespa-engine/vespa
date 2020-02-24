// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;

import java.util.Collection;

/**
 * Fetches node metrics over the metrics/v2 API
 *
 * @author bratseth
 */
public class NodeMetricsHttpFetcher implements NodeMetrics {

    private static final String apiPath = "/metrics/v2";

    @Override
    public Collection<MetricValue> fetchMetrics(ApplicationId application) {
        String response = ""; // TODO
        return new MetricsResponse(response).metrics();
    }

}
