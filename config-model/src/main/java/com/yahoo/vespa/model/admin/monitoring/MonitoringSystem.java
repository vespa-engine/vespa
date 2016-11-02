// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

/**
 * Interface for different monitoring services
 *
 * @author hmusum
 */
public interface MonitoringSystem {
    /**
     * @return Snapshot interval in minutes
     */
    public Integer getInterval();

    /**
     * @return Snapshot interval in seconds.
     */
    public Integer getIntervalSeconds();

    /**
     * @return the monitoring cluster name
     */
    public String getClustername();
}
