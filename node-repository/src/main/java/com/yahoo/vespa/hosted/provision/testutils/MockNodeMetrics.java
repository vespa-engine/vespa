// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetrics;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @ahtor bratseth
 */
public class MockNodeMetrics implements NodeMetrics {

    @Override
    public Collection<MetricValue> fetchMetrics(ApplicationId application) {
        return new ArrayList<>();
    }

}
