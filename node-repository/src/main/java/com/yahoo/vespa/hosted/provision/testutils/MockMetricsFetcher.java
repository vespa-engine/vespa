// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsFetcher;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author bratseth
 */
public class MockMetricsFetcher implements MetricsFetcher {

    @Override
    public Collection<NodeMetrics> fetchMetrics(ApplicationId application) {
        return new ArrayList<>();
    }

}
