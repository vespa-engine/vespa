// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.Objects;

/**
 *
 * Represents an abstract monitoring service
 *
 * @author hmusum
 * @since 5.1.20
 *
*/
class AbstractMonitoringSystem implements MonitoringSystem {

    private final Integer interval;
    private final String clustername;

    public AbstractMonitoringSystem(String clustername, Integer interval) {
        Objects.requireNonNull(clustername);
        Objects.requireNonNull(interval);
        this.clustername = clustername;
        this.interval = interval;
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
