// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Fetches node metrics over the metrics/v2 API
 *
 * @author bratseth
 */
public class NodeMetricsHttpFetcher implements NodeMetrics {

    @Override
    public Collection<Metric> fetchMetrics(String hostname) {
        return new ArrayList<>();
    }

}
