// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;

import java.util.concurrent.CompletableFuture;

/**
 * Interface to retrieve metrics on (tenant) nodes.
 *
 * @author bratseth
 */
public interface MetricsFetcher {

    /**
     * Fetches metrics asynchronously for all hosts of an application. This call may be expensive.
     *
     * @param application the application to fetch metrics from
     */
    CompletableFuture<MetricsResponse> fetchMetrics(ApplicationId application);

    void deconstruct();

}
