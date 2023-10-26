// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.Objects;
import java.util.Optional;

/**
 * Monitoring service properties
 *
 * @author hmusum
 */
public class DefaultMonitoring implements Monitoring {

    public static final int DEFAULT_MONITORING_INTERVAL = 1; // in minutes
    private static final String DEFAULT_MONITORING_CLUSTER_NAME = "vespa";

    private final Integer interval;
    private final String clustername;

    public DefaultMonitoring() {
        this(DEFAULT_MONITORING_CLUSTER_NAME, Optional.empty());
    }

    public DefaultMonitoring(String clustername, Optional<Integer> interval) {
        Objects.requireNonNull(clustername);
        Objects.requireNonNull(interval);
        this.clustername = clustername;
        this.interval = interval.orElse(DEFAULT_MONITORING_INTERVAL);
    }

    @Override
    public Integer getInterval() {
        return interval;
    }

    @Override
    public Integer getIntervalSeconds() {
        return interval * 60;
    }

    @Override
    public String getClustername() {
        return clustername;
    }

}


