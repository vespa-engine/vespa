// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsFetcher;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsResponse;

import java.util.concurrent.CompletableFuture;

/**
 * @author bratseth
 */
public class MockMetricsFetcher implements MetricsFetcher {

    @Override
    public CompletableFuture<MetricsResponse> fetchMetrics(ApplicationId application) {
        return CompletableFuture.completedFuture(MetricsResponse.empty());
    }

}
