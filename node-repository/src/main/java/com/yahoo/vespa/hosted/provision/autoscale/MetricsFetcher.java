// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;

import java.time.Instant;
import java.util.Collection;

/**
 * Interface to retrieve metrics on (tenant) nodes.
 *
 * @author bratseth
 */
public interface MetricsFetcher {

    /**
     * Fetches metrics for all hosts of an application. This call may be expensive.
     *
     * @param application the application to fetch metrics from
     * @return a metric snapshot for each hostname of this application
     */
    Collection<Pair<String, MetricSnapshot>> fetchMetrics(ApplicationId application);

}
