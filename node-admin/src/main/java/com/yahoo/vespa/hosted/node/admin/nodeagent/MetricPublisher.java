// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.dockerapi.metrics.DimensionMetrics;

import java.util.List;

/**
 * @author olaa
 */
@FunctionalInterface
public interface MetricPublisher {
    void publishNodeSystemMetrics(List<DimensionMetrics> metrics);
}
