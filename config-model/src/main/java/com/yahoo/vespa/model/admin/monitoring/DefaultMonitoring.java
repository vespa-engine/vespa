// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.Objects;

/**
 * Properties for yamas monitoring service
 *
 * @author hmusum
 * @since 5.1.20
 */
public class DefaultMonitoring implements Monitoring {

    private final Integer interval;
    private final String clustername;

    public DefaultMonitoring(String clustername, Integer interval) {
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


