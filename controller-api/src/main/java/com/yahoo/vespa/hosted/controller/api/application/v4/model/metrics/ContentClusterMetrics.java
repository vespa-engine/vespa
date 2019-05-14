// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model.metrics;

import com.yahoo.config.provision.ClusterSpec;

/**
 * @author olaa
 */
public class ContentClusterMetrics{

    private final double documentCount;

    public ContentClusterMetrics(String clusterId, double documentCount) {
        this.documentCount = documentCount;
    }
}
